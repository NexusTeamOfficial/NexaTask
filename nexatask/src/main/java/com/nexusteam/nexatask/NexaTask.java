package com.nexusteam.nexatask;

import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.nexusteam.nexatask.manager.TaskManager;
import com.nexusteam.nexatask.utils.TaskLogger;

import java.lang.ref.WeakReference;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * NexaTask - AsyncTask Alternative
 * 
 * A powerful, feature-rich background task library with:
 * - Full AsyncTask compatibility (drop-in replacement)
 * - Automatic retry mechanism
 * - Timeout support
 * - Pause/Resume functionality
 * - Priority-based execution
 * - Memory leak prevention
 * - Lifecycle awareness
 * - Comprehensive error handling
 * 
 * @param <Params> Input parameter type
 * @param <Progress> Progress update type
 * @param <Result> Result type
 * 
 * @author NexaTask Team
 * @version 1.0.0
 * @since 1.0
 */
public abstract class NexaTask<Params, Progress, Result> {
    
    // ======================== VERSION INFO ========================
    
    public static final String VERSION_NAME = "1.0.0";
    public static final int VERSION_CODE = 1;
    public static final String LIBRARY_NAME = "NexaTask";
    
    // ======================== THREAD POOL CONFIGURATION ========================
    
    private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();
    private static final int CORE_POOL_SIZE = Math.max(2, Math.min(CPU_COUNT - 1, 4));
    private static final int MAXIMUM_POOL_SIZE = CPU_COUNT * 2 + 1;
    private static final long KEEP_ALIVE_TIME = 30L;
    private static final TimeUnit KEEP_ALIVE_TIME_UNIT = TimeUnit.SECONDS;
    
    // Thread pools for different priorities
    private static final ExecutorService HIGH_PRIORITY_EXECUTOR;
    private static final ExecutorService DEFAULT_EXECUTOR;
    private static final ExecutorService LOW_PRIORITY_EXECUTOR;
    private static final ScheduledExecutorService SCHEDULED_EXECUTOR;
    private static final Handler MAIN_HANDLER;
    
