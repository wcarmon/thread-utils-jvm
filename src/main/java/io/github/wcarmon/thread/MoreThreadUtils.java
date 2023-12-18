package io.github.wcarmon.thread;

import com.google.common.primitives.Ints;
import java.io.PrintStream;
import java.util.Objects;

/** Utility methods for dealing with threads */
public final class MoreThreadUtils {

    private MoreThreadUtils() {}

    /** Pretty prints call info to passed sink */
    public static void printRecentCallerInfo() {
        printRecentCallerInfo(System.out, 2);
    }

    /**
     * Pretty prints call info to passed sink
     *
     * @param sink where to write stack trace
     */
    public static void printRecentCallerInfo(PrintStream sink) {
        printRecentCallerInfo(sink, 2);
    }

    /**
     * Pretty prints call info to passed sink
     *
     * @param sink where to write stack trace
     * @param maxInterestingCallers limit stack trace
     */
    public static void printRecentCallerInfo(PrintStream sink, int maxInterestingCallers) {
        Objects.requireNonNull(sink, "sink is required and null.");

        final var stackTrace = Thread.currentThread().getStackTrace();
        if (stackTrace.length < 4) {
            System.err.println("Failed to determine caller information.");
            return;
        }

        // -- stackTrace[0] is java.base/java.lang.Thread.getStackTrace(Thread.java)
        // -- stackTrace[1] is
        // com.wcarmon.exec.MoreThreadUtils.printRecentCallerInfo(MoreThreadUtils.java)
        // -- stackTrace[2] is the method containing call to printRecentCallerInfo (already obvious
        // to user)

        final var start = stackTrace[2];
        final var maxCallers =
                Ints.constrainToRange(stackTrace.length, 4, 3 + maxInterestingCallers);

        StringBuilder sb = new StringBuilder();
        sb.append("StackTrace for method=")
                .append(simplifyClassName(start.getClassName()))
                .append("::")
                .append(simplifyMethodName(start.getMethodName()))
                .append(" line-")
                .append(start.getLineNumber());

        for (int i = 3; i < maxCallers; i++) {
            var caller = stackTrace[i];
            sb.append("\n\t-> ")
                    .append(simplifyClassName(caller.getClassName()))
                    .append("::")
                    .append(simplifyMethodName(caller.getMethodName()))
                    .append(" line-")
                    .append(caller.getLineNumber());
        }

        sink.println(sb);
    }

    private static String simplifyClassName(String className) {
        if (className == null || className.isBlank()) {
            throw new IllegalArgumentException("className is required");
        }

        final int idx = className.lastIndexOf(".");
        if (idx == -1 || idx == className.length() - 1) {
            return "";
        }

        return className.substring(idx + 1);
    }

    private static String simplifyMethodName(String methodName) {
        if (methodName == null || methodName.isBlank()) {
            throw new IllegalArgumentException("methodName is required");
        }

        if ("<init>".equals(methodName)) {
            return "<constructor>";
        }

        return methodName;
    }
}
