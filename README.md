# NexaTask

A powerful, feature-rich background task library for Android.

## Features

*   **AsyncTask Alternative**: Drop-in replacement for AsyncTask.
*   **Retry Mechanism**: Automatic retry with exponential backoff.
*   **Timeout Support**: Set execution timeouts for tasks.
*   **Lifecycle Awareness**: Automatically cancels tasks when activities/fragments are destroyed.
*   **Priority-based Execution**: High, Normal, and Low priority thread pools.

## Installation (JitPack)

1.  **Add JitPack repository to your root `settings.gradle`**:

    ```gradle
    dependencyResolutionManagement {
        repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
        repositories {
            google()
            mavenCentral()
            maven { url 'https://jitpack.io' }
        }
    }
    ```

2.  **Add the dependency to your app module's `build.gradle`**:

    ```gradle
    dependencies {
        implementation 'com.github.NexusTeamOfficial:NexaTask:1.0.0'
    }
    ```

## Usage

```java
new NexaTask<String, Integer, String>(this) {
    @Override
    protected String doInBackground(String... params) throws Exception {
        // Background work
        return "Result";
    }

    @Override
    protected void onPostExecute(String result) {
        // UI thread
    }
}.execute("Param");
```
