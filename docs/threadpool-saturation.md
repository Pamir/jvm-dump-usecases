# Thread Pool Saturation

## Description & Symptoms

**Symptoms:** Tasks start being rejected with `RejectedExecutionException`. Application stops processing new requests or processes them extremely slowly. Response times spike dramatically. Thread dump shows all thread pool workers are RUNNABLE (actively busy) with no idle threads available.

Users experience:
- HTTP 500 errors from rejected tasks
- Request timeouts and extremely slow responses
- Background job processing stops
- Application appears to "freeze" under load

## How to Run

```bash
java -jar application.jar ThreadPoolSaturation
```

This simulation will:
1. Create a small thread pool (e.g., 5 core threads, 10 max, queue size 50)
2. Submit tasks faster than they can be completed
3. Fill the work queue completely
4. Demonstrate task rejections when pool + queue are saturated

## How to Detect

### Application Logs
```bash
# Look for RejectedExecutionException messages:
java.util.concurrent.RejectedExecutionException: Task java.util.concurrent.FutureTask@abc123 
  rejected from java.util.concurrent.ThreadPoolExecutor@def456
  [Running, pool size = 10, active threads = 10, queued tasks = 1000, completed tasks = 5432]
```

### Thread Dump Analysis
```bash
jstack <pid>
# All pool-N-thread-M threads are RUNNABLE (busy processing)
# No threads in WAITING/TIMED_WAITING state within the pool
# Example pattern:
#   "pool-1-thread-1" #10 prio=5 os_prio=0 tid=0x... nid=0x... runnable
#   "pool-1-thread-2" #11 prio=5 os_prio=0 tid=0x... nid=0x... runnable
```

### JMX Monitoring
Check ThreadPoolExecutor metrics:
- `activeCount` = `maximumPoolSize` (all threads busy)
- `queueSize` approaching `queueCapacity`
- `rejectedExecutionCount` > 0 (tasks being rejected)

## Step-by-Step Analysis

### 1. Identify Saturated Thread Pools
```bash
jstack <pid> | grep -E "pool-.*-thread-" | head -20
# Count active vs total threads in each pool
```

### 2. Check Task Queue Depth
Monitor via JMX or custom metrics:
```java
ThreadPoolExecutor executor = (ThreadPoolExecutor) this.executor;
int queueSize = executor.getQueue().size();
int activeThreads = executor.getActiveCount();
int poolSize = executor.getPoolSize();
```

### 3. Analyze What Threads Are Doing
From thread dump, identify if threads are:
- **I/O bound**: Waiting for network/database responses
- **CPU bound**: Heavy computation (parsing, calculations)
- **Blocked**: Waiting on locks or synchronization
- **Stuck**: Infinite loops or deadlocks

### 4. Measure Task Execution Time
```java
long startTime = System.currentTimeMillis();
// execute task
long duration = System.currentTimeMillis() - startTime;
// Are tasks taking longer than expected?
```

### 5. Check Task Submission Rate
Compare submission rate vs completion rate:
```java
// Monitor these metrics over time
executor.getTaskCount();        // Total submitted
executor.getCompletedTaskCount(); // Total completed
```

## Root Cause

Thread pools have limited capacity (threads + queue). When task submission rate exceeds completion rate, the pool becomes saturated. All threads are busy, queue fills up, and new tasks get rejected.

Common scenarios:
- **Traffic spike**: Sudden increase in request volume
- **Downstream slowness**: External services responding slowly
- **Resource contention**: Database connections, file I/O bottlenecks
- **Poor task design**: Tasks taking much longer than expected
- **Undersized pool**: Not enough threads for the workload

## How to Fix / Prevent

### 1. Use CallerRunsPolicy for Backpressure
```java
// ❌ BAD: Default AbortPolicy throws RejectedExecutionException
ThreadPoolExecutor executor = new ThreadPoolExecutor(
    5,    // corePoolSize
    10,   // maximumPoolSize
    60L, TimeUnit.SECONDS,
    new LinkedBlockingQueue<>(100)
    // Default: AbortPolicy - throws exception on rejection
);

// ✅ GOOD: CallerRunsPolicy provides natural backpressure
ThreadPoolExecutor executor = new ThreadPoolExecutor(
    5,    // corePoolSize
    10,   // maximumPoolSize
    60L, TimeUnit.SECONDS,
    new LinkedBlockingQueue<>(100),
    new ThreadPoolExecutor.CallerRunsPolicy() // Caller executes task
);
```

### 2. Right-Size the Thread Pool
```java
// For CPU-bound tasks
int coreCount = Runtime.getRuntime().availableProcessors();
int cpuBoundThreads = coreCount;

// For I/O-bound tasks (rule of thumb)
int ioBoundThreads = coreCount * (1 + waitTime / computeTime);
// Example: If 90% waiting, 10% compute: cores * (1 + 9) = cores * 10

ThreadPoolExecutor executor = new ThreadPoolExecutor(
    cpuBoundThreads,
    cpuBoundThreads * 2,    // Allow some burst capacity
    60L, TimeUnit.SECONDS,
    new LinkedBlockingQueue<>(1000),
    new ThreadPoolExecutor.CallerRunsPolicy()
);
```

