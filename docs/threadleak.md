### MemoryLeak
Our dockerized Java application  seems perfect. Dump the memory  what is going on, at the background


1. Run Test App
```bash
docker run -it --rm  pamir/jvm-cases ThreadLeak
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
	--net=container:8b0970e1aa99 \
	--pid=container:8b0970e1aa99 \
	-v /tmp:/tmp \
	--privileged \
	adriantodt/alpine-zlib-jattach \
	/bin/sh
```

3. Dump Heap with jattach
 
```bash
jattach 1 heapdump /tmp/threadleak1.hprof
jattacj 1 heapdump /tmp/threadleak2.hprof
```

4. Be careful the dump  files are not in in on jattach container. They are on java container's tmp directory
```bash
ls -lart /tmp
```

5. Copy Dump files from java container to host machine
```bash
docker cp 8b0970e1aa99:/tmp/memoryleak1.hprof /tmp/threadleak1.hprof
docker cp 8b0970e1aa99:/tmp/memoryleak2.hprof /tmp/threadleak2.hprof
```

6. Download Memory Dump Analzyer Tool</p>
http://www.eclipse.org/mat/


8. Open summary page. See eveything seems perfect</p>
![](img/thread-leak-summary.jpg)

10. Open histogram. See everything seems perfect </p>
![](img/thread-leak-histogram.jpg)

11. Open Threads View  </p>
![](img/thread-leak-threads.jpg)


### Todo
- drop capabilities
- Add Reference to Charlie Hunt Book Java Performance
- Refer to Source Code
- Convert this simple to hibernate/MYSQL scenrio