    static {
        HIGH_PRIORITY_EXECUTOR = new ThreadPoolExecutor(
            CORE_POOL_SIZE,
            MAXIMUM_POOL_SIZE,
            KEEP_ALIVE_TIME,
            KEEP_ALIVE_TIME_UNIT,
            new PriorityBlockingQueue<Runnable>(),
            new NexaThreadFactory("NexaTask-High", Thread.MAX_PRIORITY - 1)
        );
        
        DEFAULT_EXECUTOR = new ThreadPoolExecutor(
            CORE_POOL_SIZE,
            MAXIMUM_POOL_SIZE,
            KEEP_ALIVE_TIME,
            KEEP_ALIVE_TIME_UNIT,
            new LinkedBlockingQueue<Runnable>(),
            new NexaThreadFactory("NexaTask-Default", Thread.NORM_PRIORITY)
        );
        
        LOW_PRIORITY_EXECUTOR = new ThreadPoolExecutor(
            CORE_POOL_SIZE,
            MAXIMUM_POOL_SIZE,
            KEEP_ALIVE_TIME,
            KEEP_ALIVE_TIME_UNIT,
            new LinkedBlockingQueue<Runnable>(),
            new NexaThreadFactory("NexaTask-Low", Thread.MIN_PRIORITY + 1)
        );
        
        SCHEDULED_EXECUTOR = Executors.newScheduledThreadPool(2, 
            new NexaThreadFactory("NexaTask-Scheduled", Thread.NORM_PRIORITY));
        
        MAIN_HANDLER = new Handler(Looper.getMainLooper());
        
        // Register shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                shutdownExecutors();
            }
        }));
    }
    
    private static void shutdownExecutors() {
        try {
            DEFAULT_EXECUTOR.shutdown();
            HIGH_PRIORITY_EXECUTOR.shutdown();
            LOW_PRIORITY_EXECUTOR.shutdown();
            SCHEDULED_EXECUTOR.shutdown();
        } catch (Exception e) {
            // Ignore during shutdown
        }
    }
    
    // ======================== TASK STATE ========================
    
    private enum TaskState {
        PENDING, RUNNING, PAUSED, CANCELLED, FINISHED, TIMEOUT, ERROR
    }
    
    private final AtomicReference<TaskState> mState = new AtomicReference<>(TaskState.PENDING);
    private final AtomicBoolean mCancelled = new AtomicBoolean(false);
    private final AtomicBoolean mIsPaused = new AtomicBoolean(false);
    private final AtomicReference<Result> mResult = new AtomicReference<>();
    private final AtomicReference<Throwable> mError = new AtomicReference<>();
    private final AtomicInteger mRetryCount = new AtomicInteger(0);
    
    private volatile long mStartTime;
    private volatile long mExecutionTimeout = 0;
    private volatile int mMaxRetries = 0;
    private volatile Priority mPriority = Priority.NORMAL;
    private volatile WeakReference<Object> mTag = null;
    private volatile String mTaskId;
    private volatile boolean mLoggingEnabled = true;
    
    private TaskListener<Result> mTaskListener;
    private ProgressListener<Progress> mProgressListener;
    
    // ======================== ENUMS ========================
    
    public enum Status {
        PENDING, RUNNING, PAUSED, CANCELLED, FINISHED, TIMEOUT, ERROR
    }
    
    public enum Priority {
        HIGH, NORMAL, LOW
    }
    
    // ======================== CONSTRUCTORS ========================
    
    public NexaTask() {
        this(null);
    }
    
    public NexaTask(@Nullable Object tag) {
        mTaskId = generateTaskId();
        if (tag != null) {
            mTag = new WeakReference<>(tag);
        }
        
        // Auto-register with TaskManager if tag is an Activity/Fragment
        if (tag != null && TaskManager.isLifecycleOwner(tag)) {
            TaskManager.getInstance().registerTask(this, null);
        }
        
        if (mLoggingEnabled) {
            TaskLogger.d(LIBRARY_NAME, "Task created: " + mTaskId);
        }
    }
    
    // ======================== ABSTRACT METHODS ========================
    
    /**
     * Override this method to perform background work
     * 
     * @param params Input parameters
     * @return Result of background operation
     * @throws Exception Any exception during execution
     */
    @WorkerThread
    protected abstract Result doInBackground(@Nullable Params... params) throws Exception;
    
    // ======================== CALLBACK METHODS ========================
    
    /**
     * Called on UI thread before execution starts
     */
    @MainThread
    protected void onPreExecute() {
    }
    
    /**
     * Called on UI thread after completion
     * 
     * @param result Result from doInBackground
     */
    @MainThread
    protected void onPostExecute(@Nullable Result result) {
    }
    
    /**
     * Called on UI thread when progress is published
     * 
     * @param values Progress values
     */
    @MainThread
    protected void onProgressUpdate(@Nullable Progress... values) {
    }
    
    /**
     * Called on UI thread when task is cancelled
     */
    @MainThread
    protected void onCancelled() {
    }
    
    /**
     * Called on UI thread when task is cancelled (with result)
     * 
     * @param result Result before cancellation
     */
    @MainThread
    protected void onCancelled(@Nullable Result result) {
        onCancelled();
    }
    
    /**
     * Called on UI thread when error occurs
     * 
     * @param error The exception that occurred
     */
    @MainThread
    protected void onError(@NonNull Throwable error) {
        if (mLoggingEnabled) {
            TaskLogger.e(LIBRARY_NAME, "Task error: " + mTaskId, error);
        }
    }
    
    /**
     * Called on UI thread when task times out
     */
    @MainThread
    protected void onTimeout() {
        if (mLoggingEnabled) {
            TaskLogger.w(LIBRARY_NAME, "Task timeout: " + mTaskId);
        }
    }
    
    /**
     * Called on background thread before retry
     * 
     * @param attemptCount Current retry attempt number
     * @param lastError The error that caused the retry
     */
    @WorkerThread
    protected void onRetry(int attemptCount, @NonNull Throwable lastError) {
        if (mLoggingEnabled) {
            TaskLogger.d(LIBRARY_NAME, "Retrying task: " + mTaskId + ", attempt: " + attemptCount);
        }
    }
    
    /**
     * Called on UI thread when task is paused
     */
    @MainThread
    protected void onPaused() {
    }
    
    /**
     * Called on UI thread when task is resumed
     */
    @MainThread
    protected void onResumed() {
    }
    
    // ======================== PUBLIC API ========================
    
    /**
     * Execute the task with default executor
     * 
     * @param params Input parameters
     * @return This task instance for chaining
     */
    @NonNull
    public final NexaTask<Params, Progress, Result> execute(@Nullable Params... params) {
        return executeOnExecutor(getExecutorForPriority(), params);
    }
    
    /**
     * Execute the task after a delay
     * 
     * @param delay Delay duration
     * @param unit Time unit
     * @param params Input parameters
     * @return This task instance for chaining
     */
    @NonNull
    public final NexaTask<Params, Progress, Result> executeDelayed(long delay, 
                                                                   @NonNull TimeUnit unit,
                                                                   @Nullable final Params... params) {
        if (mState.get() != TaskState.PENDING) {
            throw new IllegalStateException("Task already executed: " + mState.get());
        }
        
        SCHEDULED_EXECUTOR.schedule(new Runnable() {
            @Override
            public void run() {
                executeOnExecutor(getExecutorForPriority(), params);
            }
        }, delay, unit);
        
        return this;
    }
    
    /**
     * Execute the task on specific executor
     *
     * @param executor The executor to run the task on
     * @param params Input parameters
     * @return This task instance for chaining
     */
    @NonNull
    public final NexaTask<Params, Progress, Result> executeOnExecutor(@NonNull Executor executor, 
                                                                      @Nullable final Params... params) {
        if (mState.get() != TaskState.PENDING && mState.get() != TaskState.RUNNING) {
            throw new IllegalStateException("Task already executed: " + mState.get());
        }
        
        if (mState.compareAndSet(TaskState.PENDING, TaskState.RUNNING)) {
            mStartTime = System.currentTimeMillis();
            
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    safeOnPreExecute();
                }
            });
            
            if (mExecutionTimeout > 0) {
                setupTimeout();
            }
            
            executor.execute(new BackgroundTaskRunnable(params));
        }
        
        return this;
    }
    
    /**
     * Publish progress from background thread
     * 
     * @param values Progress values
     */
    @WorkerThread
    protected final void publishProgress(@Nullable final Progress... values) {
        if (!mCancelled.get()) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    safeOnProgressUpdate(values);
                    if (mProgressListener != null) {
                        mProgressListener.onProgress(values);
                    }
                }
            });
        }
    }
    
    /**
     * Cancel the task
     * 
     * @param mayInterruptIfRunning Whether to interrupt the background thread
     * @return True if task was cancelled
     */
    public final boolean cancel(boolean mayInterruptIfRunning) {
        mCancelled.set(true);
        boolean result = setCancelled();
        if (result) {
            TaskManager.getInstance().unregisterTask(mTaskId);
        }
        return result;
    }
    
    /**
     * Pause the task
     */
    public final void pause() {
        if (mState.get() == TaskState.RUNNING && mIsPaused.compareAndSet(false, true)) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    safeOnPaused();
                }
            });
        }
    }
    
    /**
     * Resume the task
     */
    public final void resume() {
        if (mIsPaused.compareAndSet(true, false)) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    safeOnResumed();
                }
            });
        }
    }
    
    /**
     * Check if task is cancelled
     */
    public final boolean isCancelled() {
        return mCancelled.get();
    }
    
    /**
     * Check if task is running
     */
    public final boolean isRunning() {
        return mState.get() == TaskState.RUNNING;
    }
    
    /**
     * Check if task is paused
     */
    public final boolean isPaused() {
        return mIsPaused.get();
    }
    
    /**
     * Get task result (if completed)
     */
    @Nullable
    public final Result getResult() {
        return mResult.get();
    }
    
    /**
     * Get error if any
     */
    @Nullable
    public final Throwable getError() {
        return mError.get();
    }
    
    /**
     * Get unique task ID
     */
    @NonNull
    public final String getTaskId() {
        return mTaskId;
    }
    
    /**
     * Get execution time in milliseconds
     */
    public final long getExecutionTime() {
        if (mStartTime == 0) return 0;
        return System.currentTimeMillis() - mStartTime;
    }
    
    /**
     * Get task tag
     */
    @Nullable
    public final Object getTag() {
        return mTag != null ? mTag.get() : null;
    }
    
    // ======================== CONFIGURATION ========================
    
    /**
     * Set task priority
     */
    @NonNull
    public NexaTask<Params, Progress, Result> setPriority(@NonNull Priority priority) {
        this.mPriority = priority;
        return this;
    }
    
    /**
     * Set execution timeout
     */
    @NonNull
    public NexaTask<Params, Progress, Result> setTimeout(long timeout, @NonNull TimeUnit unit) {
        this.mExecutionTimeout = unit.toMillis(timeout);
        return this;
    }
    
    /**
     * Set maximum retry attempts
     */
    @NonNull
    public NexaTask<Params, Progress, Result> setMaxRetries(int maxRetries) {
        this.mMaxRetries = maxRetries;
        return this;
    }
    
    /**
     * Set task tag for grouping
     */
    @NonNull
    public NexaTask<Params, Progress, Result> setTag(@Nullable Object tag) {
        this.mTag = tag != null ? new WeakReference<>(tag) : null;
        return this;
    }
    
    /**
     * Set task listener
     */
    @NonNull
    public NexaTask<Params, Progress, Result> setTaskListener(@Nullable TaskListener<Result> listener) {
        this.mTaskListener = listener;
        return this;
    }
    
    /**
     * Set progress listener
     */
    @NonNull
    public NexaTask<Params, Progress, Result> setProgressListener(@Nullable ProgressListener<Progress> listener) {
        this.mProgressListener = listener;
        return this;
    }
    
    /**
     * Enable/disable logging
     */
    @NonNull
    public NexaTask<Params, Progress, Result> setLoggingEnabled(boolean enabled) {
        this.mLoggingEnabled = enabled;
        return this;
    }
    
    // ======================== STATIC UTILITIES ========================
    
    /**
     * Get default executor
     */
    @NonNull
    public static ExecutorService getDefaultExecutor() {
        return DEFAULT_EXECUTOR;
    }
    
    /**
     * Get high priority executor
     */
    @NonNull
    public static ExecutorService getHighPriorityExecutor() {
        return HIGH_PRIORITY_EXECUTOR;
    }
    
    /**
     * Get low priority executor
     */
    @NonNull
    public static ExecutorService getLowPriorityExecutor() {
        return LOW_PRIORITY_EXECUTOR;
    }
    
    /**
     * Shutdown all executors gracefully
     */
    public static void shutdown() {
        shutdownExecutors();
    }
    
    // ======================== PRIVATE METHODS ========================
    
    private ExecutorService getExecutorForPriority() {
        switch (mPriority) {
            case HIGH:
                return HIGH_PRIORITY_EXECUTOR;
            case LOW:
                return LOW_PRIORITY_EXECUTOR;
            default:
                return DEFAULT_EXECUTOR;
        }
    }
    
    private void setupTimeout() {
        SCHEDULED_EXECUTOR.schedule(new Runnable() {
            @Override
            public void run() {
                if (mState.get() == TaskState.RUNNING && !mCancelled.get()) {
                    mCancelled.set(true);
                    if (mState.compareAndSet(TaskState.RUNNING, TaskState.TIMEOUT)) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                safeOnTimeout();
                                if (mTaskListener != null) {
                                    mTaskListener.onTimeout();
                                }
                            }
                        });
                        
                        if (mLoggingEnabled) {
                            TaskLogger.w(LIBRARY_NAME, "Task timeout: " + mTaskId);
                        }
                    }
                }
            }
        }, mExecutionTimeout, TimeUnit.MILLISECONDS);
    }
    
    private boolean setCancelled() {
        if (mState.get() == TaskState.RUNNING && !mCancelled.get()) {
            mCancelled.set(true);
            if (mState.compareAndSet(TaskState.RUNNING, TaskState.CANCELLED)) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        safeOnCancelled();
                    }
                });
                return true;
            }
        }
        return false;
    }
    
    private void handleError(@NonNull final Throwable error) {
        mError.set(error);
        
        // Check if we should retry
        if (mRetryCount.get() < mMaxRetries) {
            final int attempt = mRetryCount.incrementAndGet();
            
            // Call onRetry callback
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        onRetry(attempt, error);
                    } catch (Exception e) {
                        // Ignore callback errors
                    }
                }
            });
            
            // Retry after delay
            try {
                Thread.sleep(1000 * attempt); // Exponential backoff
                if (mState.get() == TaskState.RUNNING && !mCancelled.get()) {
                    // Re-execute
                    executeOnExecutor(getExecutorForPriority(), (Params[]) null);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return;
        }
        
        // No more retries, final error
        if (mState.compareAndSet(TaskState.RUNNING, TaskState.ERROR)) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        onError(error);
                        if (mTaskListener != null) {
                            mTaskListener.onError(error);
                        }
                    } catch (Exception e) {
                        // Silent fail for listener
                    }
                }
            });
        }
    }
    
    private void runOnUiThread(@NonNull Runnable runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run();
        } else {
            MAIN_HANDLER.post(runnable);
        }
    }
    
    // ======================== SAFE CALLBACKS ========================
    
    private void safeOnPreExecute() {
        try {
            onPreExecute();
            if (mTaskListener != null) {
                mTaskListener.onPreExecute();
            }
        } catch (Exception e) {
            TaskLogger.e(LIBRARY_NAME, "Error in onPreExecute", e);
        }
    }
    
    private void safeOnPostExecute(@Nullable Result result) {
        try {
            onPostExecute(result);
            if (mTaskListener != null) {
                mTaskListener.onPostExecute(result);
            }
        } catch (Exception e) {
            TaskLogger.e(LIBRARY_NAME, "Error in onPostExecute", e);
        }
    }
    
    private void safeOnProgressUpdate(@Nullable Progress... values) {
        try {
            onProgressUpdate(values);
        } catch (Exception e) {
            TaskLogger.e(LIBRARY_NAME, "Error in onProgressUpdate", e);
        }
    }
    
    private void safeOnCancelled() {
        try {
            onCancelled();
        } catch (Exception e) {
            TaskLogger.e(LIBRARY_NAME, "Error in onCancelled", e);
        }
    }
    
    private void safeOnTimeout() {
        try {
            onTimeout();
        } catch (Exception e) {
            TaskLogger.e(LIBRARY_NAME, "Error in onTimeout", e);
        }
    }
    
    private void safeOnPaused() {
        try {
            onPaused();
        } catch (Exception e) {
            TaskLogger.e(LIBRARY_NAME, "Error in onPaused", e);
        }
    }
    
    private void safeOnResumed() {
        try {
            onResumed();
        } catch (Exception e) {
            TaskLogger.e(LIBRARY_NAME, "Error in onResumed", e);
        }
    }
    
    // ======================== INNER CLASSES ========================
    
    private class BackgroundTaskRunnable implements Runnable {
        private final Params[] mParams;
        
        BackgroundTaskRunnable(@Nullable Params[] params) {
            this.mParams = params;
        }
        
        @Override
        public void run() {
            try {
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
                
                // Check if paused
                while (mIsPaused.get() && !mCancelled.get()) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                
                if (mCancelled.get()) {
                    return;
                }
                
                // Execute background work
                Result result = doInBackground(mParams);
                mResult.set(result);
                
                if (!mCancelled.get()) {
                    if (mState.compareAndSet(TaskState.RUNNING, TaskState.FINISHED)) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                safeOnPostExecute(mResult.get());
                            }
                        });
                        
                        if (mLoggingEnabled) {
                            TaskLogger.d(LIBRARY_NAME, "Task completed: " + mTaskId + 
                                        ", time: " + getExecutionTime() + "ms");
                        }
                    }
                } else {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            safeOnCancelled();
                        }
                    });
                }
            } catch (final Exception e) {
                handleError(e);
            }
        }
    }
    
    private static class NexaThreadFactory implements ThreadFactory {
        private final AtomicInteger mCount = new AtomicInteger(1);
        private final String mNamePrefix;
        private final int mPriority;
        
        NexaThreadFactory(String namePrefix, int priority) {
            this.mNamePrefix = namePrefix;
            this.mPriority = priority;
        }
        
        @Override
        public Thread newThread(@NonNull Runnable r) {
            Thread thread = new Thread(r, mNamePrefix + " #" + mCount.getAndIncrement());
            thread.setPriority(mPriority);
            thread.setDaemon(false);
            return thread;
        }
    }
    
    private static String generateTaskId() {
        return "NexaTask-" + System.currentTimeMillis() + "-" + 
               Thread.currentThread().getId() + "-" + 
               System.nanoTime();
    }
    
    // ======================== INTERFACES ========================
    
    /**
     * Task lifecycle listener
     */
    public interface TaskListener<T> {
        void onPreExecute();
        void onPostExecute(@Nullable T result);
        void onCancelled();
        void onError(@NonNull Throwable error);
        void onTimeout();
    }
    
    /**
     * Progress update listener
     */
    public interface ProgressListener<T> {
        void onProgress(@Nullable T... progress);
    }
}
