package com.pamir.dump.cases;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;

public class Log4JCase implements Case {

   /* Get actual class name to be printed on */
   static Logger log = Logger.getLogger(Log4JCase.class.getName());

   public void run() {
      List<Thread> threadList = new ArrayList<>();
      for (int i = 0; i < 30; ++i) {
         Thread t = new Thread(new Runnable() {
            public void run() {
               while (true)
                  log.debug("I think i am going to wait for sometime.");
            }
         }, "waiting-log4j-t" + String.valueOf(i));
         t.start();
         threadList.add(t);
      }
      for (Thread t : threadList) {
         try {
            t.join();
         } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
         }
      }

   }

}