package com.nexusteam.nexatask;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.fragment.app.Fragment;
import com.nexusteam.nexatask.lambda.*;
import com.nexusteam.nexatask.manager.TaskManager;
import com.nexusteam.nexatask.utils.TaskLogger;
import java.lang.ref.WeakReference;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** NexaTask 1.1.0 — AsyncTask alt with lambda API + split-screen safe UI. */
public abstract class NexaTask<Params, Progress, Result> {
    public static final String VERSION_NAME = "1.1.0";
    public static final int VERSION_CODE = 2;
    public static final String LIBRARY_NAME = "NexaTask";

    private static final int CPU = Runtime.getRuntime().availableProcessors();
    private static final int CORE = Math.max(2, Math.min(CPU - 1, 4));
    private static final int MAXP = CPU * 2 + 1;
    private static final ExecutorService HIGH = pool("Nexa-High", Thread.MAX_PRIORITY - 1);
    private static final ExecutorService DEF = pool("Nexa-Def", Thread.NORM_PRIORITY);
    private static final ExecutorService LOW = pool("Nexa-Low", Thread.MIN_PRIORITY + 1);
    private static final ScheduledExecutorService SCHED = Executors.newScheduledThreadPool(2, fac("Nexa-Sched", Thread.NORM_PRIORITY));
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static ExecutorService pool(String n, int p) {
        return new ThreadPoolExecutor(CORE, MAXP, 30L, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(), fac(n, p));
    }
    private static ThreadFactory fac(final String n, final int p) {
        return new ThreadFactory() {
            final AtomicInteger c = new AtomicInteger(1);
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, n + "-" + c.getAndIncrement());
                t.setPriority(p);
                t.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                    public void uncaughtException(Thread th, Throwable e) { TaskLogger.e(LIBRARY_NAME, th.getName(), e); }
                });
                return t;
            }
        };
    }

    private enum St { PENDING, RUNNING, PAUSED, CANCELLED, FINISHED, TIMEOUT, ERROR }
    public enum Status { PENDING, RUNNING, PAUSED, CANCELLED, FINISHED, TIMEOUT, ERROR }
    public enum Priority { HIGH, NORMAL, LOW }

    private final AtomicReference<St> state = new AtomicReference<>(St.PENDING);
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final AtomicBoolean paused = new AtomicBoolean(false);
    private final AtomicReference<Result> result = new AtomicReference<>();
    private final AtomicReference<Throwable> error = new AtomicReference<>();
    private final AtomicInteger retries = new AtomicInteger(0);
    private volatile long start, timeoutMs;
    private volatile int maxRetries;
    private volatile Priority priority = Priority.NORMAL;
    private volatile WeakReference<Object> tagRef;
    private volatile String taskId;
    private volatile boolean log = true, skipUiIfDead = true;
    private TaskListener<Result> listener;
    private ProgressListener<Progress> progressListener;

    public NexaTask() { this(null); }
    public NexaTask(@Nullable Object tag) {
        taskId = "nexa-" + Long.toHexString(System.nanoTime());
        if (tag != null) {
            tagRef = new WeakReference<>(tag);
            if (TaskManager.isLifecycleOwner(tag)) TaskManager.getInstance().registerTask(this, null);
        }
        if (log) TaskLogger.d(LIBRARY_NAME, "created " + taskId);
    }

    @WorkerThread protected abstract Result doInBackground(@Nullable Params... params) throws Exception;
    @MainThread protected void onPreExecute() {}
    @MainThread protected void onPostExecute(@Nullable Result r) {}
    @MainThread protected void onProgressUpdate(@Nullable Progress... v) {}
    @MainThread protected void onCancelled() {}
    @MainThread protected void onCancelled(@Nullable Result r) { onCancelled(); }
    @MainThread protected void onError(@NonNull Throwable e) { if (log) TaskLogger.e(LIBRARY_NAME, taskId, e); }
    @MainThread protected void onTimeout() { if (log) TaskLogger.w(LIBRARY_NAME, "timeout " + taskId); }
    @WorkerThread protected void onRetry(int a, @NonNull Throwable e) { if (log) TaskLogger.d(LIBRARY_NAME, "retry " + a); }
    @MainThread protected void onPaused() {}
    @MainThread protected void onResumed() {}

    @NonNull public final NexaTask<Params, Progress, Result> execute(@Nullable Params... params) {
        return executeOnExecutor(exec(), params);
    }
    @NonNull public final NexaTask<Params, Progress, Result> executeDelayed(long d, @NonNull TimeUnit u, @Nullable final Params... params) {
        if (state.get() != St.PENDING) throw new IllegalStateException("executed");
        SCHED.schedule(new Runnable() { public void run() {
            if (!cancelled.get() && state.get() == St.PENDING) executeOnExecutor(exec(), params);
        }}, d, u);
        return this;
    }
    @NonNull public final NexaTask<Params, Progress, Result> executeOnExecutor(@NonNull Executor ex, @Nullable final Params... params) {
        if (state.get() != St.PENDING) throw new IllegalStateException("executed");
        if (state.compareAndSet(St.PENDING, St.RUNNING)) {
            start = System.currentTimeMillis();
            ui(new Runnable() { public void run() { safePre(); } });
            if (timeoutMs > 0) scheduleTimeout();
            try {
                ex.execute(new Runnable() { public void run() { bg(params); } });
            } catch (RejectedExecutionException e) {
                state.set(St.ERROR); handleError(e);
            }
        }
        return this;
    }

    @WorkerThread protected final void publishProgress(@Nullable final Progress... values) {
        if (cancelled.get()) return;
        ui(new Runnable() { public void run() {
            if (cancelled.get() || !isHostAlive()) return;
            safeProgress(values);
            if (progressListener != null) try { progressListener.onProgress(values); } catch (Exception e) { TaskLogger.e(LIBRARY_NAME, "prog", e); }
        }});
    }

    public final boolean cancel(boolean mayInterrupt) {
        cancelled.set(true);
        boolean ok = markCancel();
        TaskManager.getInstance().unregisterTask(taskId);
        return ok;
    }
    public final void pause() {
        if (state.get() == St.RUNNING && paused.compareAndSet(false, true))
            ui(new Runnable() { public void run() { safePaused(); } });
    }
    public final void resume() {
        if (paused.compareAndSet(true, false))
            ui(new Runnable() { public void run() { safeResumed(); } });
    }
    public final boolean isCancelled() { return cancelled.get(); }
    public final boolean isRunning() { return state.get() == St.RUNNING; }
    public final boolean isPaused() { return paused.get(); }
    @Nullable public final Result getResult() { return result.get(); }
    @Nullable public final Throwable getError() { return error.get(); }
    @NonNull public final String getTaskId() { return taskId; }
    public final long getExecutionTime() { return start == 0 ? 0 : System.currentTimeMillis() - start; }
    @Nullable public final Object getTag() { return tagRef != null ? tagRef.get() : null; }
    @NonNull public final Status getStatus() {
        switch (state.get()) {
            case PENDING: return Status.PENDING;
            case RUNNING: return Status.RUNNING;
            case PAUSED: return Status.PAUSED;
            case CANCELLED: return Status.CANCELLED;
            case FINISHED: return Status.FINISHED;
            case TIMEOUT: return Status.TIMEOUT;
            default: return Status.ERROR;
        }
    }

    @NonNull public NexaTask<Params, Progress, Result> setPriority(@NonNull Priority p) { priority = p; return this; }
    @NonNull public NexaTask<Params, Progress, Result> setTimeout(long t, @NonNull TimeUnit u) { timeoutMs = u.toMillis(t); return this; }
    @NonNull public NexaTask<Params, Progress, Result> setMaxRetries(int n) { maxRetries = Math.max(0, n); return this; }
    @NonNull public NexaTask<Params, Progress, Result> setTag(@Nullable Object tag) {
        tagRef = tag != null ? new WeakReference<>(tag) : null;
        if (tag != null && TaskManager.isLifecycleOwner(tag)) TaskManager.getInstance().registerTask(this, null);
        return this;
    }
    @NonNull public NexaTask<Params, Progress, Result> setTaskListener(@Nullable TaskListener<Result> l) { listener = l; return this; }
    @NonNull public NexaTask<Params, Progress, Result> setProgressListener(@Nullable ProgressListener<Progress> l) { progressListener = l; return this; }
    @NonNull public NexaTask<Params, Progress, Result> setLoggingEnabled(boolean e) { log = e; return this; }
    @NonNull public NexaTask<Params, Progress, Result> setSkipUiIfHostDead(boolean s) { skipUiIfDead = s; return this; }

    @NonNull public static <P, Prog, R> LambdaBuilder<P, Prog, R> lambda() { return new LambdaBuilder<P, Prog, R>(null); }
    @NonNull public static <P, Prog, R> LambdaBuilder<P, Prog, R> lambda(@Nullable Object host) { return new LambdaBuilder<P, Prog, R>(host); }
    @NonNull public static <R> SimpleBuilder<R> run(@Nullable Object host, @NonNull BackgroundWork<R> work) { return new SimpleBuilder<R>(host, work); }
    @NonNull public static <R> SimpleBuilder<R> run(@NonNull BackgroundWork<R> work) { return run(null, work); }

    @NonNull public static ExecutorService getDefaultExecutor() { return DEF; }
    @NonNull public static ExecutorService getHighPriorityExecutor() { return HIGH; }
    @NonNull public static ExecutorService getLowPriorityExecutor() { return LOW; }
    public static void shutdown() { try { DEF.shutdown(); HIGH.shutdown(); LOW.shutdown(); SCHED.shutdown(); } catch (Exception ignored) {} }

    protected final boolean isHostAlive() {
        if (!skipUiIfDead) return true;
        Object tag = getTag();
        if (tag == null) return true;
        if (tag instanceof Activity) {
            Activity a = (Activity) tag;
            if (a.isFinishing()) return false;
            try { return !a.isDestroyed(); } catch (Throwable t) { return true; }
        }
        if (tag instanceof Fragment) {
            Fragment f = (Fragment) tag;
            if (!f.isAdded() || f.isDetached() || f.isRemoving()) return false;
            Activity a = f.getActivity();
            if (a == null || a.isFinishing()) return false;
            try { return !a.isDestroyed(); } catch (Throwable t) { return true; }
        }
        return true;
    }

    private ExecutorService exec() {
        switch (priority) { case HIGH: return HIGH; case LOW: return LOW; default: return DEF; }
    }

    private void scheduleTimeout() {
        final long ms = timeoutMs;
        SCHED.schedule(new Runnable() { public void run() {
            if (state.get() == St.RUNNING && !cancelled.get()) {
                cancelled.set(true);
                if (state.compareAndSet(St.RUNNING, St.TIMEOUT)) {
                    ui(new Runnable() { public void run() {
                        safeTimeout();
                        if (listener != null && isHostAlive()) try { listener.onTimeout(); } catch (Exception e) { TaskLogger.e(LIBRARY_NAME, "to", e); }
                    }});
                }
            }
        }}, ms, TimeUnit.MILLISECONDS);
    }

    private boolean markCancel() {
        St prev = state.get();
        if (prev == St.RUNNING || prev == St.PENDING || prev == St.PAUSED) {
            cancelled.set(true); state.set(St.CANCELLED);
            ui(new Runnable() { public void run() { safeCancel(); } });
            return true;
        }
        return false;
    }

    private void handleError(@NonNull final Throwable err) {
        error.set(err);
        if (retries.get() < maxRetries && !cancelled.get()) {
            final int attempt = retries.incrementAndGet();
            try { onRetry(attempt, err); } catch (Throwable t) { TaskLogger.e(LIBRARY_NAME, "retry", t); }
            SCHED.schedule(new Runnable() { public void run() {
                if (cancelled.get()) return;
                state.set(St.RUNNING);
                try {
                    Result r = doInBackground();
                    result.set(r);
                    if (!cancelled.get() && state.compareAndSet(St.RUNNING, St.FINISHED))
                        ui(new Runnable() { public void run() { safePost(result.get()); } });
                } catch (Throwable e2) { handleError(e2); }
            }}, Math.min(1000L * attempt, 5000L), TimeUnit.MILLISECONDS);
            return;
        }
        state.set(St.ERROR);
        ui(new Runnable() { public void run() {
            safeError(err);
            if (listener != null && isHostAlive()) try { listener.onError(err); } catch (Exception e) { TaskLogger.e(LIBRARY_NAME, "le", e); }
        }});
        TaskManager.getInstance().unregisterTask(taskId);
    }

    private void bg(@Nullable Params[] params) {
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
            while (paused.get() && !cancelled.get()) {
                try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
            }
            if (cancelled.get()) { ui(new Runnable() { public void run() { safeCancel(); } }); return; }
            Result r = doInBackground(params);
            result.set(r);
            if (!cancelled.get()) {
                if (state.compareAndSet(St.RUNNING, St.FINISHED)) {
                    ui(new Runnable() { public void run() { safePost(result.get()); } });
                    if (log) TaskLogger.d(LIBRARY_NAME, "done " + taskId + " " + getExecutionTime() + "ms");
                }
            } else ui(new Runnable() { public void run() { safeCancel(); } });
        } catch (Throwable e) { if (!cancelled.get()) handleError(e); }
    }

    private void ui(@NonNull final Runnable r) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            try { r.run(); } catch (Throwable t) { TaskLogger.e(LIBRARY_NAME, "ui", t); }
        } else MAIN.post(new Runnable() { public void run() {
            try { r.run(); } catch (Throwable t) { TaskLogger.e(LIBRARY_NAME, "ui", t); }
        }});
    }

    private void safePre() {
        if (!isHostAlive()) return;
        try { onPreExecute(); if (listener != null) listener.onPreExecute(); } catch (Exception e) { TaskLogger.e(LIBRARY_NAME, "pre", e); }
    }
    private void safePost(@Nullable Result r) {
        if (!isHostAlive()) { TaskManager.getInstance().unregisterTask(taskId); return; }
        try { onPostExecute(r); if (listener != null) listener.onPostExecute(r); } catch (Exception e) { TaskLogger.e(LIBRARY_NAME, "post", e); }
        TaskManager.getInstance().unregisterTask(taskId);
    }
    private void safeProgress(@Nullable Progress... v) {
        if (!isHostAlive()) return;
        try { onProgressUpdate(v); } catch (Exception e) { TaskLogger.e(LIBRARY_NAME, "prog", e); }
    }
    private void safeCancel() {
        if (!isHostAlive()) { TaskManager.getInstance().unregisterTask(taskId); return; }
        try { onCancelled(result.get()); if (listener != null) listener.onCancelled(); } catch (Exception e) { TaskLogger.e(LIBRARY_NAME, "cancel", e); }
        TaskManager.getInstance().unregisterTask(taskId);
    }
    private void safeTimeout() {
        if (!isHostAlive()) { TaskManager.getInstance().unregisterTask(taskId); return; }
        try { onTimeout(); } catch (Exception e) { TaskLogger.e(LIBRARY_NAME, "timeout", e); }
        TaskManager.getInstance().unregisterTask(taskId);
    }
    private void safeError(@NonNull Throwable e) {
        if (!isHostAlive()) return;
        try { onError(e); } catch (Exception x) { TaskLogger.e(LIBRARY_NAME, "err", x); }
    }
    private void safePaused() { if (!isHostAlive()) return; try { onPaused(); } catch (Exception e) { TaskLogger.e(LIBRARY_NAME, "pause", e); } }
    private void safeResumed() { if (!isHostAlive()) return; try { onResumed(); } catch (Exception e) { TaskLogger.e(LIBRARY_NAME, "resume", e); } }

    public interface TaskListener<T> {
        void onPreExecute(); void onPostExecute(@Nullable T result); void onCancelled();
        void onError(@NonNull Throwable error); void onTimeout();
    }
    public interface ProgressListener<T> { void onProgress(@Nullable T... progress); }

    public static final class LambdaBuilder<P, Prog, R> {
        private final Object host;
        private BackgroundWork<R> background;
        private PreCallback pre, cancelled, timeoutCb;
        private PostCallback<R> post;
        private ProgressCallback<Prog> progress;
        private ErrorCallback error;
        private long timeoutMs; private int maxRetries; private Priority priority = Priority.NORMAL;
        LambdaBuilder(@Nullable Object host) { this.host = host; }
        @NonNull public LambdaBuilder<P, Prog, R> doInBackground(@NonNull BackgroundWork<R> w) { background = w; return this; }
        @NonNull public LambdaBuilder<P, Prog, R> onPreExecute(@Nullable PreCallback c) { pre = c; return this; }
        @NonNull public LambdaBuilder<P, Prog, R> onPostExecute(@Nullable PostCallback<R> c) { post = c; return this; }
        @NonNull public LambdaBuilder<P, Prog, R> onProgressUpdate(@Nullable ProgressCallback<Prog> c) { progress = c; return this; }
        @NonNull public LambdaBuilder<P, Prog, R> onCancelled(@Nullable PreCallback c) { cancelled = c; return this; }
        @NonNull public LambdaBuilder<P, Prog, R> onError(@Nullable ErrorCallback c) { error = c; return this; }
        @NonNull public LambdaBuilder<P, Prog, R> onTimeout(@Nullable PreCallback c) { timeoutCb = c; return this; }
        @NonNull public LambdaBuilder<P, Prog, R> setTimeout(long v, @NonNull TimeUnit u) { timeoutMs = u.toMillis(v); return this; }
        @NonNull public LambdaBuilder<P, Prog, R> setMaxRetries(int n) { maxRetries = n; return this; }
        @NonNull public LambdaBuilder<P, Prog, R> setPriority(@NonNull Priority p) { priority = p; return this; }
        @NonNull @SuppressWarnings("unchecked")
        public NexaTask<P, Prog, R> execute(@Nullable final P... params) {
            if (background == null) throw new IllegalStateException("doInBackground required");
            final BackgroundWork<R> bg = background;
            final PreCallback preCb = pre, cancelCb = cancelled, toCb = timeoutCb;
            final PostCallback<R> postCb = post;
            final ProgressCallback<Prog> progCb = progress;
            final ErrorCallback errCb = error;
            NexaTask<P, Prog, R> task = new NexaTask<P, Prog, R>(host) {
                protected R doInBackground(@Nullable P... p) throws Exception { return bg.run(); }
                protected void onPreExecute() { if (preCb != null) preCb.run(); }
                protected void onPostExecute(@Nullable R r) { if (postCb != null) postCb.onResult(r); }
                protected void onProgressUpdate(@Nullable Prog... v) { if (progCb != null) progCb.onProgress(v); }
                protected void onCancelled() { if (cancelCb != null) cancelCb.run(); }
                protected void onError(@NonNull Throwable e) { if (errCb != null) errCb.onError(e); else super.onError(e); }
                protected void onTimeout() { if (toCb != null) toCb.run(); else super.onTimeout(); }
            };
            task.setPriority(priority).setMaxRetries(maxRetries);
            if (timeoutMs > 0) task.setTimeout(timeoutMs, TimeUnit.MILLISECONDS);
            task.execute(params);
            return task;
        }
    }

    public static final class SimpleBuilder<R> {
        private final Object host; private final BackgroundWork<R> work;
        private PostCallback<R> success; private ErrorCallback error;
        private long timeoutMs; private Priority priority = Priority.NORMAL;
        SimpleBuilder(@Nullable Object host, @NonNull BackgroundWork<R> work) { this.host = host; this.work = work; }
        @NonNull public SimpleBuilder<R> onSuccess(@Nullable PostCallback<R> c) { success = c; return this; }
        @NonNull public SimpleBuilder<R> onError(@Nullable ErrorCallback c) { error = c; return this; }
        @NonNull public SimpleBuilder<R> setTimeout(long v, @NonNull TimeUnit u) { timeoutMs = u.toMillis(v); return this; }
        @NonNull public SimpleBuilder<R> setPriority(@NonNull Priority p) { priority = p; return this; }
        @NonNull public NexaTask<Void, Void, R> execute() {
            final PostCallback<R> ok = success; final ErrorCallback err = error;
            NexaTask<Void, Void, R> task = new NexaTask<Void, Void, R>(host) {
                protected R doInBackground(@Nullable Void... p) throws Exception { return work.run(); }
                protected void onPostExecute(@Nullable R r) { if (ok != null) ok.onResult(r); }
                protected void onError(@NonNull Throwable e) { if (err != null) err.onError(e); else super.onError(e); }
            };
            task.setPriority(priority);
            if (timeoutMs > 0) task.setTimeout(timeoutMs, TimeUnit.MILLISECONDS);
            task.execute();
            return task;
        }
    }
}
