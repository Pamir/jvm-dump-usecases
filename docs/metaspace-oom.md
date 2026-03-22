# Metaspace / Class Loading OOM

## Problem Description and Symptoms

**Metaspace OutOfMemoryError** occurs when the JVM runs out of native memory allocated for class metadata storage. Unlike heap OOM, this affects the native memory area where class definitions, method information, and constant pools are stored.

**Common symptoms:**
- `java.lang.OutOfMemoryError: Metaspace` or `java.lang.OutOfMemoryError: Compressed class space`
- Native memory (RSS) grows continuously while heap usage remains stable
- Increasing number of loaded classes over time
- Application works fine initially but fails after extended runtime
- Particularly common in applications with dynamic class generation

## How to Run the Case

### Using Docker with Metaspace Limit
```bash
docker run -it --rm \
  -e JAVA_OPTS="-XX:MaxMetaspaceSize=32m -XX:+UnlockDiagnosticVMOptions -XX:+LogVMOutput" \
  pamir/jvm-cases MetaspaceOOM
```

### Direct Java Command
```bash
# Set small Metaspace limit to trigger OOM quickly
java -XX:MaxMetaspaceSize=32m \
     -XX:MetaspaceSize=16m \
     -XX:+UnlockDiagnosticVMOptions \
     -XX:+LogVMOutput \
     -jar application.jar MetaspaceOOM

# For Java 8 (PermGen era)
java -XX:MaxPermSize=32m -XX:PermSize=16m -jar application.jar MetaspaceOOM
```

### Enable Native Memory Tracking
```bash
java -XX:NativeMemoryTracking=detail \
     -XX:MaxMetaspaceSize=64m \
     -jar application.jar MetaspaceOOM
```

## How to Observe/Detect the Issue

### Monitor Metaspace Usage
```bash
# Check Metaspace capacity and utilization
jstat -gcmetacapacity <pid> 1000

# Expected output pattern:
#   MCMN    MCMX     MC     CCSMN   CCSMX    CCSC    YGC   FGC    FGCT     GCT
# 0.0   1048576.0  21248.0    0.0  1048576.0  2688.0    12     2    0.045    0.087
```

### Native Memory Analysis
```bash
# Get comprehensive native memory breakdown
jcmd <pid> VM.native_memory summary

# Monitor native memory growth over time
jcmd <pid> VM.native_memory baseline
# ... wait some time ...
jcmd <pid> VM.native_memory summary.diff
```

### Class Loading Statistics
```bash
# Check number of loaded classes
jcmd <pid> VM.classloader_stats

# Class histogram to see class distribution
jcmd <pid> GC.class_histogram | head -30

# Verbose class loading (add to JVM startup)
java -verbose:class -jar application.jar
```

## Step-by-Step Analysis

### 1. Confirm Metaspace Issue
```bash
# Check if Metaspace is the culprit
jstat -gcmetacapacity <pid> | awk 'NR==2 {print "Used:", $3, "Max:", $2, "Utilization:", ($3/$2)*100"%"}'
```

### 2. Analyze Class Loading Pattern
```bash
# Monitor class loading over time
while true; do
    CLASSES=$(jcmd $PID VM.classloader_stats | grep "Total" | awk '{print $3}')
    echo "$(date): $CLASSES classes loaded"
    sleep 10
done
```

### 3. Take Heap Dump for Class Analysis
```bash
# Heap dump contains class metadata
jcmd <pid> GC.run
jmap -dump:format=b,file=metaspace-analysis.hprof <pid>

# Analyze with MAT - look for:
# 1. Class instances by loader
# 2. Duplicate classes from different loaders
# 3. Custom classloaders and their loaded classes
```

### 4. Native Memory Tracking Deep Dive
```bash
# Detailed native memory tracking
jcmd <pid> VM.native_memory detail | grep -A 10 "Class"

# Example output showing Metaspace growth:
# Class (reserved=1048576KB, committed=21248KB)
#     (classes #2567)
#     (malloc=1048KB #7890)
#     (mmap: reserved=1047528KB, committed=20200KB)
```

