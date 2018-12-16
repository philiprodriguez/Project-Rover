package xyz.philiprodriguez.projectrovercommunications;

import java.util.HashSet;
import java.util.concurrent.atomic.AtomicInteger;

public class GlobalLogger {
    public static final int EXCESS = 0;
    public static final int INFO = 1000;
    public static final int WARNING = 2000;
    public static final int ERROR = 3000;
    public static final int FATAL = 4000;

    private static final AtomicInteger printCounter = new AtomicInteger(0);

    private static final HashSet<String> allowedClasses = new HashSet<>();
    private static final int MIN_LOG_LEVEL = EXCESS;
    static {
    }



    public static void log(String classIdentifier, int level, String message) {
        int messageNum = printCounter.getAndIncrement();
        if (level < MIN_LOG_LEVEL) {
            return;
        }
        if (allowedClasses.contains(classIdentifier) || allowedClasses.size() <= 0) {
            System.out.println("[" + level + "] (" + messageNum + "@" + System.currentTimeMillis() + ") " + message);
        }
    }
}
