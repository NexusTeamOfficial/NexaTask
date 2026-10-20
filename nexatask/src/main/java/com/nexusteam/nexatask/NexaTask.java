package com.nexusteam.nexatask;

import android.os.Process;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.nexusteam.nexatask.api.ProgressListener;
import com.nexusteam.nexatask.api.TaskListener;
import com.nexusteam.nexatask.core.HostChecker;
import com.nexusteam.nexatask.core.TaskExecutors;
import com.nexusteam.nexatask.core.UiDispatcher;
import com.nexusteam.nexatask.lambda.BackgroundWork;
import com.nexusteam.nexatask.lambda.ErrorCallback;
import com.nexusteam.nexatask.lambda.PostCallback;
import com.nexusteam.nexatask.lambda.PreCallback;
import com.nexusteam.nexatask.lambda.ProgressCallback;
import com.nexusteam.nexatask.manager.TaskManager;
import com.nexusteam.nexatask.model.TaskPriority;
import com.nexusteam.nexatask.model.TaskStatus;
import com.nexusteam.nexatask.util.TaskLogger;

import java.lang.ref.WeakReference;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** NexaTask 1.1.1 — modular api/core/model/util structure + lambda + split-screen safe. */
public abstract class NexaTask<Params, Progress, Result> {

    public static final String VERSION_NAME = "1.1.1";
    public static final int VERSION_CODE = 3;
    public static final String LIBRARY_NAME = "NexaTask";

    /** @deprecated use {@link TaskStatus} */
    @Deprecated
    public enum Status { PENDING, RUNNING, PAUSED, CANCELLED, FINISHED, TIMEOUT, ERROR }

    /** @deprecated use {@link TaskPriority} */
    @Deprecated
    public enum Priority { HIGH, NORMAL, LOW }

    private enum St { PENDING, RUNNING, PAUSED, CANCELLED, FINISHED, TIMEOUT, ERROR }

    private final AtomicReference<St> state = new AtomicReference<>(St.PENDING);
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final AtomicBoolean paused = new AtomicBoolean(false);
    private final AtomicReference<Result> result = new AtomicReference<>();
    private final AtomicReference<Throwable> error = new AtomicReference<>();
    private final AtomicInteger retries = new AtomicInteger(0);

    private volatile long start, timeoutMs;
    private volatile int maxRetries;
    private volatile TaskPriority priority = TaskPriority.NORMAL;
    private volatile WeakReference<Object> tagRef;
    private volatile String taskId;
    private volatile boolean log = true;
    private volatile boolean skipUiIfDead = true;

    private TaskListener<Result> listener;
    private ProgressListener<Progress> progressListener;

    public NexaTask() { this(null); }

    public NexaTask(@Nullable Object tag) {
        taskId = "nexa-" + Long.toHexString(System.nanoTime());
        if (tag != null) {
            tagRef = new WeakReference<>(tag);
            if (TaskManager.isLifecycleOwner(tag))
                TaskManager.getInstance().registerTask(this, null);
        }
        if (log) TaskLogger.d(LIBRARY_NAME, "created " + taskId);
    }

    @WorkerThread protected abstract Result doInBackground(@Nullable Params... params) throws Exception;
    @MainThread protected void onPreExecute() {}
    @MainThread protected void onPostExecute(@Nullable Result r) {}
    @MainThread protected void onProgressUpdate(@Nullable Progress... v) {}
    @MainThread protected void onCancelled() {}
    @MainThread protected void onCancelled(@Nullable Result r) { onCancelled(); }
    @MainThread protected void onError(@NonNull Throwable e) {
        if (log) TaskLogger.e(LIBRARY_NAME, taskId, e);
    }
    @MainThread protected void onTimeout() {
        if (log) TaskLogger.w(LIBRARY_NAME, "timeout " + taskId);
    }
    @WorkerThread protected void onRetry(int a, @NonNull Throwable e) {
        if (log) TaskLogger.d(LIBRARY_NAME, "retry " + a);
    }
    @MainThread protected void onPaused() {}
    @MainThread protected void onResumed() {}

    @NonNull public final NexaTask<Params, Progress, Result> execute(@Nullable Params... params) {
        return executeOnExecutor(TaskExecutors.forPriority(priority), params);
    }

