# Deadlock

## Problem Description and Symptoms

A **deadlock** occurs when two or more threads are permanently blocked, each waiting for a resource held by another thread. The application hangs completely with low CPU usage and no progress on any thread.

**Common symptoms:**
- Application becomes completely unresponsive
- Low CPU utilization despite hanging threads
- No progress in application logs
- User interface freezes (if applicable)
- Database connections pile up in pools

## How to Run the Case

### Using Docker
```bash
docker run -it --rm pamir/jvm-cases Deadlock
```

### Direct Java Command
```bash
java -jar application.jar Deadlock
# Or with specific main class:
java -cp application.jar com.pamir.dump.cases.Deadlock
```

## How to Observe/Detect the Issue

### Thread Dump Analysis
```bash
# Get thread dump using jstack
jstack <pid>

# Alternative using jcmd
jcmd <pid> Thread.print

# For containerized applications
docker exec <container_id> jstack 1
```

### Expected jstack Output Pattern
```
Found one Java-level deadlock:
=============================
"Thread-1":
  waiting to lock monitor 0x00007f8b8c004e58 (object 0x000000076ab62208, a java.lang.Object),
  which is held by "Thread-2"
"Thread-2":
  waiting to lock monitor 0x00007f8b8c004e08 (object 0x000000076ab62218, a java.lang.Object),
  which is held by "Thread-1"

Java stack information for the threads listed above:
===================================================
"Thread-1":
	at com.pamir.dump.cases.Deadlock.method1(Deadlock.java:45)
	- waiting to lock <0x000000076ab62208> (a java.lang.Object)
	- locked <0x000000076ab62218> (a java.lang.Object)
```

## Step-by-Step Analysis

### 1. Identify Blocked Threads
```bash
jstack <pid> | grep -A 5 -B 5 BLOCKED
```

### 2. Look for Deadlock Detection
```bash
jstack <pid> | grep -A 20 "Found one Java-level deadlock"
```

### 3. Analyze Lock Acquisition Pattern
- Note which locks each thread holds (`locked <address>`)
- Note which locks each thread waits for (`waiting to lock <address>`)
- Map addresses back to object types and code locations

### 4. Trace Back to Source Code
```bash
# Find the exact line numbers from stack trace
grep -n "method1\|method2" src/main/java/com/pamir/dump/cases/Deadlock.java
```

### 5. Continuous Monitoring
```bash
# Monitor thread states over time
while true; do
  jcmd <pid> Thread.print | grep "BLOCKED\|WAITING"
  sleep 5
done
```

## Root Cause

**Primary cause:** Two or more threads acquire locks in different order, creating a circular dependency.

**Typical scenario:**
- Thread A: acquires Lock 1 → tries to acquire Lock 2
- Thread B: acquires Lock 2 → tries to acquire Lock 1
- Result: Both threads wait indefinitely

**Common patterns leading to deadlock:**
1. **Inconsistent lock ordering** across methods
2. **Nested synchronization** without proper design
3. **Cross-module dependencies** with different locking strategies
4. **Resource pooling** without timeout mechanisms

## How to Fix / Prevent

### 1. Consistent Lock Ordering
```java
// BAD: Inconsistent lock ordering
public void transferMoney(Account from, Account to, double amount) {
    synchronized(from) {
        synchronized(to) {
            from.withdraw(amount);
            to.deposit(amount);
        }
    }
}

// GOOD: Consistent lock ordering
public void transferMoney(Account from, Account to, double amount) {
    Account firstLock = System.identityHashCode(from) < System.identityHashCode(to) ? from : to;
    Account secondLock = firstLock == from ? to : from;
    
    synchronized(firstLock) {
        synchronized(secondLock) {
            from.withdraw(amount);
            to.deposit(amount);
        }
    }
}
```

### 2. Use Timeout-Based Locking
```java
// Replace synchronized with tryLock
public boolean transferMoney(Account from, Account to, double amount) {
    if (from.getLock().tryLock(5, TimeUnit.SECONDS)) {
        try {
            if (to.getLock().tryLock(5, TimeUnit.SECONDS)) {
                try {
                    from.withdraw(amount);
                    to.deposit(amount);
                    return true;
                } finally {
                    to.getLock().unlock();
                }
            }
        } finally {
            from.getLock().unlock();
        }
    }
    return false; // Transfer failed due to timeout
}
```

### 3. Use java.util.concurrent Collections
```java
// Replace synchronized collections with concurrent ones
Map<String, Account> accounts = new ConcurrentHashMap<>();
AtomicLong balanceSum = new AtomicLong();

// Use atomic operations instead of synchronized blocks
public void updateBalance(String accountId, double amount) {
    accounts.computeIfPresent(accountId, (id, account) -> {
        account.addBalance(amount);
        return account;
    });
}
```

### 4. Reduce Lock Scope
```java
// BAD: Large synchronized block
public synchronized void processLargeDataSet() {
    prepareData();           // Long operation
    transformData();         // Long operation
    persistData();           // Long operation
}

// GOOD: Minimize synchronized scope
public void processLargeDataSet() {
    Data prepared = prepareData();
    Data transformed = transformData(prepared);
    
    synchronized(this) {     // Only critical section
        persistData(transformed);
    }
}
```

## Prevention Strategies

### 1. Development Time
- **Static analysis tools:** FindBugs, SpotBugs, SonarQube detect potential deadlocks
- **Code reviews:** Focus on locking patterns and ordering
- **Unit tests:** Simulate concurrent access patterns

### 2. Runtime Monitoring
```bash
# Health check script
#!/bin/bash
DEADLOCK_CHECK=$(jcmd $PID Thread.print | grep "Found one Java-level deadlock")
if [ ! -z "$DEADLOCK_CHECK" ]; then
    echo "DEADLOCK DETECTED" | logger -p local0.error
    # Restart application or alert operations team
fi
```

### 3. JVM Options for Detection
```bash
java -XX:+PrintConcurrentLocks \
     -XX:+PrintGCApplicationStoppedTime \
     -jar application.jar
```

## Key Takeaways

1. **Always use consistent lock ordering** when multiple locks are needed
2. **Prefer `java.util.concurrent`** collections over manual synchronization
3. **Use timeout-based locking** (`tryLock`) instead of blocking indefinitely
4. **Keep synchronized blocks small** and avoid nested synchronization
5. **Monitor for deadlocks in production** using automated thread dump analysis
6. **Design for concurrency upfront** rather than adding synchronization as an afterthought
7. **Test concurrent scenarios** during development with tools like JCStress

**Remember:** Prevention is always better than detection. Design your locking strategy carefully from the beginning.