### Log4J 1 Case.
It is always Logs or log frameworks

Our dockerized Java application does not hang like (Low CPU Hang)[infinite-wait.md] but it is too slow. We don't know why. We know, that process is some how idle but (CPU usage is not  0%). This was happened too much during development of a financial application. Still we are facing these kinds of issues under load.

1. Run Test App
```bash
docker run -it --rm  pamir/jvm-cases Log4j
```

2. Check, that our app is idle(%CPU=~0.0):
```bash
top -c -p $(pgrep -d',' -f java)
```

#### Steps To Analyze

1. Get id of container with our application(java application.jar ...):

```bash
docker ps
```

2. Run container with jattach utility:
```bash
docker run --rm -it \
	--net=container:741d07985f66 \
	--pid=container:741d07985f66 \
	-v /tmp:/tmp \
        --privileged \
	adriantodt/alpine-zlib-jattach \
	/bin/sh
```

where 741d07985f66 is id of container with our application.

Find PID of java process we need to analyze(java application.jar ...):
```bash
ps aux
```

In this example PID is "1"

4. Dump all call stack of java process  with using kill -3. Process Id 1 will print all the call stack on the default output. We can copy this output to another txt file or we can find the problematic thread and source code by searching on the default output.
```bash
kill -3 1
sleep 3

kill -3 1
sleep 3
kill -3 1
```

5.  Copy the all 30 threads to a txt file.
```
Name: waiting-log4j-t2
State: BLOCKED on org.apache.log4j.spi.RootLogger@4fb652cc owned by: waiting-log4j-t9
Total blocked: 4,338  Total waited: 0

Stack trace: 
app//org.apache.log4j.Category.callAppenders(Category.java:204)
app//org.apache.log4j.Category.forcedLog(Category.java:391)
app//org.apache.log4j.Category.debug(Category.java:260)
app//com.pamir.dump.cases.Log4JCase$1.run(Log4JCase.java:20)
java.base@11.0.5/java.lang.Thread.run(Thread.java:834)


Name: waiting-log4j-t6
State: BLOCKED on org.apache.log4j.spi.RootLogger@4fb652cc owned by: waiting-log4j-t19
Total blocked: 23,579  Total waited: 0

Stack trace: 
app//org.apache.log4j.Category.callAppenders(Category.java:204)
app//org.apache.log4j.Category.forcedLog(Category.java:391)
app//org.apache.log4j.Category.debug(Category.java:260)
app//com.pamir.dump.cases.Log4JCase$1.run(Log4JCase.java:20)
java.base@11.0.5/java.lang.Thread.run(Thread.java:834)

Name: waiting-log4j-t13
State: BLOCKED on org.apache.log4j.spi.RootLogger@4fb652cc owned by: waiting-log4j-t16
Total blocked: 26,436  Total waited: 0

Stack trace: 
app//org.apache.log4j.Category.callAppenders(Category.java:204)
app//org.apache.log4j.Category.forcedLog(Category.java:391)
app//org.apache.log4j.Category.debug(Category.java:260)
app//com.pamir.dump.cases.Log4JCase$1.run(Log4JCase.java:20)
java.base@11.0.5/java.lang.Thread.run(Thread.java:834)

```

6. See  Log4J Source Code  
http://svn.apache.org/viewvc/logging/log4j/trunk/src/main/java/org/apache/log4j/Category.java?revision=1567107&view=markup
```java
 /**
190	     Call the appenders in the hierrachy starting at
191	     <code>this</code>.  If no appenders could be found, emit a
192	     warning.
193	
194	     <p>This method calls all the appenders inherited from the
195	     hierarchy circumventing any evaluation of whether to log or not
196	     to log the particular log request.
197	
198	  @param event the event to log.  */
199	  public
200	  void callAppenders(LoggingEvent event) {
201	    int writes = 0;
202	
203	    for(Category c = this; c != null; c=c.parent) {
204	      // Protected against simultaneous call to addAppender, removeAppender,...
205	      synchronized(c) {
206	        if(c.aai != null) {
207	          writes += c.aai.appendLoopOnAppenders(event);
208	        }
209	        if(!c.additive) {
210	          break;
211	        }
212	      }
213	    }
```

7. Another way
```bash
jattach 1 threaddump >> /tmp/threads.dmp
jattach 1 threaddump >> /tmp/threads.dmp
jattach 1 threaddump >> /tmp/threads.dmp
```
8. Download (IBM Thread Dump Analyzer)[https://www.ibm.com/support/pages/ibm-thread-and-monitor-dump-analyzer-java-tmda] 
9. Open /tmp/threads.dmp with thread dump analyzer

10. Find the blocking thread. Finding the root casuse will lead you to upgrade log4j to log4j2 or another logging framework.

### TODO
- Drop unnecessary capabilities. Use --add-cap instead of --privieged
- Add IBM Thread Analayzer Tool screenshots