### 3. Use Unbounded Queue with Monitoring
```java
// ❌ RISKY: Unbounded queue can cause OOM
new LinkedBlockingQueue<>()

// ✅ BETTER: Unbounded with monitoring and alerts
public class MonitoredThreadPoolExecutor extends ThreadPoolExecutor {
    
    public MonitoredThreadPoolExecutor(int corePoolSize, int maximumPoolSize, 
                                     long keepAliveTime, TimeUnit unit) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, 
              new LinkedBlockingQueue<>(), // Unbounded
              new CallerRunsPolicy());
    }
    
    @Override
    protected void afterExecute(Runnable r, Throwable t) {
        super.afterExecute(r, t);
        
        int queueSize = getQueue().size();
        if (queueSize > 1000) { // Alert threshold
            log.warn("Thread pool queue size high: {}", queueSize);
        }
    }
}
```

### 4. Implement Circuit Breaker Pattern
```java
// Using Resilience4j
@Component
public class TaskService {
    
    private final CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("taskService");
    
    public CompletableFuture<String> processTask(String input) {
        Supplier<CompletableFuture<String>> decoratedSupplier = 
            CircuitBreaker.decorateSupplier(circuitBreaker, () -> {
                return executorService.submit(() -> heavyProcessing(input));
            });
        
        return decoratedSupplier.get();
    }
}
```

### 5. Use Async I/O for I/O-Bound Tasks
```java
// ❌ BAD: Blocking I/O ties up threads
@Async
public Future<String> callExternalService(String request) {
    RestTemplate restTemplate = new RestTemplate();
    return new AsyncResult<>(restTemplate.postForObject(url, request, String.class));
}

// ✅ GOOD: Non-blocking I/O with WebClient
@Service
public class AsyncService {
    
    private final WebClient webClient = WebClient.builder().build();
    
    public CompletableFuture<String> callExternalService(String request) {
        return webClient.post()
                .uri("/external-api")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(String.class)
                .toFuture();
    }
}
```

### 6. Implement Comprehensive Monitoring
```java
@Component
public class ThreadPoolMonitor {
    
    private final MeterRegistry meterRegistry;
    private final ThreadPoolExecutor executor;
    
    @EventListener
    @Scheduled(fixedDelay = 10000) // Every 10 seconds
    public void recordMetrics() {
        Gauge.builder("threadpool.active.threads")
            .register(meterRegistry, executor, ThreadPoolExecutor::getActiveCount);
            
        Gauge.builder("threadpool.queue.size")
            .register(meterRegistry, executor, e -> e.getQueue().size());
            
        Gauge.builder("threadpool.completed.tasks")
            .register(meterRegistry, executor, ThreadPoolExecutor::getCompletedTaskCount);
            
        // Calculate utilization percentage
        double utilization = (double) executor.getActiveCount() / executor.getMaximumPoolSize();
        Gauge.builder("threadpool.utilization.percent")
            .register(meterRegistry, () -> utilization * 100);
    }
}
```

### 7. Configure Rejection Handling
```java
public enum RejectionStrategy {
    ABORT,          // Throw exception (default)
    CALLER_RUNS,    // Caller executes task (backpressure)
    DISCARD,        // Silently drop task
    DISCARD_OLDEST; // Drop oldest queued task
    
    public RejectedExecutionHandler getHandler() {
        switch (this) {
            case ABORT: return new AbortPolicy();
            case CALLER_RUNS: return new CallerRunsPolicy();
            case DISCARD: return new DiscardPolicy();
            case DISCARD_OLDEST: return new DiscardOldestPolicy();
            default: throw new IllegalStateException();
        }
    }
}
```

## Prevention Strategies

- **Load testing**: Test with realistic concurrent load to find optimal pool size
- **Monitoring**: Track pool utilization, queue depth, and rejection count
- **Gradual scaling**: Increase pool size gradually while monitoring resource usage
- **Circuit breakers**: Fail fast when downstream services are slow
- **Resource isolation**: Use separate thread pools for different types of work
- **Async processing**: Use message queues for non-urgent background tasks

## Key Takeaways

- Never use default `AbortPolicy` in production - always configure rejection handling
- Thread pool size should match the type of work (CPU-bound vs I/O-bound)
- Monitor queue depth and active thread count continuously
- Use `CallerRunsPolicy` for natural backpressure in most scenarios
- Separate thread pools for different workload types (web requests vs background jobs)
- Load test to determine optimal pool configuration for your specific workload
- Consider async I/O (WebClient, CompletableFuture) for I/O-heavy operations
- Implement circuit breakers to handle downstream service failures gracefully