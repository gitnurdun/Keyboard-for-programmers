package com.nurdun.swipecode;

import android.app.Application;

public final class DiagnosticApplication extends Application {
    @Override public void onCreate() {
        super.onCreate();
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, failure) -> {
            try { CrashLog.save(this, thread.getName(), failure); }
            finally {
                if (previous != null) previous.uncaughtException(thread, failure);
                else { android.os.Process.killProcess(android.os.Process.myPid()); System.exit(10); }
            }
        });
    }
}
