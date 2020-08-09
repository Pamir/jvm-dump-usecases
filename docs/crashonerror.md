It is weird to see java does not have memory dump on crash options. Instead of that we can build and deploy our JVMTI agent. Our JVMTI agent generates heap dump, in case of JVM crash.

```c
#include <jvmti.h>
#include <string.h>
#include <stdio.h>
#include "jmm.h"

JNIEXPORT void* JNICALL JVM_GetManagement(jint version);

void JNICALL VMDeath(jvmtiEnv* jvmti, JNIEnv* jni) {
    JmmInterface* jmm = (JmmInterface*) JVM_GetManagement(JMM_VERSION_1_0);
    if (jmm == NULL) {
        printf("Sorry, JMM is not supported\n");
    } else {
        jstring path = (*jni)->NewStringUTF(jni, "dump.hprof");
        jmm->DumpHeap0(jni, path, JNI_TRUE);
        printf("Heap dumped\n");
    }
}

JNIEXPORT jint JNICALL Agent_OnLoad(JavaVM* vm, char* options, void* reserved) {
    jvmtiEnv* jvmti;
    (*vm)->GetEnv(vm, (void**)&jvmti, JVMTI_VERSION_1_0);

    jvmtiEventCallbacks callbacks;
    memset(&callbacks, 0, sizeof(callbacks));
    callbacks.VMDeath = VMDeath;
    (*jvmti)->SetEventCallbacks(jvmti, &callbacks, sizeof(callbacks));
    (*jvmti)->SetEventNotificationMode(jvmti, JVMTI_ENABLE, JVMTI_EVENT_VM_DEATH, NULL);

    return 0;
}
```

This command build and generates jvmti agent. It is for only demostration purpose. It is in the container.
```bash
gcc -shared -fPIC -DPIC memory-dump-agent.c -I /usr/lib/jvm/java-8-openjdk-amd64/include/  -I /usr/lib/jvm/java-8-openjdk-amd64/include/linux/ -o memory-dump-agent.so
```

```bash
docker run -it --rm  -e JVM_ARGS="-agentpath:$(pwd)/memory-dump-agent.so" pamir/jvm-cases CrashOnError
#Container crash
docker ps -a
docker cp 02d4fc404f1e:/my-fault/dump.hprof dump.hprof
```

It is very hard to find root cause of this kind of production. In Log4J and BufferedWriter objects we can find the root cause of the problem





```



