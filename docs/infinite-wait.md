### Low CPU Hang
Our dockerized Java application hangs and we don't know why. We know, that process is idle(CPU usage near 0%).

1. Run Test App
```bash
docker run -it --rm  pamir/jvm-cases InfiniteWait
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

5.  Copy the main thread to a txt file.
```
"main" #1 prio=5 os_prio=0 tid=0x00007f292000a800 nid=0x6 in Object.wait() [0x00007f292a12b000]
   java.lang.Thread.State: WAITING (on object monitor)
        at java.lang.Object.wait(Native Method)
        - waiting on <0x00000000d697fe88> (a java.lang.Thread)
        at java.lang.Thread.join(Thread.java:1252)
        - locked <0x00000000d697fe88> (a java.lang.Thread)
        at java.lang.Thread.join(Thread.java:1326)
        at com.pamir.dump.cases.InfiniteWait.runInternal(InfiniteWait.java:17)
        - locked <0x00000000d697fcf8> (a java.lang.Object)
        at com.pamir.dump.cases.InfiniteWait.run(InfiniteWait.java:23)
        at com.pamir.dump.cases.Application.main(Application.java:24)
```

6. Copy te the Blocked Thread to a txt file and investigate the problem. As it is seen from the output, InfiniteWait.java at line 12 it is waiting an object to be released. 

```
"Thread-0" #8 prio=5 os_prio=0 tid=0x00007f2920209800 nid=0x11 waiting for monitor entry [0x00007f290ca9f000]
   java.lang.Thread.State: BLOCKED (on object monitor)
        at com.pamir.dump.cases.InfiniteWait$1.run(InfiniteWait.java:12)
        - waiting to lock <0x00000000d697fcf8> (a java.lang.Object)
        at java.lang.Thread.run(Thread.java:748)
```

7. Another way
```bash
jattach 1 threaddump >> /tmp/threads.dmp
jattach 1 threaddump >> /tmp/threads.dmp
jattach 1 threaddump >> /tmp/threads.dmp
```
8. Download Thread Dump Analyzer from https://mkbrv.github.io/tda/
9. Open /tmp/threads.dmp with thread dump analyzer
![](img/tda-1.jpg)

10. Find the blocking thread
![](img/tda-2.jpg)

### TODO
- Drop unnecessary capabilities. Use --add-cap instead of --privieged
