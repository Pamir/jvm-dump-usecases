package com.pamir.dump.cases;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.pamir.dump.cases.domain.Counter;
import com.pamir.dump.cases.domain.ICounter;

public class ClassloaderLeak implements Case {

   public static ICounter newInstance() {
      try {
         return newInstanceWithThrows();
      } catch (InstantiationException | IllegalAccessException | ClassNotFoundException | MalformedURLException e) {
         e.printStackTrace();
         throw new RuntimeException(e.getCause());
      }
   }

   private static ICounter newInstanceWithThrows()
         throws InstantiationException, IllegalAccessException, ClassNotFoundException, MalformedURLException {
      URLClassLoader tmp = new URLClassLoader(getClassPath()) {
         public Class<?> loadClass(String name) throws ClassNotFoundException {
            if ("com.pamir.dump.cases.domain.Counter".equals(name) || "com.pamir.dump.cases.domain.Leak".equals(name))
               return findClass(name);
            return super.loadClass(name);
         }
      };
      return (ICounter) tmp.loadClass("com.pamir.dump.cases.domain.Counter").newInstance();
   }

   private static URL[] getClassPath() throws MalformedURLException {
      // String classPath = System.getProperty("java.class.path");
      String classPath = "C:/dev/projects/opensource/jvm-dump-usecases/target/application.jar";
      String[] classPaths = classPath.split(";");
      List<URL> urlList = new ArrayList<>();
      for (String clsPath : classPaths) {
         urlList.add(new URL(String.format("file:%s", clsPath.replace("\\", "//"))));
      }
      URL[] urls = new URL[classPaths.length];
      return urlList.toArray(urls);

   }

   public void run() {
      ICounter root = new Counter();
      ICounter example1 = ClassloaderLeak.newInstance().copy(root);

      while (true) {
         ICounter example2 = ClassloaderLeak.newInstance().copy(example1);

         System.out.println("1) " + example1.message() + " = " + example1.plusPlus());
         System.out.println("2) " + example2.message() + " = " + example2.plusPlus());
         System.out.println();
      }

   }
}