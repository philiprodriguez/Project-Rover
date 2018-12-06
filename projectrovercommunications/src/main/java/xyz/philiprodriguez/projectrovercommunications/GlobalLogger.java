package xyz.philiprodriguez.projectrovercommunications;

import java.util.Date;
import java.util.HashSet;

public class GlobalLogger {
    private static final HashSet<String> allowedClasses = new HashSet<>();
    private static final HashSet<String> allowedModifiers = new HashSet<>();
    static {
    }

    public static void log(String classIdentifier, String modifier, String message) {
        if (allowedClasses.contains(classIdentifier) || allowedClasses.size() <= 0) {
            if (allowedModifiers.contains(modifier) || allowedModifiers.size() <= 0) {
                System.out.println(new Date() + ": " + message);
            }
        }
    }
}
