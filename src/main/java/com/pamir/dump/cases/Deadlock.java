package com.pamir.dump.cases;

public class Deadlock implements Case {
    private static final Object lock1 = new Object();
    private static final Object lock2 = new Object();

    @Override
    public void run() {
        System.out.println("Starting deadlock case - this will create a deadlock between two threads");
        
        Thread thread1 = new Thread(() -> {
            synchronized (lock1) {
                System.out.println("Thread 1: Acquired lock1");
                try {
                    Thread.sleep(100); // Give time for thread2 to acquire lock2
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                System.out.println("Thread 1: Trying to acquire lock2");
                synchronized (lock2) {
                    System.out.println("Thread 1: Acquired lock2");
                }
            }
        }, "DeadlockThread1");

        Thread thread2 = new Thread(() -> {
            synchronized (lock2) {
                System.out.println("Thread 2: Acquired lock2");
                try {
                    Thread.sleep(100); // Give time for thread1 to acquire lock1
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                System.out.println("Thread 2: Trying to acquire lock1");
                synchronized (lock1) {
                    System.out.println("Thread 2: Acquired lock1");
                }
            }
        }, "DeadlockThread2");

        thread1.start();
        thread2.start();

        try {
            thread1.join();
            thread2.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}