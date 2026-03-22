package com.pamir.dump.cases;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class OffHeapLeak implements Case {
    
    @Override
    public void run() {
        System.out.println("Starting Off-Heap Leak case - allocating DirectByteBuffers without releasing");
        System.out.println("Run with -XX:MaxDirectMemorySize=128m to trigger faster");
        
        List<ByteBuffer> directBuffers = new ArrayList<>();
        int bufferCount = 0;
        final int bufferSize = 1024 * 1024; // 1MB per buffer
        
        Runtime runtime = Runtime.getRuntime();
        
        try {
            while (true) {
                // Allocate direct memory (off-heap)
                ByteBuffer directBuffer = ByteBuffer.allocateDirect(bufferSize);
                
                // Fill the buffer with some data to ensure it's actually allocated
                directBuffer.putInt(0, bufferCount);
                
                // Store reference to prevent GC from cleaning it up
                directBuffers.add(directBuffer);
                bufferCount++;
                
                if (bufferCount % 10 == 0) {
                    long heapUsed = runtime.totalMemory() - runtime.freeMemory();
                    long heapMax = runtime.maxMemory();
                    
                    System.out.printf("Allocated %d direct buffers (%d MB off-heap), Heap: %d/%d MB%n",
                                    bufferCount, 
                                    bufferCount,
                                    heapUsed / 1024 / 1024,
                                    heapMax / 1024 / 1024);
                }
                
                if (bufferCount % 50 == 0) {
                    // Try to force GC to show that heap stays flat while off-heap grows
                    System.gc();
                    System.out.println("Forced GC - heap usage should remain relatively stable");
                }
                
                // Small delay to make the leak more observable
                Thread.sleep(50);
                
            }
            
        } catch (OutOfMemoryError e) {
            System.out.println("OutOfMemoryError occurred: " + e.getMessage());
            System.out.println("Successfully triggered off-heap memory exhaustion");
            System.out.println("Total direct buffers allocated: " + bufferCount);
            System.out.println("Total off-heap memory allocated: " + bufferCount + "MB");
            
            if (e.getMessage().contains("Direct buffer memory")) {
                System.out.println("✓ Direct buffer memory OOM confirmed!");
            } else {
                System.out.println("OOM occurred but may not be direct memory: " + e.getMessage());
            }
            
            // Show final heap usage
            long heapUsed = runtime.totalMemory() - runtime.freeMemory();
            System.out.println("Final heap usage: " + (heapUsed / 1024 / 1024) + "MB (should be low)");
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Thread interrupted");
        } catch (Exception e) {
            System.out.println("Unexpected error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}