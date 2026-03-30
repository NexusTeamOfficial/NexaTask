# NexaTask - Java Background Task Library

A powerful, feature-rich background task library for Android, serving as a drop-in replacement for AsyncTask with enhanced capabilities.

## Features

- **AsyncTask Replacement**: Drop-in compatibility with familiar API.
- **Automatic Retry**: Built-in mechanism for failed tasks.
- **Timeout Support**: Prevent tasks from running indefinitely.
- **Pause/Resume**: Full control over task execution.
- **Priority-based Execution**: High, Normal, and Low priority thread pools.
- **Lifecycle Awareness**: Automatically cancels tasks when activities or fragments are destroyed.
- **Memory Leak Prevention**: Uses weak references to prevent context leaks.

## Installation

### Step 1: Add the JitPack repository to your build file

Add it in your root `build.gradle` at the end of repositories:

```gradle
allprojects {
    repositories {
        ...
        maven { url 'https://jitpack.io' }
    }
}
```

### Step 2: Add the dependency

Add the following to your module-level `build.gradle`:

```gradle
dependencies {
    implementation 'com.github.NexusTeamOfficial:NexaTask:1.0.0'
}
```

## Usage

### 1. Initialize TaskManager (Optional but recommended)

Initialize in your `Application` class for automatic lifecycle management:

```java
public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        TaskManager.getInstance().init(this);
    }
}
```

### 2. Create a Task

Extend `NexaTask<Params, Progress, Result>`:

```java
public class MyTask extends NexaTask<String, Integer, String> {
    
    public MyTask(Object tag) {
        super(tag); // Pass Activity or Fragment as tag for lifecycle management
        
        // Configuration
        setTimeout(10, TimeUnit.SECONDS);
        setMaxRetries(3);
        setPriority(Priority.HIGH);
    }

    @Override
    protected void onPreExecute() {
        // UI thread: Before background work
    }

    @Override
    protected String doInBackground(String... params) throws Exception {
        // Background thread: Perform work
        publishProgress(50); // Optional: Publish progress
        return "Work done!";
    }

    @Override
    protected void onProgressUpdate(Integer... values) {
        // UI thread: Handle progress updates
    }

    @Override
    protected void onPostExecute(String result) {
        // UI thread: Handle completion
    }

    @Override
    protected void onError(Throwable error) {
        // UI thread: Handle errors
    }
}
```

### 3. Execute the Task

```java
new MyTask(this).execute("My Parameter");
```

## License

```
Copyright 2026 NexaTask Team

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
