package com.nexusteam.nexatask.manager;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.nexusteam.nexatask.NexaTask;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TaskManager - Manages task lifecycle and prevents memory leaks
 * 
 * Automatically cancels tasks when activities/fragments are destroyed
 */
public class TaskManager {
    
    private static volatile TaskManager sInstance;
    private final Map<String, NexaTask<?, ?, ?>> mActiveTasks = new ConcurrentHashMap<>();
    private final Map<Object, List<String>> mTagToTaskIds = new ConcurrentHashMap<>();
    private final Map<String, WeakReference<LifecycleCallback>> mCallbacks = new ConcurrentHashMap<>();
    private final Map<String, Object> mTaskTags = new ConcurrentHashMap<>();
    
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
     * Initialize with application (for automatic lifecycle management)
     */
    public void init(@NonNull Application application) {
        application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {}
            
            @Override
            public void onActivityStarted(Activity activity) {}
            
            @Override
            public void onActivityResumed(Activity activity) {}
            
            @Override
            public void onActivityPaused(Activity activity) {}
            
            @Override
            public void onActivityStopped(Activity activity) {}
            
            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}
            
            @Override
            public void onActivityDestroyed(Activity activity) {
                cancelTasksWithTag(activity);
            }
        });
    }
    
    /**
     * Register a task with lifecycle callback
     */
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
            taskIds.add(taskId);
        }
        
        return task;
    }
    
    /**
     * Unregister a task
     */
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
    
    /**
     * Cancel all tasks for a specific tag (activity/fragment)
     */
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
    
    /**
     * Cancel a specific task
     */
    public boolean cancelTask(@NonNull String taskId) {
        NexaTask<?, ?, ?> task = mActiveTasks.get(taskId);
        if (task != null && !task.isCancelled()) {
            boolean cancelled = task.cancel(true);
            if (cancelled) {
                unregisterTask(taskId);
            }
            return cancelled;
        }
        return false;
    }
    
    /**
     * Cancel all active tasks
     */
    public void cancelAllTasks() {
        for (String taskId : new ArrayList<>(mActiveTasks.keySet())) {
            cancelTask(taskId);
        }
    }
    
    /**
     * Get active task count
     */
    public int getActiveTaskCount() {
        return mActiveTasks.size();
    }
    
    /**
     * Get task by ID
     */
    @Nullable
    public NexaTask<?, ?, ?> getTask(@NonNull String taskId) {
        return mActiveTasks.get(taskId);
    }
    
    /**
     * Check if object is a lifecycle owner (Activity/Fragment)
     */
    public static boolean isLifecycleOwner(@NonNull Object obj) {
        return obj instanceof Activity || obj instanceof Fragment || obj instanceof FragmentActivity;
    }
    
    /**
     * Lifecycle callback interface
     */
    public interface LifecycleCallback {
        void onDestroy();
        void onPause();
        void onResume();
    }
}
