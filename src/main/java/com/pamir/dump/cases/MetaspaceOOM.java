package com.pamir.dump.cases;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

public class MetaspaceOOM implements Case {
    
    @Override
    public void run() {
        System.out.println("Starting Metaspace OOM case - generating classes until Metaspace is exhausted");
        System.out.println("Run with -XX:MaxMetaspaceSize=32m to trigger faster");
        
        List<Object> proxyInstances = new ArrayList<>();
        int classCount = 0;
        
        try {
            while (true) {
                // Create a new interface dynamically using Proxy
                // Each proxy class generated consumes Metaspace
                DynamicInterface proxy = (DynamicInterface) Proxy.newProxyInstance(
                    getClass().getClassLoader(),
                    new Class<?>[]{DynamicInterface.class},
                    new InvocationHandler() {
                        @Override
                        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                            return "Method " + method.getName() + " called";
                        }
                    }
                );
                
                proxyInstances.add(proxy);
                classCount++;
                
                if (classCount % 1000 == 0) {
                    System.out.println("Generated " + classCount + " proxy classes");
                    
                    // Try to get memory info
                    Runtime runtime = Runtime.getRuntime();
                    long heapUsed = runtime.totalMemory() - runtime.freeMemory();
                    System.out.println("Heap used: " + (heapUsed / 1024 / 1024) + "MB, Classes: " + classCount);
                }
                
                // Create additional classes by creating new anonymous classes
                createAnonymousClass(classCount);
            }
            
        } catch (OutOfMemoryError e) {
            System.out.println("OutOfMemoryError occurred: " + e.getMessage());
            System.out.println("Successfully triggered Metaspace exhaustion after generating " + classCount + " classes");
            
            // Print some info about what happened
            if (e.getMessage().contains("Metaspace")) {
                System.out.println("✓ Metaspace OOM confirmed!");
            } else {
                System.out.println("OOM occurred but may not be Metaspace: " + e.getMessage());
            }
        } catch (Exception e) {
            System.out.println("Unexpected error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    // Helper method to create additional classes
    private void createAnonymousClass(final int id) {
        // Create anonymous inner class
        Runnable anonymousClass = new Runnable() {
            private final int classId = id;
            
            @Override
            public void run() {
                // Empty implementation
            }
            
            public int getId() {
                return classId;
            }
        };
        
        // Use the class to prevent optimization
        anonymousClass.run();
    }
    
    // Simple interface for proxy creation
    public interface DynamicInterface {
        String doSomething();
    }
}