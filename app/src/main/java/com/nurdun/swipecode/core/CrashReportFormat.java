package com.nurdun.swipecode.core;

import java.io.PrintWriter;
import java.io.StringWriter;

/** Local diagnostic text only; never includes the editor draft or keyboard mappings. */
public final class CrashReportFormat {
    private CrashReportFormat() {}
    public static boolean matchesVersion(String report, String version) {
        return report != null && version != null && report.startsWith("SwipeCode " + version + "\n");
    }
    public static String format(String version, String device, String thread, Throwable failure) {
        StringWriter stack = new StringWriter();
        failure.printStackTrace(new PrintWriter(stack));
        return "SwipeCode " + version + "\nDevice: " + device + "\nThread: " + thread
                + "\n\n" + stack;
    }
}
