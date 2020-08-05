package com.pamir.cases;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import com.pamir.dump.cases.ClassloaderLeak;

import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.junit.Test;
import org.junit.runner.RunWith;

import se.jiderhamn.classloader.leak.Leaks;
import se.jiderhamn.classloader.leak.JUnitClassloaderRunner;

@RunWith(JUnitClassloaderRunner.class)
public class ClassloaderLeakTest {

   @Test
   @Leaks(haltBeforeError = true)
   public void testRun() throws ParserConfigurationException {
      System.out.println(ProcessHandle.current().pid());
      Document c = DocumentHelper.createDocument();
      

   }
}