# NexaTask

A powerful, feature-rich background task library for Android, designed as a modern and robust alternative to AsyncTask.

## Features
- **Lifecycle Awareness**: Automatically cancels tasks when Activities or Fragments are destroyed.
- **Retry Mechanism**: Built-in support for automatic retries on failure.
- **Timeout Support**: Set execution limits for background operations.
- **Priority-based Execution**: High, Normal, and Low priority thread pools.
- **Memory Leak Prevention**: Uses weak references to prevent context leaks.

## Installation

### 1. Add the JitPack repository to your build file
Add it in your root `build.gradle` at the end of repositories:

```gradle
allprojects {
    repositories {
        ...
        maven { url 'https://jitpack.io' }
    }
}
```

### 2. Add the dependency
Add the following implementation to your app's `build.gradle`:

```gradle
dependencies {
    implementation 'com.github.NexusTeamOfficial:NexaTask:1.0.0'
}
```

## Quick Start

```java
new NexaTask<String, Integer, String>() {
    @Override
    protected String doInBackground(String... params) throws Exception {
        // Your background work here
        return "Task Completed";
    }

    @Override
    protected void onPostExecute(String result) {
        // Handle result on UI thread
    }
}.execute("Input Data");
```

## License
This project is licensed under the MIT License.
