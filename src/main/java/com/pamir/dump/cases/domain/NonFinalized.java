package com.pamir.dump.cases.domain;

import com.pamir.dump.cases.utils.ThreadUtils;

public class NonFinalized {

   private byte[] simpleByte;

   public NonFinalized() {
      this.simpleByte = new byte[1024 * 10 * 10];
   }

   @Override
   protected void finalize() {
      System.out.println("finalize method called");
      ThreadUtils.sleepUninterruptedly(100000);
      this.simpleByte = null;
   }

}