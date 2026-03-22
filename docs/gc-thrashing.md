# GC Thrashing / GC Overhead Limit

## Problem Description and Symptoms

**GC Thrashing** occurs when the JVM spends excessive time in garbage collection with minimal memory recovery. The application becomes extremely slow as most CPU cycles are consumed by GC threads rather than application logic.

**Common symptoms:**
- Application response time degrades dramatically
- High CPU usage from GC threads
- Throughput drops to near zero
- Eventually: `java.lang.OutOfMemoryError: GC overhead limit exceeded`
- Old generation constantly at 98-100% capacity
- Full GC cycles recover less than 2% of heap space

## How to Run the Case

### Using Docker with GC Logging
```bash
docker run -it --rm \
  -e JAVA_OPTS="-Xmx64m -verbose:gc -Xlog:gc*:gc.log" \
  pamir/jvm-cases GCThrashing
```

### Direct Java Command
```bash
java -Xmx64m -verbose:gc -Xlog:gc*:gc.log -jar application.jar GCThrashing

# For older JVM versions (Java 8)
java -Xmx64m -XX:+PrintGC -XX:+PrintGCDetails -Xloggc:gc.log -jar application.jar GCThrashing
```

### Trigger Conditions
```bash
# Intentionally constrain memory to trigger thrashing
java -Xmx32m -Xms32m -jar application.jar GCThrashing
```

## How to Observe/Detect the Issue

### Real-time GC Monitoring
```bash
# Monitor GC statistics every second
jstat -gcutil <pid> 1000

# Expected output showing thrashing:
# S0     S1     E      O      M     CCS    YGC     YGCT    FGC    FGCT     GCT
# 0.00   0.00  45.32  99.87  95.21  89.23    245    2.345    89   67.123   69.468
```

### Detailed GC Analysis
```bash
# Check GC frequency and duration
jstat -gc <pid> 1000

# Memory pool usage
jcmd <pid> GC.run_finalization
jcmd <pid> VM.memory_summary
```

### GC Log Analysis
```bash
# Look for frequent Full GC with minimal recovery
tail -f gc.log | grep "Full GC"

# Example of thrashing pattern in logs:
# [gc] GC(156) Pause Full (Ergonomics) 63M->62M(64M) 891.234ms
# [gc] GC(157) Pause Full (Ergonomics) 62M->62M(64M) 923.156ms
# [gc] GC(158) Pause Full (Ergonomics) 62M->61M(64M) 1034.567ms
```

## Step-by-Step Analysis

### 1. Confirm GC Thrashing Pattern
```bash
# Check if GC time > 98% and recovery < 2%
jstat -gcutil <pid> | awk '{if(NR>1) print "Old Gen:", $4"%, GC Time:", $10"s"}'
```

### 2. Take Heap Dump for Analysis
```bash
# Force GC first, then dump
jcmd <pid> GC.run
jcmd <pid> GC.run_finalization
jmap -dump:format=b,file=heap-thrashing.hprof <pid>
```

### 3. Analyze Memory Usage Patterns
```bash
# Quick histogram of objects in heap
jcmd <pid> GC.class_histogram | head -20

# Expected output showing memory hogs:
# num     #instances         #bytes  class name (module)
#   1:      45678234      1463703488  [B (java.base@11.0.2)
#   2:      12345678       987654320  java.lang.String (java.base@11.0.2)
```

### 4. Monitor GC Behavior Over Time
```bash
# Continuous monitoring script
while true; do
    echo "$(date): $(jstat -gcutil $PID | tail -1)"
    sleep 5
done > gc-monitoring.log
```

### 5. Use Memory Analysis Tools
```bash
# Eclipse MAT analysis
# 1. Open heap-thrashing.hprof in MAT
# 2. Run "Leak Suspects Report"
# 3. Check "Dominator Tree" for retained heap
# 4. Analyze "Top Consumers" by class
```

## Root Cause

**Primary causes of GC thrashing:**

### 1. Undersized Heap
Application legitimately needs more memory than allocated heap size.

### 2. Memory Leaks
Objects are retained in memory longer than necessary:
- **Static collections** growing unbounded
- **Event listeners** not properly removed
- **ThreadLocal variables** not cleaned up
- **Cache implementations** without eviction policies

### 3. Inefficient Memory Usage Patterns
- Loading entire large datasets into memory
- Creating too many temporary objects
- String concatenation in loops
- Excessive autoboxing/unboxing

### 4. GC Algorithm Mismatch
Using inappropriate GC algorithm for the workload (e.g., Serial GC for high-throughput applications).

## How to Fix / Prevent

### 1. Increase Heap Size (If Legitimate Need)
```bash
# Analyze actual memory requirements first
java -XX:+UnlockDiagnosticVMOptions -XX:+PrintFlagsFinal -version | grep HeapSize

# Increase heap appropriately (ensure container has sufficient memory)
java -Xmx2g -Xms1g -jar application.jar

# For containers, ensure: container_memory > Xmx + 512MB overhead
```

