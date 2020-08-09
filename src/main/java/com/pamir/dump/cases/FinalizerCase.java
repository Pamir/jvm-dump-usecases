package com.pamir.dump.cases;

import com.pamir.dump.cases.domain.NonFinalized;
import com.pamir.dump.cases.utils.ThreadUtils;

public class FinalizerCase implements Case {

   @Override
   public void run() {
      while (true) {
         NonFinalized finalized = new NonFinalized();
         ThreadUtils.sleepUninterruptedly(50);
      }

   }

}