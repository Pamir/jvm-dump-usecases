package com.pamir.dump.cases;

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

public class ConnectionPoolExhaustion implements Case {
    private static final int POOL_SIZE = 5;
    private static final int THREAD_COUNT = 20;
    private final Semaphore connectionPool = new Semaphore(POOL_SIZE);
    private final AtomicInteger activeConnections = new AtomicInteger(0);
    
    @Override
    public void run() {
        System.out.println("Starting Connection Pool Exhaustion case");
        System.out.printf("Pool size: %d, Threads requesting: %d%n", POOL_SIZE, THREAD_COUNT);
        System.out.println("First 5 threads will get connections, remaining 15 will be stuck waiting");
        
        // Launch threads that request connections but never return them
        for (int i = 1; i <= THREAD_COUNT; i++) {
            final int threadId = i;
            Thread worker = new Thread(() -> {
                try {
                    System.out.printf("Thread %d: Requesting connection...%n", threadId);
                    
                    // Try to acquire a connection from the pool
                    connectionPool.acquire();
                    
                    int activeCount = activeConnections.incrementAndGet();
                    System.out.printf("Thread %d: Got connection! Active: %d/%d%n", 
                                    threadId, activeCount, POOL_SIZE);
                    
                    // Simulate a slow operation (never return the connection)
                    // This is the bug - connections are never returned to pool
                    Thread.sleep(30000); // Hold connection for 30 seconds
                    
                } catch (InterruptedException e) {
                    System.out.printf("Thread %d: Interrupted while waiting for connection%n", threadId);
                    Thread.currentThread().interrupt();
                } finally {
                    // In a real scenario, we should return the connection here
                    // But we're intentionally NOT doing it to simulate the leak
                    // connectionPool.release();
                }
            }, "ConnectionWorker-" + threadId);
            
            worker.start();
            
            // Small delay between thread starts to make output clearer
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        // Monitor the pool status
        Thread monitor = new Thread(() -> {
            for (int i = 0; i < 60; i++) { // Monitor for 60 seconds
                try {
                    Thread.sleep(1000);
                    int available = connectionPool.availablePermits();
                    int active = activeConnections.get();
                    int waiting = connectionPool.getQueueLength();
                    
                    System.out.printf("Pool Status - Available: %d, Active: %d, Waiting: %d%n",
                                    available, active, waiting);
                    
                    if (waiting > 0) {
                        System.out.printf("⚠️  %d threads are blocked waiting for connections!%n", waiting);
                    }
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "PoolMonitor");
        
        monitor.start();
        
        try {
            monitor.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        System.out.println("Connection pool exhaustion demonstration complete");
        System.out.println("Use jstack to see threads WAITING on the semaphore");
    }
}