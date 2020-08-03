package com.pamir.dump.cases.utils;

public class ThreadUtils {

    public static void sleepUninterruptedly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {

        }
    }
}