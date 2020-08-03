package com.pamir.dump.cases;

public class InfiniteWait implements Case {

    private void runInternal() throws InterruptedException {
        Object waitObj = new Object();
        synchronized (waitObj) {
            Thread t = new Thread(new Runnable() {
                public void run() {
                    System.out.println("You will wait too long");
                    synchronized (waitObj) {
                        System.out.println("Reach  me if you can");
                    }
                }
            });
            t.start();
            t.join();
        }
    }

    public void run() {
        try {
            runInternal();
        } catch (InterruptedException e) {
            System.out.println(e);
        }
    }

}