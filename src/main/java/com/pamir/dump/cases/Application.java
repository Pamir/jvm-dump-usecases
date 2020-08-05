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
            default:
                dumpAndExit();
        }
        problemCase.run();

    }

    private static void dumpAndExit() {
        System.err.println("Usage : application.jar <Problem>");
        System.err.println("Probems: InfiniteLoop | InfiniteWait | MemoryLeak | UnhandledException");
        System.err.println("Example Usage: application.jar InfiniteWait");
        System.exit(-1);
    }
}