### 2. Fix Memory Leaks - Streaming vs Loading All
```java
// BAD: Loading all data into memory
public List<Record> processAllRecords() {
    List<Record> allRecords = repository.findAll(); // Loads millions of records
    return allRecords.stream()
        .filter(this::isValid)
        .map(this::transform)
        .collect(Collectors.toList());
}

// GOOD: Stream processing with pagination
public void processRecordsInBatches() {
    int pageSize = 1000;
    int page = 0;
    List<Record> batch;
    
    do {
        batch = repository.findPage(page++, pageSize);
        batch.stream()
            .filter(this::isValid)
            .map(this::transform)
            .forEach(this::process);
        
        // Allow GC to clean up batch
        batch = null;
        System.gc(); // Hint for GC (optional)
    } while (!batch.isEmpty());
}
```

### 3. Optimize Object Creation
```java
// BAD: Excessive object creation
public String buildQuery(List<String> params) {
    String query = "SELECT * FROM table WHERE ";
    for (String param : params) {
        query += " field = '" + param + "' AND"; // Creates new String each iteration
    }
    return query.substring(0, query.length() - 4);
}

// GOOD: Use StringBuilder
public String buildQuery(List<String> params) {
    StringBuilder query = new StringBuilder("SELECT * FROM table WHERE ");
    for (String param : params) {
        query.append(" field = '").append(param).append("' AND");
    }
    return query.substring(0, query.length() - 4);
}
```

### 4. Implement Proper Cache Eviction
```java
// BAD: Unbounded cache
private static final Map<String, Object> cache = new ConcurrentHashMap<>();

public Object getCachedValue(String key) {
    return cache.computeIfAbsent(key, this::expensiveComputation);
}

// GOOD: Bounded cache with eviction
private static final Cache<String, Object> cache = Caffeine.newBuilder()
    .maximumSize(10_000)
    .expireAfterWrite(Duration.ofMinutes(30))
    .build();

public Object getCachedValue(String key) {
    return cache.get(key, this::expensiveComputation);
}
```

### 5. Tune GC Algorithm and Parameters
```bash
# For low-latency applications - G1GC
java -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -jar application.jar

# For high-throughput applications - Parallel GC
java -XX:+UseParallelGC -XX:ParallelGCThreads=8 -jar application.jar

# For very large heaps - ZGC (Java 11+)
java -XX:+UnlockExperimentalVMOptions -XX:+UseZGC -jar application.jar

# Set GC overhead limit (default 98% time in GC, 2% recovery)
java -XX:GCTimeRatio=9 -jar application.jar  # Allow max 10% GC time
```

### 6. Monitor and Alert on GC Metrics
```bash
# Production monitoring script
#!/bin/bash
PID=$(pgrep java)
GC_UTIL=$(jstat -gcutil $PID | tail -1 | awk '{print $4, $10}')
OLD_GEN=$(echo $GC_UTIL | awk '{print $1}')
GC_TIME=$(echo $GC_UTIL | awk '{print $2}')

if (( $(echo "$OLD_GEN > 90" | bc -l) )); then
    echo "WARNING: Old Gen at ${OLD_GEN}%" | logger -p local0.warn
fi

if (( $(echo "$GC_TIME > 5" | bc -l) )); then
    echo "CRITICAL: GC Time at ${GC_TIME}s" | logger -p local0.error
fi
```

## Prevention Strategies

### 1. Development Best Practices
- **Profile memory usage** during development
- **Use memory profilers** (JProfiler, YourKit, async-profiler)
- **Load testing** with realistic data volumes
- **Monitor object allocation rates** in CI/CD

### 2. Production Readiness
```bash
# Essential JVM flags for production
java -server \
     -Xmx4g -Xms2g \
     -XX:+UseG1GC \
     -XX:MaxGCPauseMillis=200 \
     -Xlog:gc*:gc.log:time \
     -XX:+HeapDumpOnOutOfMemoryError \
     -XX:HeapDumpPath=/dumps/ \
     -jar application.jar
```

### 3. Monitoring and Alerting
- **JVM metrics** in monitoring systems (Prometheus, Grafana)
- **GC time percentage** alerts (>5% is concerning)
- **Old generation usage** alerts (>80% sustained)
- **Heap dump automation** on OOM conditions

## Key Takeaways

1. **GC thrashing is preventable** with proper heap sizing and memory management
2. **Monitor GC metrics continuously** - time percentage and old gen usage
3. **Use appropriate GC algorithms** for your workload characteristics
4. **Implement streaming patterns** instead of loading all data into memory
5. **Set explicit GC limits** to fail fast rather than thrash indefinitely
6. **Profile before optimizing** - understand actual memory usage patterns
7. **Plan for container memory overhead** - heap is not the only memory usage
8. **Use bounded caches** and proper eviction policies everywhere

**Container Memory Formula:** `Container Limit = Xmx + Direct Memory + Metaspace + OS Overhead (~512MB)`