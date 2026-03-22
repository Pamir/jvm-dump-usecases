# JVM Dump Use Cases

> Hands-on examples of common JVM issues diagnosed through heap dumps, thread dumps, and core dumps.  
> Each case includes a reproducible Java application, Kubernetes deployment, and step-by-step analysis guide with screenshots.

*Inspired by [lldb-netcore-use-cases](https://github.com/6opuc/lldb-netcore-use-cases/)*

---

## 🚀 Quick Start

```bash
# Build the project
mvn clean package -DskipTests

# Run a specific case (e.g., memory leak)
docker build -t jvm-dump-cases .
docker run -it jvm-dump-cases memoryleak

# Or deploy on Kubernetes
kubectl apply -f k8s/01-infinitewait.yaml
```

---

## ✅ Implemented Cases

| # | Case | CPU | Memory | Diagnosis Tool | Doc |
|---|------|:---:|:------:|---------------|-----|
| 1 | **Low CPU Hang** (Infinite Wait) | 🟢 Low | 🟢 Normal | Thread Dump | [📖](docs/infinite-wait.md) |
| 2 | **High CPU Hang** (Infinite Loop) | 🔴 High | 🟢 Normal | Thread Dump + `top -H` | [📖](docs/infinite-loop.md) |
| 3 | **Heap Memory Leak** | 🟢 Normal | 🔴 Growing | Heap Dump + MAT | [📖](docs/memoryleak.md) |
| 4 | **Single Thread High Memory** | 🟢 Normal | 🔴 Spike | Heap Dump + MAT | [📖](docs/singlethreadhighmemusage.md) |
| 5 | **Unhandled Exception / Crash** | — | — | `-XX:+CrashOnOutOfMemoryError` | [📖](docs/crashonerror.md) |
| 6 | **Classloader Leak** | 🟢 Normal | 🔴 Metaspace | Heap Dump (class histogram) | [📖](docs/classloaderleak.md) |
| 7 | **Thread Leak** | 🟡 Medium | 🔴 Growing | Thread Dump + `jstack` | [📖](docs/threadleak.md) |
| 8 | **Log4j Appender Blocking** | 🟢 Normal | 🟢 Normal | Thread Dump (BLOCKED) | [📖](docs/log4j.md) |
| 9 | **Finalizer Queue Leak** | 🟢 Normal | 🔴 Growing | Heap Dump (Finalizer) | [📖](docs/finalizerleak.md) |
| 10 | Unmanaged (Native) Memory Leak | 🟢 Normal | 🔴 RSS growing | NMT + `pmap` | — |
| 11 | **Deadlock** | 🟢 Low | 🟢 Normal | Thread Dump (`jstack`) | [📖](docs/deadlock.md) |
| 12 | **GC Thrashing** | 🔴 High (GC) | 🔴 Full | GC Logs + `jstat` | [📖](docs/gc-thrashing.md) |
| 13 | **Metaspace OOM** | 🟢 Normal | 🔴 Metaspace | NMT + class histogram | [📖](docs/metaspace-oom.md) |
| 14 | **Off-Heap Leak** | 🟢 Normal | 🔴 RSS growing | NMT + `pmap` | [📖](docs/offheap-leak.md) |
| 15 | **Connection Pool Exhaustion** | 🟢 Normal | 🟢 Normal | Thread Dump (WAITING) | [📖](docs/connection-pool-exhaustion.md) |
| 16 | **Thread Pool Saturation** | 🟡 Medium | 🟢 Normal | Thread Dump + metrics | [📖](docs/threadpool-saturation.md) |
| 17 | **Stack Overflow** | 🟢 Normal | 🟢 Normal | Stack trace analysis | [📖](docs/stackoverflow.md) |
| 18 | **File Descriptor Leak** | 🟢 Normal | 🟢 Normal | `lsof` + `/proc/fd` | [📖](docs/filedescriptor-leak.md) |

---

## 📋 TODO — New Cases to Add

### 🔴 High Priority

- [x] **Deadlock Detection** — Two threads holding locks and waiting for each other. Diagnose with `jstack` (shows "Found one Java-level deadlock"). Classic producer-consumer deadlock scenario.

- [x] **GC Thrashing / GC Overhead Limit** — Application spending >98% time in GC with <2% heap recovered. Diagnose with GC logs (`-Xlog:gc*`), GCViewer. Trigger `java.lang.OutOfMemoryError: GC overhead limit exceeded`.

- [x] **Metaspace / PermGen OOM** — Metaspace exhaustion from dynamic class generation (Groovy scripts, CGLIB proxies, excessive reflection). Diagnose with `-XX:+HeapDumpOnOutOfMemoryError` and class histogram.

- [x] **Direct ByteBuffer / Off-Heap Leak** — Native memory leak via `ByteBuffer.allocateDirect()` or NIO channels. RSS grows but heap looks fine. Diagnose with NMT (`-XX:NativeMemoryTracking=detail`) and `jcmd VM.native_memory`.

- [x] **Connection Pool Exhaustion** — Database connection pool (HikariCP/C3P0) fully consumed. Threads stuck waiting for connection. Diagnose with thread dump (waiting on pool) + pool metrics.

### 🟡 Medium Priority

- [x] **Thread Pool Saturation** — `ThreadPoolExecutor` with bounded queue full. Tasks rejected with `RejectedExecutionException`. Diagnose with thread dump (all pool threads RUNNABLE) + JMX metrics.

- [ ] **Excessive Object Creation (Allocation Pressure)** — High allocation rate causing frequent young GC. Short-lived objects dominating Eden space. Diagnose with allocation profiling (JFR/async-profiler).

- [x] **Stack Overflow** — Deep recursion causing `StackOverflowError`. Diagnose with `-Xss` tuning, thread dump shows deep call stack.

- [ ] **String/StringBuilder Abuse** — Massive String concatenation in loops creating GC pressure. Compare `String +=` vs `StringBuilder` vs `String.join()`. Diagnose with allocation profiler.

- [ ] **Zombie / Orphan Threads** — Threads created but never properly shut down (missing `ExecutorService.shutdown()`). Thread count grows indefinitely. Diagnose with `jstack` thread count over time.

- [ ] **Class Data Sharing (CDS) Issues** — AppCDS misconfiguration causing slow startup or class loading failures. Diagnose with `-Xlog:class+load`.

### 🟢 Advanced / Production Scenarios

- [ ] **JIT Compilation Issues** — Code deoptimization causing performance cliffs. C2 compiler bailouts. Diagnose with `-XX:+PrintCompilation` and JFR.

- [ ] **Safepoint Stalls** — Long time-to-safepoint causing latency spikes. Diagnose with `-XX:+PrintSafepointStatistics` and JFR safepoint events.

- [ ] **TLAB Resizing / Allocation Contention** — Multi-threaded allocation contention outside TLABs. Diagnose with `-XX:+PrintTLAB` and JFR.

- [x] **File Descriptor Leak** — `java.io.IOException: Too many open files`. Streams/connections opened but never closed. Diagnose with `lsof -p <pid>` and `/proc/<pid>/fd`.

- [ ] **DNS Resolution Hang** — `InetAddress.getByName()` blocking under load. JVM DNS caching issues (`networkaddress.cache.ttl`). Thread dump shows threads stuck in DNS resolution.

- [ ] **SSL/TLS Handshake Issues** — Slow or failing TLS handshakes. Certificate validation problems, cipher negotiation. Diagnose with `-Djavax.net.debug=ssl:handshake`.

- [ ] **Container Memory Limits** — JVM not respecting container memory limits (old JVMs). `-XX:+UseContainerSupport` vs manual `-Xmx`. OOMKilled by Kubernetes vs JVM OOM.

- [ ] **Large Object Allocation in Old Gen** — Objects too large for young gen allocated directly in old gen, causing premature full GC. Diagnose with `-XX:PretenureSizeThreshold` and GC logs.

---

## 🛠️ Diagnosis Tools Reference

| Tool | What It Captures | Command |
|------|-----------------|---------|
| **jstack** | Thread dump | `jstack <pid>` |
| **jmap** | Heap dump | `jmap -dump:format=b,file=heap.hprof <pid>` |
| **jcmd** | All-in-one diagnostic | `jcmd <pid> GC.heap_dump heap.hprof` |
| **jstat** | GC statistics | `jstat -gcutil <pid> 1000` |
| **jinfo** | JVM flags | `jinfo -flags <pid>` |
| **jfr** | Flight Recorder | `jcmd <pid> JFR.start duration=60s filename=rec.jfr` |
| **MAT** | Heap analysis (GUI) | Eclipse Memory Analyzer |
| **TDA** | Thread dump analysis | Thread Dump Analyzer |
| **async-profiler** | CPU/allocation profiling | `./profiler.sh -d 30 -f profile.html <pid>` |
| **NMT** | Native memory tracking | `-XX:NativeMemoryTracking=detail` + `jcmd VM.native_memory` |
| **GCViewer** | GC log analysis | Parse `-Xlog:gc*:file=gc.log` |
| **VisualVM** | Live monitoring | Connect via JMX |

### Useful JVM Flags for Debugging

```bash
# Heap dump on OOM (essential for production)
-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp/heapdump.hprof

# GC logging (JDK 11+)
-Xlog:gc*:file=gc.log:time,uptime,level,tags:filecount=5,filesize=100m

# Native Memory Tracking
-XX:NativeMemoryTracking=detail

# Flight Recorder (always-on in production)
-XX:StartFlightRecording=dumponexit=true,filename=recording.jfr,maxage=1h

# Crash on OOM (let Kubernetes restart the pod)
-XX:+CrashOnOutOfMemoryError

# Container-aware memory settings
-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0
```

---

## 📁 Project Structure

```
jvm-dump-usecases/
├── src/main/java/com/pamir/dump/cases/
│   ├── Application.java          # Main entry point (selects case by arg)
│   ├── Case.java                 # Base interface
│   ├── InfiniteWait.java         # Low CPU hang
│   ├── InfiniteLoop.java         # High CPU hang
│   ├── MemoryLeak.java           # Heap memory leak
│   ├── SingleThreadHighMemoryUsage.java
│   ├── CrashOnError.java         # Unhandled exception
│   ├── ClassloaderLeak.java      # Metaspace leak
│   ├── ThreadLeak.java           # Thread count growing
│   ├── Log4JCase.java            # Log4j blocking
│   ├── FinalizerCase.java        # Finalizer queue leak
│   └── UnhandledException.java
├── docs/                         # Step-by-step analysis guides with screenshots
├── k8s/                          # Kubernetes deployment manifests
├── Dockerfile                    # Container image
├── Dockerfile_oom                # OOM-specific image
└── pom.xml                       # Maven build
```

---

## 📚 References

- [eBay SRE: Triage a Non-Heap JVM OOM Issue](https://tech.ebayinc.com/engineering/sre-case-study-triage-a-non-heap-jvm-out-of-memory-issue/)
- [lldb-netcore-use-cases](https://github.com/6opuc/lldb-netcore-use-cases/) (inspiration for .NET equivalent)
- [Java Performance: In-Depth Advice](https://www.oreilly.com/library/view/java-performance-2nd/9781492056102/) — Scott Oaks
- [JVM Troubleshooting Guide](https://docs.oracle.com/en/java/javase/17/troubleshoot/)
- [Eclipse MAT Documentation](https://help.eclipse.org/latest/index.jsp?topic=/org.eclipse.mat.ui.help/welcome.html)
- [async-profiler](https://github.com/async-profiler/async-profiler)

---

## 📄 License

MIT
