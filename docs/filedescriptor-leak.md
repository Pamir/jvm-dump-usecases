# File Descriptor Leak

## Description & Symptoms

**Symptoms:** `java.io.IOException: Too many open files` when attempting to open new files, create socket connections, or even load classes. The application becomes completely unable to perform I/O operations and may become totally unresponsive. All file-related operations fail with this error.

Users experience:
- Complete application breakdown - no file I/O works
- New network connections fail
- Log files stop being written
- Database connections fail
- Class loading errors (JVM can't open .jar files)
- Application restart required to recover

## How to Run

```bash
java -jar application.jar FileDescriptorLeak

# Monitor file descriptor count during execution:
watch -n 1 "ls /proc/\$(pgrep -f application.jar)/fd | wc -l"

# Check system limit:
ulimit -n
```

This simulation will:
1. Open files/sockets repeatedly without closing them
2. Show FD count growing over time
3. Eventually hit the system limit (usually 1024)
4. Demonstrate complete I/O failure once limit is reached

## How to Detect

### Count Open File Descriptors
```bash
# Method 1: Count FDs via /proc filesystem
ls /proc/<pid>/fd | wc -l

# Method 2: Using lsof (more detailed)
lsof -p <pid> | wc -l

# Method 3: For Java processes specifically
jstat -gc <pid> | awk '{print "Open FDs estimate:", NF*50}' # Rough estimate
```

### Check System Limits
```bash
# Per-process limit
ulimit -n

# Check current process limits
cat /proc/<pid>/limits | grep "Max open files"

# System-wide limits
cat /proc/sys/fs/file-max      # System maximum
cat /proc/sys/fs/file-nr       # Currently open system-wide
```

### Identify What's Open
```bash
# List all open files/sockets by type
lsof -p <pid> | awk '{print $5}' | sort | uniq -c | sort -rn

# Common output:
#  500 REG    (regular files)
#  300 sock   (sockets) 
#   50 PIPE   (pipes)
#   20 DIR    (directories)

# Find specific files being opened repeatedly
lsof -p <pid> | grep REG | awk '{print $9}' | sort | uniq -c | sort -rn
```

## Step-by-Step Analysis

### 1. Monitor FD Growth Over Time
```bash
# Script to track FD count
#!/bin/bash
PID=$(pgrep -f "your-application")
while true; do
    FD_COUNT=$(ls /proc/$PID/fd 2>/dev/null | wc -l)
    echo "$(date): FD count = $FD_COUNT"
    sleep 10
done
```

### 2. Identify File Descriptor Types
```bash
# Categorize open FDs
lsof -p <pid> | awk 'NR>1 {print $5}' | sort | uniq -c
```
Look for:
- **REG**: Regular files - check if same file opened multiple times
- **sock**: Sockets - possible connection pool leak
- **PIPE**: Pipes - check Process.exec() usage  
- **FIFO**: Named pipes - IPC mechanisms

### 3. Find Specific Leaking Resources
```bash
# Files opened multiple times (suspicious)
lsof -p <pid> | grep REG | awk '{print $9}' | sort | uniq -c | sort -rn | head -10

# Sockets in problematic states
lsof -p <pid> | grep sock | grep -E "CLOSE_WAIT|FIN_WAIT"

# Check for process pipes not being closed
lsof -p <pid> | grep PIPE
```

### 4. Review Code for Resource Leaks
Search codebase for patterns that commonly leak FDs:
```bash
# Find potential file leaks
grep -r "new FileInputStream\|new FileOutputStream\|new FileReader\|new FileWriter" src/
grep -r "Socket\|ServerSocket" src/
grep -r "Process\|Runtime.exec" src/

# Look for missing try-with-resources or .close() calls
```

### 5. Use JVM Monitoring
```java
// Add to application for runtime monitoring
ManagementFactory.getOperatingSystemMXBean().getOpenFileDescriptorCount(); // Unix only
```

## Root Cause

Every I/O resource (files, sockets, pipes) consumes a file descriptor. The OS limits how many FDs a process can have open simultaneously (typically 1024). When resources are opened but never closed, FDs accumulate until the limit is reached.

Common causes:
- **Missing `close()`** calls on streams, readers, writers
- **HTTP clients** not properly closed after requests
- **JDBC connections** not returned to pool
- **Process.exec()** without closing stdin/stdout/stderr streams
- **Socket connections** not closed (especially in error paths)
- **File watchers/listeners** not properly disposed

## How to Fix / Prevent

### 1. Always Use Try-With-Resources
```java
// ❌ BAD - File descriptor leak
FileInputStream fis = new FileInputStream("data.txt");
byte[] data = fis.readAllBytes();
// If exception occurs here, fis never closed!

// ✅ GOOD - Automatic cleanup
try (FileInputStream fis = new FileInputStream("data.txt")) {
    byte[] data = fis.readAllBytes();
    return data;
} // fis.close() called automatically, even on exception
```

### 2. Properly Close HTTP Clients
```java
// ❌ BAD - Connection/FD leak  
CloseableHttpClient client = HttpClients.createDefault();
HttpGet request = new HttpGet("http://api.example.com/data");
CloseableHttpResponse response = client.execute(request);
// client and response never closed!

// ✅ GOOD - Proper resource management
try (CloseableHttpClient client = HttpClients.createDefault();
     CloseableHttpResponse response = client.execute(request)) {
    
    HttpEntity entity = response.getEntity();
    String result = EntityUtils.toString(entity);
    return result;
} // Both client and response closed automatically
```

### 3. Handle Process Execution Properly
```java
// ❌ BAD - Process streams leak FDs
Process process = Runtime.getRuntime().exec("external-command");
InputStream stdout = process.getInputStream();
// Streams never closed!

// ✅ GOOD - Close all process streams
Process process = Runtime.getRuntime().exec("external-command");
try {
    // Consume and close all streams
    try (InputStream stdout = process.getInputStream();
         InputStream stderr = process.getErrorStream();
         OutputStream stdin = process.getOutputStream()) {
        
        // Process streams
        String output = new String(stdout.readAllBytes());
        return output;
    }
} finally {
    process.destroyForcibly(); // Cleanup process
}
```

### 4. Implement FD Monitoring
```java
@Component
public class FileDescriptorMonitor {
    
    private static final Logger log = LoggerFactory.getLogger(FileDescriptorMonitor.class);
    
    @Scheduled(fixedDelay = 30000) // Every 30 seconds
    public void monitorFileDescriptors() {
        try {
            OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
            
            if (osBean instanceof UnixOperatingSystemMXBean) {
                UnixOperatingSystemMXBean unixBean = (UnixOperatingSystemMXBean) osBean;
                long openFDs = unixBean.getOpenFileDescriptorCount();
                long maxFDs = unixBean.getMaxFileDescriptorCount();
                
                double utilization = (double) openFDs / maxFDs;
                
                log.info("File Descriptors: {}/{} ({}% utilization)", 
                        openFDs, maxFDs, String.format("%.1f", utilization * 100));
                
                // Alert if utilization > 80%
                if (utilization > 0.8) {
                    log.warn("HIGH FD utilization: {}/{} ({}%)", 
                            openFDs, maxFDs, String.format("%.1f", utilization * 100));
                }
            }
        } catch (Exception e) {
            log.error("Error monitoring file descriptors", e);
        }
    }
}
```

### 5. Use Connection Pooling
```java
// ✅ Reuse connections instead of creating new ones
@Configuration
public class HttpClientConfig {
    
    @Bean
    public CloseableHttpClient httpClient() {
        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
        cm.setMaxTotal(200);                    // Total pool size
        cm.setDefaultMaxPerRoute(20);           // Per-route limit
        
        return HttpClients.custom()
                .setConnectionManager(cm)
                .setConnectionTimeToLive(30, TimeUnit.SECONDS)
                .build();
    }
}
```

### 6. Configure Resource Limits
```java
// Database connection pool settings
spring.datasource.hikari.maximum-pool-size=20
spring.datasource.hikari.connection-timeout=30000
spring.datasource.hikari.max-lifetime=1800000

// HTTP client settings  
http.client.connection.pool.max-total=100
http.client.connection.pool.max-per-route=20
http.client.connection.timeout=5000
```

### 7. Add FD Leak Detection
```java
public class ResourceTracker {
    private static final Map<String, Long> openResources = new ConcurrentHashMap<>();
    
    public static void trackResource(String resourceId, String type) {
        openResources.put(resourceId, System.currentTimeMillis());
        log.debug("Opened {}: {}", type, resourceId);
    }
    
    public static void releaseResource(String resourceId, String type) {
        Long openTime = openResources.remove(resourceId);
        if (openTime != null) {
            long duration = System.currentTimeMillis() - openTime;
            log.debug("Closed {}: {} (held for {}ms)", type, resourceId, duration);
        }
    }
    
    @Scheduled(fixedDelay = 60000)
    public void reportLeaks() {
        long now = System.currentTimeMillis();
        openResources.entrySet().stream()
                .filter(entry -> now - entry.getValue() > 300000) // 5 minutes
                .forEach(entry -> log.warn("Potential resource leak: {} (open for {}ms)", 
                        entry.getKey(), now - entry.getValue()));
    }
}
```

### 8. Increase System Limits (Temporary)
```bash
# Per-process limit (session-only)
ulimit -n 65536

# Permanent increase (add to /etc/security/limits.conf)
myuser soft nofile 65536
myuser hard nofile 65536

# Systemd service (add to service file)
[Service]
LimitNOFILE=65536
```

## Prevention Strategies

- **Static analysis**: Use SpotBugs/FindBugs to detect unclosed resources
- **Code review**: Every resource acquisition must have corresponding release
- **Testing**: Load test with monitoring of FD count over time
- **Monitoring**: Alert when FD utilization > 80%
- **Resource pooling**: Reuse connections/files instead of creating new ones
- **Graceful shutdown**: Ensure all resources are closed on application shutdown

## Key Takeaways

- File descriptors are a finite system resource - treat them carefully
- Always use try-with-resources for automatic cleanup
- Monitor FD count in production and alert on high utilization
- Connection pooling is essential for network-heavy applications
- Process execution requires careful stream management
- Increasing system limits doesn't fix leaks - it just delays the problem
- Static analysis tools can catch many resource leak patterns
- Load testing should include FD monitoring to catch leaks early
- Every `new FileInputStream()`, `new Socket()`, `Runtime.exec()` needs corresponding cleanup