    @NonNull public final NexaTask<Params, Progress, Result> executeDelayed(
            long delay, @NonNull TimeUnit unit, @Nullable final Params... params) {
        if (state.get() != St.PENDING) throw new IllegalStateException("already executed");
        TaskExecutors.scheduled().schedule(new Runnable() {
            @Override public void run() {
                if (!cancelled.get() && state.get() == St.PENDING)
                    executeOnExecutor(TaskExecutors.forPriority(priority), params);
            }
        }, delay, unit);
        return this;
    }

    @NonNull public final NexaTask<Params, Progress, Result> executeOnExecutor(
            @NonNull Executor executor, @Nullable final Params... params) {
        if (state.get() != St.PENDING) throw new IllegalStateException("already executed");
        if (state.compareAndSet(St.PENDING, St.RUNNING)) {
            start = System.currentTimeMillis();
            UiDispatcher.post(new Runnable() { @Override public void run() { safePre(); } });
            if (timeoutMs > 0) scheduleTimeout();
            try {
                executor.execute(new Runnable() { @Override public void run() { background(params); } });
            } catch (RejectedExecutionException e) {
                state.set(St.ERROR); handleError(e);
            }
        }
        return this;
    }

    @WorkerThread protected final void publishProgress(@Nullable final Progress... values) {
        if (cancelled.get()) return;
        UiDispatcher.post(new Runnable() {
            @Override public void run() {
                if (cancelled.get() || !isHostAlive()) return;
                safeProgress(values);
                if (progressListener != null) {
                    try { progressListener.onProgress(values); }
                    catch (Exception e) { TaskLogger.e(LIBRARY_NAME, "ProgressListener", e); }
                }
            }
        });
    }

    public final boolean cancel(boolean mayInterrupt) {
        cancelled.set(true);
        boolean ok = markCancel();
        TaskManager.getInstance().unregisterTask(taskId);
        return ok;
    }

    public final void pause() {
        if (state.get() == St.RUNNING && paused.compareAndSet(false, true))
            UiDispatcher.post(new Runnable() { @Override public void run() { safePaused(); } });
    }

    public final void resume() {
        if (paused.compareAndSet(true, false))
            UiDispatcher.post(new Runnable() { @Override public void run() { safeResumed(); } });
    }

    public final boolean isCancelled() { return cancelled.get(); }
    public final boolean isRunning() { return state.get() == St.RUNNING; }
    public final boolean isPaused() { return paused.get(); }
    @Nullable public final Result getResult() { return result.get(); }
    @Nullable public final Throwable getError() { return error.get(); }
    @NonNull public final String getTaskId() { return taskId; }
    public final long getExecutionTime() {
        return start == 0 ? 0 : System.currentTimeMillis() - start;
    }
    @Nullable public final Object getTag() {
        return tagRef != null ? tagRef.get() : null;
    }

    @NonNull public final TaskStatus getTaskStatus() {
        switch (state.get()) {
            case PENDING: return TaskStatus.PENDING;
            case RUNNING: return TaskStatus.RUNNING;
            case PAUSED: return TaskStatus.PAUSED;
            case CANCELLED: return TaskStatus.CANCELLED;
            case FINISHED: return TaskStatus.FINISHED;
            case TIMEOUT: return TaskStatus.TIMEOUT;
            default: return TaskStatus.ERROR;
        }
    }

    /** @deprecated use {@link #getTaskStatus()} */
    @Deprecated
    @NonNull public final Status getStatus() {
        return Status.valueOf(getTaskStatus().name());
    }

    @NonNull public NexaTask<Params, Progress, Result> setPriority(@NonNull TaskPriority p) {
        priority = p; return this;
    }

    /** @deprecated use {@link #setPriority(TaskPriority)} */
    @Deprecated
    @NonNull public NexaTask<Params, Progress, Result> setPriority(@NonNull Priority p) {
        priority = TaskPriority.valueOf(p.name()); return this;
    }

    @NonNull public NexaTask<Params, Progress, Result> setTimeout(long t, @NonNull TimeUnit u) {
        timeoutMs = u.toMillis(t); return this;
    }

    @NonNull public NexaTask<Params, Progress, Result> setMaxRetries(int n) {
        maxRetries = Math.max(0, n); return this;
    }

