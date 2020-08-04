### MemoryLeak
Our dockerized Java application JVM Heap usage sometimes increases too much 
We can check it from JVM Prometheus Exporter. 


1. Run Test App
```bash
docker run -it --rm  pamir/jvm-cases SingleThreadHighMemoryUsage
```


2. Check, that our app is not idle(%CPU > 0.0):
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
jattach 1 heapdump /tmp/memoryleak1.hprof
jattacj 1 heapdump /tmp/memoryleak2.hprof
```

4. Be careful the dump  files are not in in on jattach container. They are on java container's tmp directory
```bash
ls -lart /tmp
```

5. Copy Dump files from java container to host machine
```bash
docker cp 8b0970e1aa99:/tmp/memoryleak1.hprof /tmp/memoryleak1.hprof
docker cp 8b0970e1aa99:/tmp/memoryleak2.hprof /tmp/memoryleak2.hprof
```

6. Download Memory Dump Analzyer Tool</p>
http://www.eclipse.org/mat/


8. Open Histogram and sort by retained heap size</p>
![](img/single-thread-high-mem-usage-hist.jpg)

10. Investigate suspected thread which pressures memory </p>
![](img/single-thread-high-mem-usage-1.jpg)

11. Find out what is consuming too much memory. In this type of scenerio UsernamePassword String array can make memory pressure. This pattern is Single Thread High Memory Usage. I have seen this too much when developers forget to add where clause. We can extract the query from the heap dump in  such cases   </p>
![](img/single-thread-high-mem-usage-2.jpg)


### Todo
- drop capabilities
- Add Reference to Charlie Hunt Book Java Performance
- Refer to Source Code
- Convert this simple to hibernate/MYSQL scenrio