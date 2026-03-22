# Connection Pool Exhaustion

## Description & Symptoms

**Symptoms:** Application becomes unresponsive for database operations. New requests hang indefinitely. Thread dump shows many threads in WAITING state trying to acquire pool connections. Log messages contain "Connection pool exhausted", "Timeout waiting for connection", or "Request timed out after XXXXms".

Users experience:
- Database operations timing out
- HTTP requests hanging on database queries
- Application appears frozen but CPU usage is normal
- Eventually leads to complete application unresponsiveness

## How to Run

```bash
java -jar application.jar ConnectionPoolExhaustion
```

This simulation will:
1. Create a small connection pool (e.g., 5 connections)
2. Launch multiple threads that acquire connections
3. Simulate connections not being returned (missing `close()`)
4. Show pool exhaustion as more threads request connections

## How to Detect

### Thread Dump Analysis
```bash
jstack <pid>
# Look for threads in WAITING state on Semaphore or pool queue
# Many threads stuck at patterns like:
#   java.util.concurrent.Semaphore.acquire()
#   com.zaxxer.hikari.pool.HikariPool.getConnection()
#   java.util.concurrent.LinkedBlockingQueue.take()
```

### Application Logs
```bash
# HikariCP messages:
HikariPool-1 - Connection is not available, request timed out after 30000ms
HikariPool-1 - Pool stats (total=10, active=10, idle=0, waiting=25)

# Generic pool messages:
Connection pool exhausted, max pool size reached
Timeout waiting for idle object
```

### JMX Metrics
Monitor pool metrics via JMX or application metrics:
- `active_connections` = `max_pool_size` (all busy)
- `waiting_threads` > 0 (requests queued)
- `connection_timeout_count` increasing

## Step-by-Step Analysis

### 1. Thread Dump Analysis
Count threads waiting for connections:
```bash
jstack <pid> | grep -A 5 -B 5 "HikariPool\|ConnectionPool\|Semaphore.acquire"
```

Look for patterns:
- Multiple threads with identical stack traces ending in pool acquisition
- High number of threads in WAITING state
- Thread names indicating web request handlers (e.g., `http-nio-8080-exec-N`)

### 2. Check Pool Configuration
Review connection pool settings:
```properties
# HikariCP example
spring.datasource.hikari.maximum-pool-size=10
spring.datasource.hikari.connection-timeout=30000
spring.datasource.hikari.leak-detection-threshold=60000
```

### 3. Connection Lifecycle Audit
Identify where connections are acquired but not returned:
- Search codebase for `getConnection()` calls
- Verify each has corresponding `close()` in finally block or try-with-resources
- Check error handling paths (connections often leaked in exception scenarios)

### 4. Long-Running Query Detection
Check for queries that hold connections too long:
```sql
-- PostgreSQL: Find long-running queries
SELECT pid, now() - pg_stat_activity.query_start AS duration, query 
FROM pg_stat_activity 
WHERE (now() - pg_stat_activity.query_start) > interval '5 minutes';
```

## Root Cause

Connection pools have a fixed number of database connections. When connections are borrowed but never returned, the pool becomes exhausted. Common causes:

1. **Missing connection.close()** - especially in error paths
2. **Long-running queries** - blocking all available connections
3. **Pool undersized** - too few connections for concurrent load
4. **Transaction not committed/rolled back** - connection stays "in use"
5. **Network issues** - connections stuck waiting for database response

## How to Fix / Prevent

### 1. Always Use Try-With-Resources
```java
// ❌ BAD: Connection leak potential
Connection conn = dataSource.getConnection();
PreparedStatement ps = conn.prepareStatement(sql);
ResultSet rs = ps.executeQuery();
// If exception occurs here, connection never closed!
conn.close();

// ✅ GOOD: Auto-closed even on exception
try (Connection conn = dataSource.getConnection();
     PreparedStatement ps = conn.prepareStatement(sql);
     ResultSet rs = ps.executeQuery()) {
    
    while (rs.next()) {
        // process results
    }
} // conn.close() called automatically
```

### 2. Configure Connection Pool Properly
```properties
# HikariCP configuration
spring.datasource.hikari.maximum-pool-size=20
spring.datasource.hikari.connection-timeout=5000        # Fail fast
spring.datasource.hikari.max-lifetime=1800000           # 30 minutes
spring.datasource.hikari.idle-timeout=300000            # 5 minutes
spring.datasource.hikari.leak-detection-threshold=30000 # Log leaks >30s
spring.datasource.hikari.validation-timeout=3000
```

### 3. Right-Size the Pool
Use the PostgreSQL formula as a starting point:
```
pool_size = (number_of_cores * 2) + number_of_spindle_disks
```

For cloud databases or SSDs, start with `cores * 2` and tune based on monitoring.

### 4. Enable Connection Leak Detection
```java
@Configuration
public class DataSourceConfig {
    
    @Bean
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://localhost/mydb");
        config.setUsername("user");
        config.setPassword("password");
        
        // Leak detection - logs stack trace of connection acquisition
        config.setLeakDetectionThreshold(30000); // 30 seconds
        
        return new HikariDataSource(config);
    }
}
```

### 5. Implement Connection Pool Monitoring
```java
@Component
public class PoolMonitor {
    
    @Autowired
    private HikariDataSource dataSource;
    
    @EventListener
    @Scheduled(fixedDelay = 30000)
    public void logPoolStats() {
        HikariPoolMXBean pool = dataSource.getHikariPoolMXBean();
        
        log.info("Pool stats - Active: {}, Idle: {}, Waiting: {}, Total: {}", 
                pool.getActiveConnections(),
                pool.getIdleConnections(), 
                pool.getThreadsAwaitingConnection(),
                pool.getTotalConnections());
        
        // Alert if pool utilization > 80%
        if (pool.getActiveConnections() > pool.getTotalConnections() * 0.8) {
            log.warn("Connection pool utilization high: {}%", 
                    (pool.getActiveConnections() * 100 / pool.getTotalConnections()));
        }
    }
}
```

### 6. Set Query Timeouts
```java
try (Connection conn = dataSource.getConnection();
     PreparedStatement ps = conn.prepareStatement(sql)) {
    
    ps.setQueryTimeout(30); // 30 second query timeout
    ResultSet rs = ps.executeQuery();
    // process results
}
```

## Prevention Strategies

- **Code reviews**: Every `getConnection()` must have corresponding `close()`
- **Static analysis**: Use tools like SpotBugs to detect resource leaks
- **Testing**: Load test with realistic concurrent user counts
- **Monitoring**: Alert when pool utilization > 80%
- **Graceful degradation**: Circuit breaker pattern for database operations

## Key Takeaways

- Connection pools are finite resources - treat them carefully
- Always use try-with-resources for automatic cleanup
- Enable leak detection in all environments (dev, staging, prod)
- Monitor pool metrics and alert on high utilization
- Size pools based on actual load testing, not guesswork
- Fast failure (connection timeout) is better than hanging indefinitely
- Regular connection validation prevents stale connection issues