    @NonNull public NexaTask<Params, Progress, Result> setTag(@Nullable Object tag) {
        tagRef = tag != null ? new WeakReference<>(tag) : null;
        if (tag != null && TaskManager.isLifecycleOwner(tag))
            TaskManager.getInstance().registerTask(this, null);
        return this;
    }

    @NonNull public NexaTask<Params, Progress, Result> setTaskListener(@Nullable TaskListener<Result> l) {
        listener = l; return this;
    }

    @NonNull public NexaTask<Params, Progress, Result> setProgressListener(@Nullable ProgressListener<Progress> l) {
        progressListener = l; return this;
    }

    @NonNull public NexaTask<Params, Progress, Result> setLoggingEnabled(boolean e) {
        log = e; return this;
    }

    @NonNull public NexaTask<Params, Progress, Result> setSkipUiIfHostDead(boolean s) {
        skipUiIfDead = s; return this;
    }

    @NonNull public static <P, G, R> LambdaBuilder<P, G, R> lambda() {
        return new LambdaBuilder<P, G, R>(null);
    }
    @NonNull public static <P, G, R> LambdaBuilder<P, G, R> lambda(@Nullable Object host) {
        return new LambdaBuilder<P, G, R>(host);
    }
    @NonNull public static <R> SimpleBuilder<R> run(@Nullable Object host, @NonNull BackgroundWork<R> work) {
        return new SimpleBuilder<R>(host, work);
    }
    @NonNull public static <R> SimpleBuilder<R> run(@NonNull BackgroundWork<R> work) {
        return run(null, work);
    }

    @NonNull public static ExecutorService getDefaultExecutor() { return TaskExecutors.normal(); }
    @NonNull public static ExecutorService getHighPriorityExecutor() { return TaskExecutors.high(); }
    @NonNull public static ExecutorService getLowPriorityExecutor() { return TaskExecutors.low(); }
    public static void shutdown() { TaskExecutors.shutdown(); }

    protected final boolean isHostAlive() {
        return !skipUiIfDead || HostChecker.isAlive(getTag());
    }

    private void scheduleTimeout() {
        final long ms = timeoutMs;
        TaskExecutors.scheduled().schedule(new Runnable() {
            @Override public void run() {
                if (state.get() == St.RUNNING && !cancelled.get()) {
                    cancelled.set(true);
                    if (state.compareAndSet(St.RUNNING, St.TIMEOUT)) {
                        UiDispatcher.post(new Runnable() {
                            @Override public void run() {
                                safeTimeout();
                                if (listener != null && isHostAlive()) {
                                    try { listener.onTimeout(); }
                                    catch (Exception e) { TaskLogger.e(LIBRARY_NAME, "timeout L", e); }
                                }
                            }
                        });
                    }
                }
            }
        }, ms, TimeUnit.MILLISECONDS);
    }

    private boolean markCancel() {
        St prev = state.get();
        if (prev == St.RUNNING || prev == St.PENDING || prev == St.PAUSED) {
            cancelled.set(true); state.set(St.CANCELLED);
            UiDispatcher.post(new Runnable() { @Override public void run() { safeCancel(); } });
            return true;
        }
        return false;
    }

    private void handleError(@NonNull final Throwable err) {
        error.set(err);
        if (retries.get() < maxRetries && !cancelled.get()) {
            final int attempt = retries.incrementAndGet();
            try { onRetry(attempt, err); }
            catch (Throwable t) { TaskLogger.e(LIBRARY_NAME, "onRetry", t); }
            TaskExecutors.scheduled().schedule(new Runnable() {
                @Override public void run() {
                    if (cancelled.get()) return;
                    state.set(St.RUNNING);
                    try {
                        Result r = doInBackground();
                        result.set(r);
                        if (!cancelled.get() && state.compareAndSet(St.RUNNING, St.FINISHED))
                            UiDispatcher.post(new Runnable() { @Override public void run() { safePost(result.get()); } });
                    } catch (Throwable e2) { handleError(e2); }
                }
            }, Math.min(1000L * attempt, 5000L), TimeUnit.MILLISECONDS);
            return;
        }
        state.set(St.ERROR);
        UiDispatcher.post(new Runnable() {
            @Override public void run() {
                safeError(err);
                if (listener != null && isHostAlive()) {
                    try { listener.onError(err); }
                    catch (Exception e) { TaskLogger.e(LIBRARY_NAME, "error L", e); }
                }
            }
        });
        TaskManager.getInstance().unregisterTask(taskId);
    }

