package com.nexusteam.nexatask.utils;

import android.util.Log;

/**
 * TaskLogger - Centralized logging utility for NexaTask library
 * Provides consistent logging with different log levels
 */
public class TaskLogger {
    
    private static final String DEFAULT_TAG = "NexaTask";
    private static boolean sDebugEnabled = true;
    private static boolean sVerboseEnabled = false;
    private static LogLevel sMinLogLevel = LogLevel.INFO;
    
    private TaskLogger() {
        // Private constructor to prevent instantiation
    }
    
    public enum LogLevel {
        VERBOSE(2),
        DEBUG(3),
        INFO(4),
        WARN(5),
        ERROR(6),
        ASSERT(7),
        NONE(8);
        
        private final int level;
        
        LogLevel(int level) {
            this.level = level;
        }
        
        public int getLevel() {
            return level;
        }
    }
    
    /**
     * Configure logging settings
     */
    public static void configure(boolean debugEnabled, boolean verboseEnabled, LogLevel minLevel) {
        sDebugEnabled = debugEnabled;
        sVerboseEnabled = verboseEnabled;
        sMinLogLevel = minLevel;
    }
    
    /**
     * Enable debug logging
     */
    public static void setDebugEnabled(boolean enabled) {
        sDebugEnabled = enabled;
    }
    
    /**
     * Enable verbose logging
     */
    public static void setVerboseEnabled(boolean enabled) {
        sVerboseEnabled = enabled;
    }
    
    /**
     * Set minimum log level
     */
    public static void setMinLogLevel(LogLevel level) {
        sMinLogLevel = level;
    }
    
    /**
     * Log verbose message
     */
    public static void v(String tag, String message) {
        if (sVerboseEnabled && sMinLogLevel.getLevel() <= LogLevel.VERBOSE.getLevel()) {
            Log.v(tag != null ? tag : DEFAULT_TAG, message);
        }
    }
    
    /**
     * Log verbose message with throwable
     */
    public static void v(String tag, String message, Throwable tr) {
        if (sVerboseEnabled && sMinLogLevel.getLevel() <= LogLevel.VERBOSE.getLevel()) {
            Log.v(tag != null ? tag : DEFAULT_TAG, message, tr);
        }
    }
    
    /**
     * Log debug message
     */
    public static void d(String tag, String message) {
        if (sDebugEnabled && sMinLogLevel.getLevel() <= LogLevel.DEBUG.getLevel()) {
            Log.d(tag != null ? tag : DEFAULT_TAG, message);
        }
    }
    
    /**
     * Log debug message with throwable
     */
    public static void d(String tag, String message, Throwable tr) {
        if (sDebugEnabled && sMinLogLevel.getLevel() <= LogLevel.DEBUG.getLevel()) {
            Log.d(tag != null ? tag : DEFAULT_TAG, message, tr);
        }
    }
    
    /**
     * Log info message
     */
    public static void i(String tag, String message) {
        if (sMinLogLevel.getLevel() <= LogLevel.INFO.getLevel()) {
            Log.i(tag != null ? tag : DEFAULT_TAG, message);
        }
    }
    
    /**
     * Log info message with throwable
     */
    public static void i(String tag, String message, Throwable tr) {
        if (sMinLogLevel.getLevel() <= LogLevel.INFO.getLevel()) {
            Log.i(tag != null ? tag : DEFAULT_TAG, message, tr);
        }
    }
    
    /**
     * Log warning message
     */
    public static void w(String tag, String message) {
        if (sMinLogLevel.getLevel() <= LogLevel.WARN.getLevel()) {
            Log.w(tag != null ? tag : DEFAULT_TAG, message);
        }
    }
    
    /**
     * Log warning message with throwable
     */
    public static void w(String tag, String message, Throwable tr) {
        if (sMinLogLevel.getLevel() <= LogLevel.WARN.getLevel()) {
            Log.w(tag != null ? tag : DEFAULT_TAG, message, tr);
        }
    }
    
    /**
     * Log error message
     */
    public static void e(String tag, String message) {
        if (sMinLogLevel.getLevel() <= LogLevel.ERROR.getLevel()) {
            Log.e(tag != null ? tag : DEFAULT_TAG, message);
        }
    }
    
    /**
     * Log error message with throwable
     */
    public static void e(String tag, String message, Throwable tr) {
        if (sMinLogLevel.getLevel() <= LogLevel.ERROR.getLevel()) {
            Log.e(tag != null ? tag : DEFAULT_TAG, message, tr);
        }
    }
    
    /**
     * Log assert message
     */
    public static void wtf(String tag, String message) {
        if (sMinLogLevel.getLevel() <= LogLevel.ASSERT.getLevel()) {
            Log.wtf(tag != null ? tag : DEFAULT_TAG, message);
        }
    }
    
    /**
     * Log assert message with throwable
     */
    public static void wtf(String tag, String message, Throwable tr) {
        if (sMinLogLevel.getLevel() <= LogLevel.ASSERT.getLevel()) {
            Log.wtf(tag != null ? tag : DEFAULT_TAG, message, tr);
        }
    }
    
    /**
     * Get current debug state
     */
    public static boolean isDebugEnabled() {
        return sDebugEnabled;
    }
    
    /**
     * Get current verbose state
     */
    public static boolean isVerboseEnabled() {
        return sVerboseEnabled;
    }
    
    /**
     * Get current minimum log level
     */
    public static LogLevel getMinLogLevel() {
        return sMinLogLevel;
    }
}
