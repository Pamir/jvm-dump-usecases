package com.pamir.dump.cases.utils;

import java.nio.charset.Charset;
import java.util.Random;

public class StringUtils {

   public static String generateRandomUser() {
      byte[] array = new byte[7]; // length is bounded by 7
      new Random().nextBytes(array);
      String generatedString = new String(array, Charset.forName("UTF-8"));
      return generatedString;
   }

}