    private void background(@Nullable Params[] params) {
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
            while (paused.get() && !cancelled.get()) {
                try { Thread.sleep(100); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
            }
            if (cancelled.get()) {
                UiDispatcher.post(new Runnable() { @Override public void run() { safeCancel(); } });
                return;
            }
            Result r = doInBackground(params);
            result.set(r);
            if (!cancelled.get()) {
                if (state.compareAndSet(St.RUNNING, St.FINISHED)) {
                    UiDispatcher.post(new Runnable() { @Override public void run() { safePost(result.get()); } });
                    if (log) TaskLogger.d(LIBRARY_NAME, "done " + taskId + " " + getExecutionTime() + "ms");
                }
            } else {
                UiDispatcher.post(new Runnable() { @Override public void run() { safeCancel(); } });
            }
        } catch (Throwable e) {
            if (!cancelled.get()) handleError(e);
        }
    }

    private void safePre() {
        if (!isHostAlive()) return;
        try {
            onPreExecute();
            if (listener != null) listener.onPreExecute();
        } catch (Exception e) { TaskLogger.e(LIBRARY_NAME, "onPreExecute", e); }
    }

    private void safePost(@Nullable Result r) {
        if (!isHostAlive()) {
            TaskManager.getInstance().unregisterTask(taskId);
            return;
        }
        try {
            onPostExecute(r);
            if (listener != null) listener.onPostExecute(r);
        } catch (Exception e) { TaskLogger.e(LIBRARY_NAME, "onPostExecute", e); }
        TaskManager.getInstance().unregisterTask(taskId);
    }

    private void safeProgress(@Nullable Progress... v) {
        if (!isHostAlive()) return;
        try { onProgressUpdate(v); }
        catch (Exception e) { TaskLogger.e(LIBRARY_NAME, "onProgressUpdate", e); }
    }

    private void safeCancel() {
        if (!isHostAlive()) {
            TaskManager.getInstance().unregisterTask(taskId);
            return;
        }
        try {
            onCancelled(result.get());
            if (listener != null) listener.onCancelled();
        } catch (Exception e) { TaskLogger.e(LIBRARY_NAME, "onCancelled", e); }
        TaskManager.getInstance().unregisterTask(taskId);
    }

    private void safeTimeout() {
        if (!isHostAlive()) {
            TaskManager.getInstance().unregisterTask(taskId);
            return;
        }
        try { onTimeout(); }
        catch (Exception e) { TaskLogger.e(LIBRARY_NAME, "onTimeout", e); }
        TaskManager.getInstance().unregisterTask(taskId);
    }

    private void safeError(@NonNull Throwable e) {
        if (!isHostAlive()) return;
        try { onError(e); }
        catch (Exception x) { TaskLogger.e(LIBRARY_NAME, "onError", x); }
    }

    private void safePaused() {
        if (!isHostAlive()) return;
        try { onPaused(); }
        catch (Exception e) { TaskLogger.e(LIBRARY_NAME, "onPaused", e); }
    }

    private void safeResumed() {
        if (!isHostAlive()) return;
        try { onResumed(); }
        catch (Exception e) { TaskLogger.e(LIBRARY_NAME, "onResumed", e); }
    }

    public static final class LambdaBuilder<P, G, R> {
        private final Object host;
        private BackgroundWork<R> background;
        private PreCallback pre, cancelled, timeoutCb;
        private PostCallback<R> post;
        private ProgressCallback<G> progress;
        private ErrorCallback error;
        private long timeoutMs;
        private int maxRetries;
        private TaskPriority priority = TaskPriority.NORMAL;

        LambdaBuilder(@Nullable Object host) { this.host = host; }

