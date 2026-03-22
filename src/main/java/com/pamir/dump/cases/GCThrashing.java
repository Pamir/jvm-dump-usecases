package com.pamir.dump.cases;

import java.util.ArrayList;
import java.util.List;

public class GCThrashing implements Case {
    
    @Override
    public void run() {
        System.out.println("Starting GC Thrashing case - filling heap to ~95% then continuously allocating");
        System.out.println("Run with -Xmx64m to trigger GC overhead limit exceeded quickly");
        
        List<byte[]> retainedObjects = new ArrayList<>();
        Runtime runtime = Runtime.getRuntime();
        
        // Fill heap to ~90% with retained objects
        long maxMemory = runtime.maxMemory();
        long targetMemory = (long) (maxMemory * 0.9);
        
        System.out.println("Max memory: " + (maxMemory / 1024 / 1024) + "MB");
        System.out.println("Filling heap to ~90% (" + (targetMemory / 1024 / 1024) + "MB)");
        
        while (runtime.totalMemory() - runtime.freeMemory() < targetMemory) {
            try {
                retainedObjects.add(new byte[1024]); // 1KB chunks
            } catch (OutOfMemoryError e) {
                System.out.println("Hit OOM while filling retained objects");
                break;
            }
        }
        
        System.out.println("Filled " + retainedObjects.size() + " KB with retained objects");
        System.out.println("Now continuously allocating small objects to cause GC thrashing...");
        
        int iteration = 0;
        long startTime = System.currentTimeMillis();
        
        while (true) {
            try {
                // Allocate objects that will quickly become garbage
                for (int i = 0; i < 1000; i++) {
                    byte[] garbage = new byte[1024];
                    // Don't store reference - let it become garbage
                }
                
                iteration++;
                if (iteration % 100 == 0) {
                    long usedMemory = runtime.totalMemory() - runtime.freeMemory();
                    double memoryPercent = (double) usedMemory / maxMemory * 100;
                    long elapsed = System.currentTimeMillis() - startTime;
                    System.out.printf("Iteration %d, Memory usage: %.1f%%, Time: %dms%n", 
                                    iteration, memoryPercent, elapsed);
                    
                    // Force GC to see the thrashing effect
                    System.gc();
                }
                
            } catch (OutOfMemoryError e) {
                System.out.println("OutOfMemoryError: " + e.getMessage());
                System.out.println("GC thrashing successfully demonstrated");
                break;
            }
        }
    }
}