package com.nexusteam.nexatask.util;

import android.util.Log;

/** Centralized logging for NexaTask. */
public final class TaskLogger {

    private static final String DEFAULT_TAG = "NexaTask";
    private static boolean sDebugEnabled = true;
    private static boolean sVerboseEnabled = false;
    private static LogLevel sMinLogLevel = LogLevel.DEBUG;

    private TaskLogger() {}

    public enum LogLevel {
        VERBOSE(2), DEBUG(3), INFO(4), WARN(5), ERROR(6), ASSERT(7), NONE(8);
        private final int level;
        LogLevel(int level) { this.level = level; }
        public int getLevel() { return level; }
    }

    public static void configure(boolean debug, boolean verbose, LogLevel minLevel) {
        sDebugEnabled = debug;
        sVerboseEnabled = verbose;
        sMinLogLevel = minLevel;
    }

    public static void setDebugEnabled(boolean enabled) { sDebugEnabled = enabled; }
    public static void setVerboseEnabled(boolean enabled) { sVerboseEnabled = enabled; }
    public static void setMinLogLevel(LogLevel level) { sMinLogLevel = level; }

    public static void v(String tag, String message) {
        if (sVerboseEnabled && sMinLogLevel.getLevel() <= LogLevel.VERBOSE.getLevel())
            Log.v(tagOrDefault(tag), message);
    }
    public static void d(String tag, String message) {
        if (sDebugEnabled && sMinLogLevel.getLevel() <= LogLevel.DEBUG.getLevel())
            Log.d(tagOrDefault(tag), message);
    }
    public static void i(String tag, String message) {
        if (sMinLogLevel.getLevel() <= LogLevel.INFO.getLevel())
            Log.i(tagOrDefault(tag), message);
    }
    public static void w(String tag, String message) {
        if (sMinLogLevel.getLevel() <= LogLevel.WARN.getLevel())
            Log.w(tagOrDefault(tag), message);
    }
    public static void e(String tag, String message) {
        if (sMinLogLevel.getLevel() <= LogLevel.ERROR.getLevel())
            Log.e(tagOrDefault(tag), message);
    }
    public static void e(String tag, String message, Throwable tr) {
        if (sMinLogLevel.getLevel() <= LogLevel.ERROR.getLevel())
            Log.e(tagOrDefault(tag), message, tr);
    }
    public static boolean isDebugEnabled() { return sDebugEnabled; }
    private static String tagOrDefault(String tag) { return tag != null ? tag : DEFAULT_TAG; }
}
