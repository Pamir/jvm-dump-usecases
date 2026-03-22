# Off-Heap / Direct ByteBuffer Memory Leak

## Problem Description and Symptoms

**Off-heap memory leaks** occur when applications allocate native memory (outside the JVM heap) but fail to properly release it. This memory is invisible to standard heap monitoring tools, making it particularly dangerous in containerized environments.

**Common symptoms:**
- RSS (Resident Set Size) grows continuously while heap usage remains stable
- Container gets OOMKilled by Kubernetes/Docker despite heap being under limit
- `java.lang.OutOfMemoryError: Direct buffer memory`
- Native memory continues growing even after full GC
- Application performance degrades due to memory pressure
- System becomes unresponsive due to memory exhaustion

## How to Run the Case

### Using Docker with Direct Memory Limit
```bash
docker run -it --rm --memory=256m \
  -e JAVA_OPTS="-Xmx128m -XX:MaxDirectMemorySize=64m -XX:NativeMemoryTracking=detail" \
  pamir/jvm-cases OffHeapLeak
```

### Direct Java Command
```bash
# Set small direct memory limit to trigger OOM quickly
java -Xmx128m \
     -XX:MaxDirectMemorySize=64m \
     -XX:NativeMemoryTracking=detail \
     -Dio.netty.leakDetection.level=PARANOID \
     -jar application.jar OffHeapLeak

# For debugging Netty applications specifically
java -Dio.netty.leakDetectionLevel=PARANOID \
     -Dio.netty.leakDetection.targetRecords=4 \
     -jar application.jar
```

### Enable Leak Detection
```bash
# Java Flight Recorder for native memory tracking
java -XX:+FlightRecorder \
     -XX:StartFlightRecording=duration=300s,filename=offheap-leak.jfr \
     -XX:FlightRecorderOptions=settings=profile \
     -jar application.jar OffHeapLeak
```

## How to Observe/Detect the Issue

### Process Memory vs Heap Comparison
```bash
# Check RSS vs heap usage disparity
ps aux | grep java | awk '{print "RSS:", $6/1024, "MB"}'
jcmd <pid> VM.memory_summary | grep heap

# Expected pattern: RSS >> Xmx indicates off-heap leak
# Example: RSS: 2048 MB, heap: 512 MB → 1.5GB off-heap usage
```

### Native Memory Tracking Analysis
```bash
# Enable NMT and take baseline
java -XX:NativeMemoryTracking=detail -jar application.jar &
PID=$!
jcmd $PID VM.native_memory baseline

# Monitor growth over time
sleep 60
jcmd $PID VM.native_memory summary.diff

# Expected output showing growth:
# Internal (reserved=2048000KB +512000KB, committed=1024000KB +256000KB)
```

### Direct ByteBuffer Monitoring
```bash
# Check direct buffer usage
jcmd <pid> VM.memory_summary | grep -A 5 "Direct buffer"

# Monitor DirectByteBuffer instances
jcmd <pid> GC.class_histogram | grep DirectByteBuffer
```

### System-Level Memory Analysis
```bash
# Linux memory mapping analysis
pmap -x <pid> | sort -k3 -nr | head -20

# Check for memory-mapped files and anonymous mappings
cat /proc/<pid>/maps | grep -E "(anon|deleted)"

# Container memory usage (if running in Docker)
docker stats <container_id>
```

## Step-by-Step Analysis

### 1. Confirm Off-Heap Growth Pattern
```bash
# Monitor RSS vs heap ratio over time
while true; do
    RSS_MB=$(ps -o pid,rss --pid $PID | tail -1 | awk '{print $2/1024}')
    HEAP_MB=$(jcmd $PID GC.heap_info | grep "Total" | awk '{print $3/1048576}')
    echo "$(date): RSS=${RSS_MB}MB, Heap=${HEAP_MB}MB, Ratio=$(echo "$RSS_MB/$HEAP_MB" | bc -l)"
    sleep 30
done
```

