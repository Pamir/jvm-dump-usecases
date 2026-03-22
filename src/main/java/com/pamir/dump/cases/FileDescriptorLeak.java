package com.pamir.dump.cases;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class FileDescriptorLeak implements Case {
    
    @Override
    public void run() {
        System.out.println("Starting File Descriptor Leak case - opening files without closing them");
        System.out.println("This will eventually trigger: java.io.IOException: Too many open files");
        
        List<FileInputStream> leakedStreams = new ArrayList<>();
        int fileCount = 0;
        
        try {
            while (true) {
                // Create a temporary file
                Path tempFile = Files.createTempFile("fd_leak_test_", ".tmp");
                
                // Write some data to it
                try (FileOutputStream out = new FileOutputStream(tempFile.toFile())) {
                    out.write(("Test data for file " + fileCount).getBytes());
                }
                
                // Open the file but intentionally DON'T close it (the leak)
                FileInputStream inputStream = new FileInputStream(tempFile.toFile());
                
                // Store reference to prevent GC from cleaning up
                leakedStreams.add(inputStream);
                
                fileCount++;
                
                if (fileCount % 100 == 0) {
                    System.out.printf("Opened %d files (leaked file descriptors)%n", fileCount);
                    
                    // Try to count open FDs on Linux
                    countOpenFileDescriptors();
                }
                
                if (fileCount % 500 == 0) {
                    // Force GC to show that FDs don't get cleaned up automatically
                    System.gc();
                    System.out.println("Forced GC - file descriptors should remain open");
                }
                
                // Small delay to make the leak observable
                Thread.sleep(10);
            }
            
        } catch (IOException e) {
            System.out.printf("IOException occurred after opening %d files: %s%n", fileCount, e.getMessage());
            
            if (e.getMessage().contains("Too many open files")) {
                System.out.println("✓ File descriptor limit reached!");
            } else {
                System.out.println("IO error (may be related to FD limit): " + e.getMessage());
            }
            
            System.out.printf("Total files opened without closing: %d%n", fileCount);
            countOpenFileDescriptors();
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Thread interrupted");
        } catch (Exception e) {
            System.out.println("Unexpected error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Clean up to be good citizens (though this is after the demo)
            System.out.println("Cleaning up leaked file descriptors...");
            for (FileInputStream stream : leakedStreams) {
                try {
                    stream.close();
                } catch (IOException e) {
                    // Ignore cleanup errors
                }
            }
        }
    }
    
    private void countOpenFileDescriptors() {
        try {
            // On Linux, we can count open FDs by listing /proc/self/fd
            Path fdDir = Paths.get("/proc/self/fd");
            if (Files.exists(fdDir)) {
                long fdCount = Files.list(fdDir).count();
                System.out.printf("Open file descriptors: %d%n", fdCount);
            } else {
                System.out.println("Cannot count FDs (not on Linux or /proc not available)");
            }
        } catch (Exception e) {
            System.out.println("Error counting FDs: " + e.getMessage());
        }
    }
}