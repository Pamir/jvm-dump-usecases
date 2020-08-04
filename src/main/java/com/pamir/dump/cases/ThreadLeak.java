package com.pamir.dump.cases;

import java.util.ArrayList;
import java.util.List;

import com.pamir.dump.cases.utils.ThreadUtils;

public class ThreadLeak implements Case {

   private static List<Thread> THREAD_POOL = new ArrayList<>();

   @Override
   public void run() {
      Integer i = 0;
      while (true) {

         doSthAsnc(i++);
         ThreadUtils.sleepUninterruptedly(100);
      }
   }

   public void doSthAsnc(Integer i) {
      Thread t = new ImProperThread();
      t.setName("thread-" + i.toString());
      t.start();
   }

   class ImProperThread extends Thread {

      private volatile boolean running = true;

      @Override
      public void run() {
         // TODO Auto-generated method stub
         super.run();
         while (running) {
            ThreadUtils.sleepUninterruptedly(100);
         }
      }

      @Override
      public void interrupt() {
         // TODO Auto-generated method stub
         super.interrupt();
         this.running = false;
      }

   }

}