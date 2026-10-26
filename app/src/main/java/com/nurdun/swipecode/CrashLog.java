package com.nurdun.swipecode;

import android.content.Context;
import android.os.Build;
import com.nurdun.swipecode.core.CrashReportFormat;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

final class CrashLog {
    private static File file(Context context) { return new File(context.getFilesDir(), "last-crash.txt"); }
    static void save(Context context, String thread, Throwable failure) {
        try {
            String version = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
            String device = Build.MANUFACTURER + " " + Build.MODEL + ", Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")";
            String report = CrashReportFormat.format(version, device, thread, failure);
            try (FileOutputStream stream = new FileOutputStream(file(context))) {
                stream.write(report.getBytes(StandardCharsets.UTF_8)); stream.getFD().sync();
            }
        } catch (Throwable ignored) {
            // Never replace the original uncaught exception with a reporting error.
        }
    }
    static String read(Context context) {
        try (FileInputStream stream = new FileInputStream(file(context)); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096]; int count;
            while ((count = stream.read(buffer)) != -1 && bytes.size() < 65536) bytes.write(buffer, 0, Math.min(count, 65536 - bytes.size()));
            return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception unavailable) { return ""; }
    }
    static String readCurrentVersion(Context context) {
        String report = read(context);
        if (report.isEmpty()) return report;
        try {
            String version = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
            // Upgrading a fixed APK must not strand the user on the old version's error screen.
            return CrashReportFormat.matchesVersion(report, version) ? report : "";
        } catch (Exception unavailable) { return report; }
    }
    static void clear(Context context) { file(context).delete(); }
}
