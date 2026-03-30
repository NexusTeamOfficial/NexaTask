package com.nexusteam.nexatask.example;

import android.os.Bundle;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.nexusteam.nexatask.core.NexaTask;
import com.nexusteam.nexatask.manager.TaskManager;
import com.nexusteam.nexatask.utils.TaskLogger;

import java.util.concurrent.TimeUnit;

/**
 * ExampleUsage - Demonstrates how to use the NexaTask library
 */
public class ExampleUsage extends AppCompatActivity {

    private static final String TAG = "NexaTaskExample";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 1. Initialize TaskManager (usually in Application class)
        TaskManager.getInstance().init(getApplication());
        
        // 2. Configure Logger
        TaskLogger.setDebugEnabled(true);
        TaskLogger.setMinLogLevel(TaskLogger.LogLevel.DEBUG);

        // 3. Execute a simple background task
        new MySimpleTask(this).execute("Hello NexaTask!");
    }

    /**
     * A simple implementation of NexaTask
     */
    private static class MySimpleTask extends NexaTask<String, Integer, String> {
        
        public MySimpleTask(Object tag) {
            super(tag);
            // Configure task
            setTimeout(5, TimeUnit.SECONDS);
            setMaxRetries(2);
            setPriority(Priority.HIGH);
        }

        @Override
        protected void onPreExecute() {
            Log.d(TAG, "Task is about to start...");
        }

        @Override
        protected String doInBackground(String... params) throws Exception {
            String input = params[0];
            Log.d(TAG, "Doing background work with: " + input);
            
            // Simulate progress
            for (int i = 1; i <= 5; i++) {
                Thread.sleep(500);
                publishProgress(i * 20);
            }
            
            return "Result: " + input.toUpperCase();
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            Log.d(TAG, "Progress: " + values[0] + "%");
        }

        @Override
        protected void onPostExecute(String result) {
            Log.d(TAG, "Task finished with result: " + result);
        }

        @Override
        protected void onError(Throwable error) {
            Log.e(TAG, "Task failed: " + error.getMessage());
        }
    }
}