### 5. Identify Class Generation Sources
```bash
# Enable class loading logging
java -Xlog:class+load:class-loading.log:time,level,tags -jar application.jar

# Analyze class loading patterns
grep "source:" class-loading.log | awk '{print $NF}' | sort | uniq -c | sort -nr
```

## Root Cause

**Primary causes of Metaspace OOM:**

### 1. Dynamic Class Generation Without Cleanup
Applications that generate classes at runtime without proper cleanup:
- **Groovy/Jython scripts** creating new classes per execution
- **CGLIB proxies** (Spring AOP, Hibernate) without caching
- **Reflection-heavy frameworks** generating proxy classes
- **JSP compilation** in web applications
- **Code generation libraries** (ASM, Javassist, ByteBuddy)

### 2. ClassLoader Leaks
Custom ClassLoaders not being garbage collected:
- **Web application redeployment** without proper cleanup
- **Plugin architectures** creating multiple loaders
- **OSGi bundles** with lifecycle management issues
- **Hot-swapping mechanisms** retaining old class versions

### 3. Excessive Library Dependencies
Large number of libraries with many classes:
- **Fat JAR files** with unnecessary dependencies
- **Duplicate classes** from different library versions
- **Large frameworks** loading extensive class hierarchies

## How to Fix / Prevent

### 1. Set Appropriate Metaspace Limits
```bash
# Always set MaxMetaspaceSize in production
java -XX:MaxMetaspaceSize=256m \
     -XX:MetaspaceSize=128m \
     -jar application.jar

# Monitor and adjust based on actual usage
# Rule of thumb: 64MB baseline + 32MB per major framework
```

### 2. Fix Dynamic Class Generation - Cache Generated Classes
```java
// BAD: Creating new GroovyClassLoader per script execution
public Object executeGroovyScript(String script) {
    GroovyClassLoader loader = new GroovyClassLoader();
    Class<?> scriptClass = loader.parseClass(script);
    return scriptClass.newInstance(); // Memory leak!
}

// GOOD: Reuse ClassLoader and cache compiled scripts
private static final GroovyClassLoader SHARED_LOADER = new GroovyClassLoader();
private static final Map<String, Class<?>> SCRIPT_CACHE = new ConcurrentHashMap<>();

public Object executeGroovyScript(String script) {
    Class<?> scriptClass = SCRIPT_CACHE.computeIfAbsent(script, s -> {
        try {
            return SHARED_LOADER.parseClass(s);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    });
    return scriptClass.newInstance();
}
```

### 3. Proper Spring Configuration for CGLIB Caching
```java
// Enable CGLIB proxy caching in Spring
@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class ApplicationConfig {
    
    @Bean
    public static BeanPostProcessor cglibProxyCachingPostProcessor() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                // Ensure CGLIB proxies are cached
                System.setProperty("cglib.useCache", "true");
                return bean;
            }
        };
    }
}
```

### 4. ClassLoader Lifecycle Management
```java
// Proper ClassLoader cleanup in plugin architecture
public class PluginManager {
    private final Map<String, PluginClassLoader> loaders = new ConcurrentHashMap<>();
    
    public void loadPlugin(String pluginId, Path jarPath) {
        PluginClassLoader loader = new PluginClassLoader(jarPath);
        loaders.put(pluginId, loader);
    }
    
    public void unloadPlugin(String pluginId) {
        PluginClassLoader loader = loaders.remove(pluginId);
        if (loader != null) {
            try {
                loader.close(); // Important: close the ClassLoader
                
                // Trigger GC to clean up Metaspace
                System.gc();
                System.runFinalization();
                System.gc();
            } catch (IOException e) {
                log.warn("Failed to close ClassLoader for plugin: " + pluginId, e);
            }
        }
    }
}
```

