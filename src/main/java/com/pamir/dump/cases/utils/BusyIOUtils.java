package com.pamir.dump.cases.utils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class BusyIOUtils {

    public Collection<Thread> doSomeIO() throws IOException {

        List<Thread> threadList = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            Thread t = new Thread(new Runnable() {
                public void run() {
                    WebPageLoader loader = new WebPageLoader();
                    while (true) {
                        try {
                            loader.load();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        ThreadUtils.sleepUninterruptedly(300);
                    }
                }
            });
            threadList.add(t);
            t.start();
        }
        return threadList;
    }

}