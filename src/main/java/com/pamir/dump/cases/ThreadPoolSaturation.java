package com.pamir.dump.cases;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ThreadPoolSaturation implements Case {
    
    @Override
    public void run() {
        System.out.println("Starting Thread Pool Saturation case");
        
        // Create ThreadPoolExecutor with limited capacity
        int corePoolSize = 2;
        int maxPoolSize = 4;
        int queueCapacity = 10;
        
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
            corePoolSize,
            maxPoolSize,
            60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(queueCapacity)
        );
        
        System.out.printf("ThreadPool config - Core: %d, Max: %d, Queue: %d%n", 
                        corePoolSize, maxPoolSize, queueCapacity);
        
        AtomicInteger submittedTasks = new AtomicInteger(0);
        AtomicInteger rejectedTasks = new AtomicInteger(0);
        AtomicInteger completedTasks = new AtomicInteger(0);
        
        // Submit many long-running tasks to saturate the pool
        for (int i = 1; i <= 50; i++) {
            final int taskId = i;
            
            try {
                executor.submit(() -> {
                    try {
                        System.out.printf("Task %d: Started on thread %s%n", 
                                        taskId, Thread.currentThread().getName());
                        
                        // Simulate long-running work
                        Thread.sleep(10000); // 10 seconds
                        
                        System.out.printf("Task %d: Completed%n", taskId);
                        completedTasks.incrementAndGet();
                        
                    } catch (InterruptedException e) {
                        System.out.printf("Task %d: Interrupted%n", taskId);
                        Thread.currentThread().interrupt();
                    }
                });
                
                submittedTasks.incrementAndGet();
                System.out.printf("Submitted task %d%n", taskId);
                
            } catch (RejectedExecutionException e) {
                rejectedTasks.incrementAndGet();
                System.out.printf("❌ Task %d REJECTED: %s%n", taskId, e.getMessage());
            }
            
            // Show pool status after every few submissions
            if (i % 5 == 0) {
                showPoolStatus(executor, submittedTasks.get(), rejectedTasks.get(), completedTasks.get());
            }
            
            // Small delay between submissions
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        // Monitor pool status for a while
        System.out.println("\n=== Monitoring pool status ===");
        for (int i = 0; i < 30; i++) {
            try {
                Thread.sleep(1000);
                showPoolStatus(executor, submittedTasks.get(), rejectedTasks.get(), completedTasks.get());
                
                if (executor.getActiveCount() == 0 && executor.getQueue().isEmpty()) {
                    System.out.println("All tasks completed or queue is empty");
                    break;
                }
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        // Shutdown the executor
        System.out.println("\nShutting down executor...");
        executor.shutdown();
        
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                System.out.println("Forcing shutdown...");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        System.out.println("Thread pool saturation demonstration complete");
        System.out.printf("Final stats - Submitted: %d, Rejected: %d, Completed: %d%n",
                        submittedTasks.get(), rejectedTasks.get(), completedTasks.get());
    }
    
    private void showPoolStatus(ThreadPoolExecutor executor, int submitted, int rejected, int completed) {
        System.out.printf("Pool Status - Active: %d/%d, Queue: %d, Submitted: %d, Rejected: %d, Completed: %d%n",
                        executor.getActiveCount(),
                        executor.getMaximumPoolSize(),
                        executor.getQueue().size(),
                        submitted,
                        rejected,
                        completed);
    }
}