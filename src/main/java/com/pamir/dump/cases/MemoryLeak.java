package com.pamir.dump.cases;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.pamir.dump.cases.utils.BusyIOUtils;
import com.pamir.dump.cases.utils.ThreadUtils;

public class MemoryLeak implements Case {

    private void runInternal() throws IOException, InterruptedException {
        List<byte[]> leakyCache = new ArrayList<>();
        ArrayList<Thread> threadList = Collections.list(Collections.enumeration((new BusyIOUtils()).doSomeIO()));
        Thread t = new Thread(new Runnable() {
            public void run() {
                while (true) {
                    leakyCache.add(new byte[512 * 1024]);
                    ThreadUtils.sleepUninterruptedly(1000);
                }

            }
        }, "leaky");
        t.start();
        t.join();

        // Just for GC handling
        for (Thread t1 : threadList) {
            t1.join();
        }
    }

    public void run() {
        try {
            runInternal();
        } catch (IOException | InterruptedException e) {

            System.out.println(e);
        }
    }

}