package com.nexusteam.nexatask.core;

import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import com.nexusteam.nexatask.util.TaskLogger;

/** Posts runnables to the main thread and swallows callback exceptions. */
public final class UiDispatcher {
    private static final String TAG = "NexaTask";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private UiDispatcher() {}

    public static void post(@NonNull final Runnable r) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runSafe(r);
        } else {
            MAIN.post(new Runnable() {
                @Override public void run() { runSafe(r); }
            });
        }
    }

    private static void runSafe(@NonNull Runnable r) {
        try { r.run(); }
        catch (Throwable t) { TaskLogger.e(TAG, "UI callback crash swallowed", t); }
    }
}
