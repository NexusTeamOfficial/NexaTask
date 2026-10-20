package com.nexusteam.nexatask.manager;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.nexusteam.nexatask.NexaTask;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TaskManager — lifecycle-aware task registry.
 *
 * <p>Cancels tasks only when the host Activity is <b>destroyed</b>
 * (not on pause/stop), so split-screen / multi-window background does not
 * kill running work. UI callbacks are still skipped safely by NexaTask
 * when the host is not alive.
 */
public class TaskManager {

    private static volatile TaskManager sInstance;
    private final Map<String, NexaTask<?, ?, ?>> mActiveTasks = new ConcurrentHashMap<>();
    private final Map<Object, List<String>> mTagToTaskIds = new ConcurrentHashMap<>();
    private final Map<String, WeakReference<LifecycleCallback>> mCallbacks = new ConcurrentHashMap<>();
    private final Map<String, Object> mTaskTags = new ConcurrentHashMap<>();
    private boolean mLifecycleRegistered;

    private TaskManager() {
    }

    public static TaskManager getInstance() {
        if (sInstance == null) {
            synchronized (TaskManager.class) {
                if (sInstance == null) {
                    sInstance = new TaskManager();
                }
            }
        }
        return sInstance;
    }

    /**
     * Register Activity lifecycle so destroyed activities cancel their tasks.
     * Call once from Application.onCreate().
     */
    public synchronized void init(@NonNull Application application) {
        if (mLifecycleRegistered) {
            return;
        }
        mLifecycleRegistered = true;
        application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
            }

            @Override
            public void onActivityStarted(@NonNull Activity activity) {
            }

            @Override
            public void onActivityResumed(@NonNull Activity activity) {
            }

            @Override
            public void onActivityPaused(@NonNull Activity activity) {
                // Do NOT cancel here — split-screen / multi-window pauses activities.
            }

            @Override
            public void onActivityStopped(@NonNull Activity activity) {
                // Do NOT cancel — background is normal.
            }

            @Override
            public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
            }

            @Override
            public void onActivityDestroyed(@NonNull Activity activity) {
                cancelTasksWithTag(activity);
            }
        });
    }

    public <Params, Progress, Result> NexaTask<Params, Progress, Result> registerTask(
            @NonNull NexaTask<Params, Progress, Result> task,
            @Nullable LifecycleCallback callback) {

        String taskId = task.getTaskId();
        mActiveTasks.put(taskId, task);

        if (callback != null) {
            mCallbacks.put(taskId, new WeakReference<>(callback));
        }

        Object tag = task.getTag();
        if (tag != null) {
            mTaskTags.put(taskId, tag);
            List<String> taskIds = mTagToTaskIds.get(tag);
            if (taskIds == null) {
                taskIds = Collections.synchronizedList(new ArrayList<String>());
                mTagToTaskIds.put(tag, taskIds);
            }
            if (!taskIds.contains(taskId)) {
                taskIds.add(taskId);
            }
        }
        return task;
    }

    public void unregisterTask(@NonNull String taskId) {
        NexaTask<?, ?, ?> task = mActiveTasks.remove(taskId);
        if (task != null) {
            Object tag = mTaskTags.remove(taskId);
            if (tag != null) {
                List<String> taskIds = mTagToTaskIds.get(tag);
                if (taskIds != null) {
                    taskIds.remove(taskId);
                    if (taskIds.isEmpty()) {
                        mTagToTaskIds.remove(tag);
                    }
                }
            }
        }
        mCallbacks.remove(taskId);
    }

    public void cancelTasksWithTag(@NonNull Object tag) {
        List<String> taskIds = mTagToTaskIds.get(tag);
        if (taskIds != null) {
            synchronized (taskIds) {
                for (String taskId : new ArrayList<>(taskIds)) {
                    cancelTask(taskId);
                }
            }
        }
    }

    public boolean cancelTask(@NonNull String taskId) {
        NexaTask<?, ?, ?> task = mActiveTasks.get(taskId);
        if (task != null && !task.isCancelled()) {
            boolean cancelled = task.cancel(true);
            unregisterTask(taskId);
            return cancelled;
        }
        return false;
    }

    public void cancelAllTasks() {
        for (String taskId : new ArrayList<>(mActiveTasks.keySet())) {
            cancelTask(taskId);
        }
    }

    public int getActiveTaskCount() {
        return mActiveTasks.size();
    }

    @Nullable
    public NexaTask<?, ?, ?> getTask(@NonNull String taskId) {
        return mActiveTasks.get(taskId);
    }

    public static boolean isLifecycleOwner(@NonNull Object obj) {
        return obj instanceof Activity
                || obj instanceof Fragment
                || obj instanceof FragmentActivity;
    }

    /**
     * Whether a host is still safe for UI updates.
     */
    public static boolean isHostAlive(@Nullable Object tag) {
        if (tag == null) {
            return true;
        }
        if (tag instanceof Activity) {
            Activity a = (Activity) tag;
            if (a.isFinishing()) {
                return false;
            }
            try {
                return !a.isDestroyed();
            } catch (Throwable t) {
                return true;
            }
        }
        if (tag instanceof Fragment) {
            Fragment f = (Fragment) tag;
            if (!f.isAdded() || f.isDetached() || f.isRemoving()) {
                return false;
            }
            Activity a = f.getActivity();
            if (a == null || a.isFinishing()) {
                return false;
            }
            try {
                return !a.isDestroyed();
            } catch (Throwable t) {
                return true;
            }
        }
        return true;
    }

    public interface LifecycleCallback {
        void onDestroy();

        void onPause();

        void onResume();
    }
}
