package com.pamir.dump.cases;

public class Application {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            dumpAndExit();
        }

        String problem = args[0];
        Case problemCase = null;
        switch (problem) {
            case "InfiniteLoop":
                problemCase = new InfiniteLoop();
                break;
            case "InfiniteWait":
                problemCase = new InfiniteWait();
                break;
            case "MemoryLeak":
                problemCase = new MemoryLeak();
                break;
            case "SingleThreadHighMemoryUsage":
                problemCase = new SingleThreadHighMemoryUsage();
                break;
            case "ThreadLeak":
                problemCase = new ThreadLeak();
                break;
            case "ClassloaderLeak":
                problemCase = new ClassloaderLeak();
                break;
            case "Log4j":
                problemCase = new Log4JCase();
                break;
            case "CrashOnError":
                problemCase = new CrashOnError();
                break;
            case "NonFinalized":
                problemCase = new FinalizerCase();
                break;
            case "Deadlock":
                problemCase = new Deadlock();
                break;
            case "GCThrashing":
                problemCase = new GCThrashing();
                break;
            case "MetaspaceOOM":
                problemCase = new MetaspaceOOM();
                break;
            case "OffHeapLeak":
                problemCase = new OffHeapLeak();
                break;
            case "ConnectionPoolExhaustion":
                problemCase = new ConnectionPoolExhaustion();
                break;
            case "ThreadPoolSaturation":
                problemCase = new ThreadPoolSaturation();
                break;
            case "StackOverflow":
                problemCase = new StackOverflowCase();
                break;
            case "FileDescriptorLeak":
                problemCase = new FileDescriptorLeak();
                break;
            default:
                dumpAndExit();
        }
        problemCase.run();

    }

    private static void dumpAndExit() {
        System.err.println("Usage : application.jar <Problem>");
        System.err.println("Available Problems:");
        System.err.println("  InfiniteLoop | InfiniteWait | MemoryLeak | SingleThreadHighMemoryUsage");
        System.err.println("  ThreadLeak | ClassloaderLeak | Log4j | CrashOnError | NonFinalized");
        System.err.println("  Deadlock | GCThrashing | MetaspaceOOM | OffHeapLeak");
        System.err.println("  ConnectionPoolExhaustion | ThreadPoolSaturation | StackOverflow | FileDescriptorLeak");
        System.err.println("Example Usage: application.jar Deadlock");
        System.exit(-1);
    }
}