### 5. Optimize Dependency Management
```xml
<!-- Maven: Exclude unnecessary transitive dependencies -->
<dependency>
    <groupId>org.springframework</groupId>
    <artifactId>spring-web</artifactId>
    <exclusions>
        <exclusion>
            <groupId>commons-logging</groupId>
            <artifactId>commons-logging</artifactId>
        </exclusion>
    </exclusions>
</dependency>

<!-- Use Maven Dependency Plugin to analyze -->
<!-- mvn dependency:analyze -->
<!-- mvn dependency:tree -->
```

### 6. Enable Shared Class Data (CDS) for Memory Efficiency
```bash
# Generate CDS archive during build
java -Xshare:dump -XX:SharedArchiveFile=app.jsa -jar application.jar

# Use CDS archive at runtime
java -Xshare:on -XX:SharedArchiveFile=app.jsa -jar application.jar

# Benefits: Reduced Metaspace usage, faster startup
```

### 7. Monitoring and Alerting Setup
```bash
# Production monitoring script
#!/bin/bash
PID=$(pgrep java)
METASPACE_USAGE=$(jcmd $PID VM.native_memory summary | grep "Class" | head -1 | awk '{print $3}' | tr -d 'KB')
MAX_METASPACE=262144  # 256MB in KB

USAGE_PERCENT=$((METASPACE_USAGE * 100 / MAX_METASPACE))

if [ $USAGE_PERCENT -gt 80 ]; then
    echo "WARNING: Metaspace usage at ${USAGE_PERCENT}%" | logger -p local0.warn
    jcmd $PID VM.classloader_stats | logger -p local0.info
fi

if [ $USAGE_PERCENT -gt 95 ]; then
    echo "CRITICAL: Metaspace usage at ${USAGE_PERCENT}%" | logger -p local0.error
fi
```

## Prevention Strategies

### 1. Development Best Practices
- **Profile class loading** during development with `-verbose:class`
- **Use static analysis** tools to detect potential ClassLoader leaks
- **Test hot-deployment scenarios** extensively
- **Monitor class count growth** in integration tests

### 2. Architecture Considerations
- **Minimize dynamic class generation** - prefer configuration over code generation
- **Use dependency injection** instead of reflection-heavy patterns
- **Implement proper plugin lifecycle management**
- **Consider AOT compilation** (GraalVM) for predictable class loading

### 3. Production Deployment
```bash
# Comprehensive Metaspace configuration
java -server \
     -XX:MetaspaceSize=128m \
     -XX:MaxMetaspaceSize=256m \
     -XX:+UnlockDiagnosticVMOptions \
     -XX:+LogVMOutput \
     -XX:NativeMemoryTracking=summary \
     -Xlog:class+load:class.log:time \
     -jar application.jar
```

### 4. Container Considerations
```dockerfile
# Dockerfile with proper memory allocation
FROM openjdk:11-jre-slim

# Ensure container memory > heap + metaspace + direct + overhead
ENV JAVA_OPTS="-Xmx1g -XX:MaxMetaspaceSize=256m -XX:MaxDirectMemorySize=128m"

# Total container memory should be ~1.5GB for above settings
```

## Key Takeaways

1. **Always set MaxMetaspaceSize** in production to prevent unbounded growth
2. **Monitor class loading patterns** - linear growth indicates a leak
3. **Cache generated classes** and reuse ClassLoaders whenever possible
4. **Implement proper cleanup** for custom ClassLoaders and plugin architectures
5. **Use native memory tracking** to understand memory allocation patterns
6. **Consider CDS** for applications with stable class sets
7. **Test hot-deployment scenarios** thoroughly in development
8. **Plan container memory** as: heap + metaspace + direct + ~25% overhead

**Metaspace Sizing Guidelines:**
- **Baseline:** 64MB for simple applications
- **Add 32-64MB** per major framework (Spring, Hibernate, etc.)
- **Add 128MB+** for dynamic class generation (Groovy, heavy AOP)
- **Monitor and adjust** based on actual production usage patterns

**Container Memory Formula:** `Container Limit = Xmx + MaxMetaspaceSize + MaxDirectMemorySize + 512MB overhead`