### 2. Native Memory Tracking Deep Dive
```bash
# Detailed breakdown of native memory usage
jcmd <pid> VM.native_memory detail > nmt-detail.txt

# Look for growing categories:
grep -A 3 "Internal\|Other\|Direct\|MMap" nmt-detail.txt

# Track specific allocation sites
jcmd <pid> VM.native_memory detail.diff | grep -E "\+.*KB"
```

### 3. Identify DirectByteBuffer Leaks
```bash
# Heap dump analysis for DirectByteBuffer references
jmap -dump:format=b,file=directbuffer-leak.hprof <pid>

# In Eclipse MAT:
# 1. Open heap dump
# 2. Search for "DirectByteBuffer" instances
# 3. Check "Path to GC Roots" for unreleased buffers
# 4. Look for Cleaner objects and their referents
```

### 4. Network Library Leak Detection
```bash
# Netty leak detection (if using Netty)
export JAVA_OPTS="$JAVA_OPTS -Dio.netty.leakDetection.level=PARANOID"

# Check application logs for leak reports:
grep -i "LEAK\|ByteBuf" application.log

# Example Netty leak log:
# LEAK: ByteBuf.release() was not called before it's garbage-collected.
```

### 5. Memory-Mapped File Analysis
```bash
# Check for unclosed memory-mapped files
lsof -p <pid> | grep -E "\.tmp|\.dat|\.idx"

# Analyze file descriptors
ls -la /proc/<pid>/fd/ | wc -l   # Check for FD leaks
```

## Root Cause

**Primary causes of off-heap memory leaks:**

### 1. DirectByteBuffer Not Released
Manual allocation without proper cleanup:
```java
// Memory allocated but never freed
ByteBuffer buffer = ByteBuffer.allocateDirect(1024 * 1024);
// Missing: buffer cleanup/release
```

### 2. NIO Channel Resource Leaks
Network operations without proper cleanup:
- **SocketChannel** not closed properly
- **FileChannel** for memory-mapped files not unmapped
- **Selector** resources not released

### 3. Netty Buffer Leaks
Most common in network applications:
- **ByteBuf.release()** not called
- **Unbalanced retain/release** calls
- **Exception handling** bypassing release logic

### 4. Memory-Mapped Files Not Unmapped
File I/O operations retaining mappings:
- **MappedByteBuffer** not cleaned up
- **RandomAccessFile** with memory mapping not closed
- **Chronicle Map** or similar libraries with mapping leaks

### 5. JNI Native Memory Allocation
Native code allocating memory without freeing it.

## How to Fix / Prevent

### 1. Set MaxDirectMemorySize Limit
```bash
# Always set explicit limit to fail fast
java -XX:MaxDirectMemorySize=512m -jar application.jar

# Use conservative sizing: typically 25-50% of heap size
# For 2GB heap: -XX:MaxDirectMemorySize=512m to 1g
```

### 2. Proper DirectByteBuffer Management
```java
// BAD: No cleanup mechanism
public void processData() {
    ByteBuffer buffer = ByteBuffer.allocateDirect(1024 * 1024);
    // Process data...
    // buffer never cleaned up - LEAK!
}

// GOOD: Try-with-resources pattern using custom wrapper
public class DirectBufferWrapper implements AutoCloseable {
    private ByteBuffer buffer;
    
    public DirectBufferWrapper(int capacity) {
        this.buffer = ByteBuffer.allocateDirect(capacity);
    }
    
    public ByteBuffer getBuffer() { return buffer; }
    
    @Override
    public void close() {
        if (buffer != null && buffer.isDirect()) {
            // Force cleanup using sun.misc.Cleaner
            ((DirectBuffer) buffer).cleaner().clean();
            buffer = null;
        }
    }
}

// Usage:
public void processData() {
    try (DirectBufferWrapper wrapper = new DirectBufferWrapper(1024 * 1024)) {
        ByteBuffer buffer = wrapper.getBuffer();
        // Process data...
    } // Automatic cleanup
}
```

