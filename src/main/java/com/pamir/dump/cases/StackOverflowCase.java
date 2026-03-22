package com.pamir.dump.cases;

public class StackOverflowCase implements Case {
    private int recursionDepth = 0;
    
    @Override
    public void run() {
        System.out.println("Starting Stack Overflow case - infinite recursion");
        System.out.println("Adjust stack size with -Xss flag (e.g., -Xss128k for smaller stack)");
        
        try {
            recursiveMethod();
        } catch (StackOverflowError e) {
            System.out.printf("✓ StackOverflowError caught at recursion depth: %d%n", recursionDepth);
            System.out.println("Stack trace (first 10 frames):");
            
            StackTraceElement[] stackTrace = e.getStackTrace();
            int framesToShow = Math.min(10, stackTrace.length);
            
            for (int i = 0; i < framesToShow; i++) {
                System.out.printf("  %d: %s%n", i, stackTrace[i]);
            }
            
            if (stackTrace.length > framesToShow) {
                System.out.printf("  ... and %d more frames%n", stackTrace.length - framesToShow);
            }
            
            System.out.println("\nStack overflow successfully demonstrated!");
            System.out.println("Each recursive call adds a new frame to the call stack");
            System.out.println("When the stack limit is reached, StackOverflowError is thrown");
        }
    }
    
    private void recursiveMethod() {
        recursionDepth++;
        
        // Print progress every 1000 calls
        if (recursionDepth % 1000 == 0) {
            System.out.printf("Recursion depth: %d%n", recursionDepth);
        }
        
        // Add some local variables to consume more stack space per frame
        int localVar1 = recursionDepth;
        double localVar2 = Math.sin(recursionDepth);
        String localVar3 = "Frame-" + recursionDepth;
        
        // Prevent optimization by using the variables
        if (localVar1 < 0 || localVar2 > 2.0 || localVar3.isEmpty()) {
            System.out.println("This should never print");
        }
        
        // Recursive call - this will eventually overflow the stack
        recursiveMethod();
    }
}