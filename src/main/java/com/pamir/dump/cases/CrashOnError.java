package com.pamir.dump.cases;

public class CrashOnError implements Case {

   public void divide(int a, int b) {
      int reuslt = a / b;
      System.out.println(reuslt);
   }

   public void run() {
      divide(3, 0);
   }

}