### 3. Netty Proper Resource Management
```java
// BAD: ByteBuf leak
@Override
public void channelRead(ChannelHandlerContext ctx, Object msg) {
    ByteBuf buf = (ByteBuf) msg;
    // Process buffer...
    // Missing buf.release() - LEAK!
}

// GOOD: Always release in finally block
@Override
public void channelRead(ChannelHandlerContext ctx, Object msg) {
    ByteBuf buf = (ByteBuf) msg;
    try {
        // Process buffer...
        processBuffer(buf);
    } finally {
        buf.release(); // Critical: always release
    }
}

// BETTER: Use Netty's ReferenceCountUtil
@Override
public void channelRead(ChannelHandlerContext ctx, Object msg) {
    try {
        ByteBuf buf = (ByteBuf) msg;
        // Process buffer...
    } finally {
        ReferenceCountUtil.release(msg); // Handles all ReferenceCounted objects
    }
}
```

### 4. NIO Channel Proper Cleanup
```java
// BAD: Resource leak
public void copyFile(String src, String dest) throws IOException {
    FileChannel sourceChannel = FileChannel.open(Paths.get(src), StandardOpenOption.READ);
    FileChannel destChannel = FileChannel.open(Paths.get(dest), StandardOpenOption.WRITE, StandardOpenOption.CREATE);
    
    MappedByteBuffer buffer = sourceChannel.map(FileChannel.MapMode.READ_ONLY, 0, sourceChannel.size());
    destChannel.write(buffer);
    // Missing cleanup - LEAK!
}

// GOOD: Try-with-resources and explicit cleanup
public void copyFile(String src, String dest) throws IOException {
    try (FileChannel sourceChannel = FileChannel.open(Paths.get(src), StandardOpenOption.READ);
         FileChannel destChannel = FileChannel.open(Paths.get(dest), StandardOpenOption.WRITE, StandardOpenOption.CREATE)) {
        
        MappedByteBuffer buffer = sourceChannel.map(FileChannel.MapMode.READ_ONLY, 0, sourceChannel.size());
        try {
            destChannel.write(buffer);
        } finally {
            // Force unmap
            if (buffer instanceof DirectBuffer) {
                ((DirectBuffer) buffer).cleaner().clean();
            }
        }
    }
}
```

### 5. Container Memory Configuration
```yaml
# Kubernetes Pod specification
apiVersion: v1
kind: Pod
spec:
  containers:
  - name: java-app
    image: myapp:latest
    resources:
      limits:
        memory: "2Gi"  # Container limit
      requests:
        memory: "1.5Gi"
    env:
    - name: JAVA_OPTS
      value: "-Xmx1024m -XX:MaxDirectMemorySize=256m -XX:MaxMetaspaceSize=256m"
      
# Memory breakdown:
# Heap: 1024MB
# Direct: 256MB  
# Metaspace: 256MB
# Overhead: ~200MB
# Total: ~1736MB (fits in 2GB limit with buffer)
```

### 6. Monitoring and Leak Detection
```java
// Custom monitoring for direct memory
public class DirectMemoryMonitor {
    private static final MemoryMXBean memoryMBean = ManagementFactory.getMemoryMXBean();
    
    public static void logDirectMemoryUsage() {
        List<MemoryPoolMXBean> memoryPools = ManagementFactory.getMemoryPoolMXBeans();
        
        memoryPools.stream()
            .filter(pool -> pool.getName().contains("direct"))
            .forEach(pool -> {
                MemoryUsage usage = pool.getUsage();
                long usedMB = usage.getUsed() / (1024 * 1024);
                long maxMB = usage.getMax() / (1024 * 1024);
                
                logger.info("Direct Memory: {}MB / {}MB ({}%)", 
                    usedMB, maxMB, (usedMB * 100) / maxMB);
                    
                if ((usedMB * 100) / maxMB > 80) {
                    logger.warn("Direct memory usage > 80%!");
                }
            });
    }
}
```

