package com.nexusteam.nexatask.core;

import com.nexusteam.nexatask.model.TaskPriority;
import com.nexusteam.nexatask.util.TaskLogger;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Shared thread pools. LinkedBlockingQueue only (no PriorityBlockingQueue). */
public final class TaskExecutors {

    private static final String TAG = "NexaTask";
    private static final int CPU = Runtime.getRuntime().availableProcessors();
    private static final int CORE = Math.max(2, Math.min(CPU - 1, 4));
    private static final int MAX = CPU * 2 + 1;

    private static final ExecutorService HIGH = createPool("Nexa-High", Thread.MAX_PRIORITY - 1);
    private static final ExecutorService NORMAL = createPool("Nexa-Def", Thread.NORM_PRIORITY);
    private static final ExecutorService LOW = createPool("Nexa-Low", Thread.MIN_PRIORITY + 1);
    private static final ScheduledExecutorService SCHEDULED =
            Executors.newScheduledThreadPool(2, factory("Nexa-Sched", Thread.NORM_PRIORITY));

    private TaskExecutors() {}

    public static ExecutorService forPriority(TaskPriority priority) {
        if (priority == null) return NORMAL;
        switch (priority) {
            case HIGH: return HIGH;
            case LOW: return LOW;
            default: return NORMAL;
        }
    }

    public static ExecutorService high() { return HIGH; }
    public static ExecutorService normal() { return NORMAL; }
    public static ExecutorService low() { return LOW; }
    public static ScheduledExecutorService scheduled() { return SCHEDULED; }

    public static void shutdown() {
        try {
            HIGH.shutdown(); NORMAL.shutdown(); LOW.shutdown(); SCHEDULED.shutdown();
        } catch (Exception ignored) {}
    }

    private static ExecutorService createPool(String name, int priority) {
        return new ThreadPoolExecutor(CORE, MAX, 30L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<Runnable>(), factory(name, priority));
    }

    private static ThreadFactory factory(final String prefix, final int priority) {
        return new ThreadFactory() {
            private final AtomicInteger count = new AtomicInteger(1);
            @Override public Thread newThread(Runnable r) {
                Thread t = new Thread(r, prefix + "-" + count.getAndIncrement());
                t.setPriority(priority);
                t.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                    @Override public void uncaughtException(Thread thread, Throwable ex) {
                        TaskLogger.e(TAG, "Uncaught on " + thread.getName(), ex);
                    }
                });
                return t;
            }
        };
    }
}
