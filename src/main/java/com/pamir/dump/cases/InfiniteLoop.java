package com.pamir.dump.cases;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.print.attribute.SupportedValuesAttribute;

import com.pamir.dump.cases.utils.BusyIOUtils;
import com.pamir.dump.cases.utils.ThreadUtils;
import com.pamir.dump.cases.utils.WebPageLoader;

public class InfiniteLoop implements Case {

    private void runInternal() throws IOException, InterruptedException {
        ArrayList<Thread> threadList = Collections.list(Collections.enumeration((new BusyIOUtils()).doSomeIO()));
        Thread calculationThread = new Thread(new Runnable() {
            public void run() {
                int result = 0;
                while (true) {
                    result = 15 * 3;
                }
            }
        }, "calculation");
        calculationThread.start();
        threadList.add(calculationThread);
        for (Thread t : threadList) {
            t.join();
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