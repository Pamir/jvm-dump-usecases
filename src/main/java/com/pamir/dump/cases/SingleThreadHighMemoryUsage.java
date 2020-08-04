package com.pamir.dump.cases;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.pamir.dump.cases.domain.UsernamePassword;
import com.pamir.dump.cases.utils.BusyIOUtils;
import com.pamir.dump.cases.utils.StringUtils;
import com.pamir.dump.cases.utils.WebPageLoader;

import org.apache.commons.io.FileUtils;

public class SingleThreadHighMemoryUsage implements Case {

   public void savePasswordList() {
      try {
         WebPageLoader loader = new WebPageLoader();
         String response = loader.load(
               "https://raw.githubusercontent.com/danielmiessler/SecLists/master/Passwords/Common-Credentials/10-million-password-list-top-1000000.txt");

         FileUtils.writeStringToFile(new File("passwordList"), response);
      } catch (IOException e) {
         throw new RuntimeException(e);
      }

   }

   public void read() throws IOException {
      List<UsernamePassword> usernamePasswordList = new ArrayList<UsernamePassword>();
      String data = FileUtils.readFileToString(new File("passwordList"));
      String[] lines = data.split(System.getProperty("line.separator"));
      for (String line : lines) {
         usernamePasswordList.add(new UsernamePassword(StringUtils.generateRandomUser(), line));
      }
   }

   public void load() {
      savePasswordList();
      while (true) {
         try {
            read();
         } catch (IOException e) {
            e.printStackTrace();
         }
      }
   }

   public void runInternal() throws IOException {
      ArrayList<Thread> threadList = Collections.list(Collections.enumeration((new BusyIOUtils()).doSomeIO()));
      Thread t = new Thread(new Runnable() {
         public void run() {
            load();
         }
      }, "highmem-thread-1");
      t.start();
      threadList.add(t);
      for (Thread t1 : threadList) {
         try {
            t1.join();
         } catch (InterruptedException ie) {
            System.out.println(ie);
         }

      }

   }

   public void run() {
      try {
         runInternal();
      } catch (IOException e) {
         // TODO Auto-generated catch block
         e.printStackTrace();
      }
   }

}