### 7. Production Health Checks
```bash
#!/bin/bash
# Direct memory health check script
PID=$(pgrep java)

# Get RSS and heap usage
RSS_KB=$(ps -o rss --pid $PID --no-headers)
RSS_MB=$((RSS_KB / 1024))

HEAP_USED=$(jcmd $PID GC.heap_info | grep "Total" | awk '{print $3/1048576}')
HEAP_MAX=$(jcmd $PID GC.heap_info | grep "Total" | awk '{print $5/1048576}')

OFF_HEAP_MB=$((RSS_MB - HEAP_USED))
RATIO=$(echo "scale=2; $OFF_HEAP_MB / $HEAP_USED" | bc -l)

echo "RSS: ${RSS_MB}MB, Heap: ${HEAP_USED}MB/${HEAP_MAX}MB, Off-heap: ${OFF_HEAP_MB}MB, Ratio: ${RATIO}"

# Alert if off-heap > heap (suspicious)
if (( $(echo "$RATIO > 1.0" | bc -l) )); then
    echo "WARNING: Off-heap memory (${OFF_HEAP_MB}MB) > heap usage!" >&2
    jcmd $PID VM.native_memory summary | grep -E "Internal|Other|Direct" >&2
fi
```

## Prevention Strategies

### 1. Development Best Practices
- **Always use try-with-resources** for NIO operations
- **Enable Netty leak detection** in development: `-Dio.netty.leakDetection.level=PARANOID`
- **Set conservative direct memory limits** during development
- **Monitor RSS vs heap ratio** in integration tests

### 2. Code Review Checklist
- ✅ All `ByteBuffer.allocateDirect()` calls have corresponding cleanup
- ✅ All Netty `ByteBuf` operations call `release()`
- ✅ All file channels and memory-mapped buffers are properly closed
- ✅ Exception handling doesn't bypass resource cleanup

### 3. Production Deployment
```bash
# Comprehensive off-heap configuration
java -server \
     -Xmx2g \
     -XX:MaxDirectMemorySize=512m \
     -XX:NativeMemoryTracking=summary \
     -Dio.netty.leakDetection.level=SIMPLE \
     -XX:+UnlockDiagnosticVMOptions \
     -XX:+PrintNMTStatistics \
     -jar application.jar
```

### 4. Kubernetes Best Practices
```yaml
# Pod with proper resource limits and monitoring
spec:
  containers:
  - name: java-app
    resources:
      limits:
        memory: "3Gi"
      requests:  
        memory: "2.5Gi"
    livenessProbe:
      exec:
        command:
        - /bin/bash
        - -c
        - |
          RSS_MB=$(ps -o rss --pid 1 --no-headers | awk '{print $1/1024}')
          if [ $RSS_MB -gt 2560 ]; then exit 1; fi
```

## Key Takeaways

1. **Always set MaxDirectMemorySize** to prevent unbounded off-heap growth
2. **Monitor RSS vs heap ratio** - ratio > 2.0 indicates potential off-heap leak
3. **Use try-with-resources** for all NIO operations and direct buffer allocations
4. **Enable Netty leak detection** in development and simple mode in production
5. **Plan container memory** as: heap + direct + metaspace + OS overhead
6. **Never ignore DirectByteBuffer cleanup** - implement proper resource management
7. **Use native memory tracking** to understand off-heap allocation patterns
8. **Test under memory pressure** to ensure proper cleanup under stress conditions

**Container Memory Planning Formula:**
```
Container Limit = Xmx + MaxDirectMemorySize + MaxMetaspaceSize + OS Overhead (512MB)
```

**Example for 2GB heap application:**
- Heap: 2048MB
- Direct: 512MB  
- Metaspace: 256MB
- OS Overhead: 512MB
- **Total Container Limit: 3328MB (3.25GB)**

**Off-heap Memory Ratio Alert Thresholds:**
- **Normal:** Off-heap < 50% of heap usage
- **Warning:** Off-heap > 100% of heap usage  
- **Critical:** Off-heap > 200% of heap usage