        @NonNull public LambdaBuilder<P, G, R> doInBackground(@NonNull BackgroundWork<R> w) { background = w; return this; }
        @NonNull public LambdaBuilder<P, G, R> onPreExecute(@Nullable PreCallback c) { pre = c; return this; }
        @NonNull public LambdaBuilder<P, G, R> onPostExecute(@Nullable PostCallback<R> c) { post = c; return this; }
        @NonNull public LambdaBuilder<P, G, R> onProgressUpdate(@Nullable ProgressCallback<G> c) { progress = c; return this; }
        @NonNull public LambdaBuilder<P, G, R> onCancelled(@Nullable PreCallback c) { cancelled = c; return this; }
        @NonNull public LambdaBuilder<P, G, R> onError(@Nullable ErrorCallback c) { error = c; return this; }
        @NonNull public LambdaBuilder<P, G, R> onTimeout(@Nullable PreCallback c) { timeoutCb = c; return this; }
        @NonNull public LambdaBuilder<P, G, R> setTimeout(long v, @NonNull TimeUnit u) { timeoutMs = u.toMillis(v); return this; }
        @NonNull public LambdaBuilder<P, G, R> setMaxRetries(int n) { maxRetries = n; return this; }
        @NonNull public LambdaBuilder<P, G, R> setPriority(@NonNull TaskPriority p) { priority = p; return this; }

        @NonNull @SuppressWarnings("unchecked")
        public NexaTask<P, G, R> execute(@Nullable final P... params) {
            if (background == null) throw new IllegalStateException("doInBackground required");
            final BackgroundWork<R> bg = background;
            final PreCallback preCb = pre, cancelCb = cancelled, toCb = timeoutCb;
            final PostCallback<R> postCb = post;
            final ProgressCallback<G> progCb = progress;
            final ErrorCallback errCb = error;
            NexaTask<P, G, R> task = new NexaTask<P, G, R>(host) {
                @Override protected R doInBackground(@Nullable P... p) throws Exception { return bg.run(); }
                @Override protected void onPreExecute() { if (preCb != null) preCb.run(); }
                @Override protected void onPostExecute(@Nullable R r) { if (postCb != null) postCb.onResult(r); }
                @Override protected void onProgressUpdate(@Nullable G... v) { if (progCb != null) progCb.onProgress(v); }
                @Override protected void onCancelled() { if (cancelCb != null) cancelCb.run(); }
                @Override protected void onError(@NonNull Throwable e) {
                    if (errCb != null) errCb.onError(e); else super.onError(e);
                }
                @Override protected void onTimeout() {
                    if (toCb != null) toCb.run(); else super.onTimeout();
                }
            };
            task.setPriority(priority).setMaxRetries(maxRetries);
            if (timeoutMs > 0) task.setTimeout(timeoutMs, TimeUnit.MILLISECONDS);
            task.execute(params);
            return task;
        }
    }

    public static final class SimpleBuilder<R> {
        private final Object host;
        private final BackgroundWork<R> work;
        private PostCallback<R> success;
        private ErrorCallback error;
        private long timeoutMs;
        private TaskPriority priority = TaskPriority.NORMAL;

        SimpleBuilder(@Nullable Object host, @NonNull BackgroundWork<R> work) {
            this.host = host; this.work = work;
        }

        @NonNull public SimpleBuilder<R> onSuccess(@Nullable PostCallback<R> c) { success = c; return this; }
        @NonNull public SimpleBuilder<R> onError(@Nullable ErrorCallback c) { error = c; return this; }
        @NonNull public SimpleBuilder<R> setTimeout(long v, @NonNull TimeUnit u) { timeoutMs = u.toMillis(v); return this; }
        @NonNull public SimpleBuilder<R> setPriority(@NonNull TaskPriority p) { priority = p; return this; }

        @NonNull public NexaTask<Void, Void, R> execute() {
            final PostCallback<R> ok = success;
            final ErrorCallback err = error;
            NexaTask<Void, Void, R> task = new NexaTask<Void, Void, R>(host) {
                @Override protected R doInBackground(@Nullable Void... p) throws Exception { return work.run(); }
                @Override protected void onPostExecute(@Nullable R r) { if (ok != null) ok.onResult(r); }
                @Override protected void onError(@NonNull Throwable e) {
                    if (err != null) err.onError(e); else super.onError(e);
                }
            };
            task.setPriority(priority);
            if (timeoutMs > 0) task.setTimeout(timeoutMs, TimeUnit.MILLISECONDS);
            task.execute();
            return task;
        }
    }
}
