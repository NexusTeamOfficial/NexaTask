# NexaTask

[![Version](https://img.shields.io/badge/version-1.1.3-blue.svg)](#)
[![JitPack](https://jitpack.io/v/NexusTeamOfficial/NexaTask.svg)](https://jitpack.io/#NexusTeamOfficial/NexaTask)

Modern **AsyncTask alternative** for Android — lambda API, lifecycle-safe UI, split-screen safe.

## Package structure

```text
com.nexusteam.nexatask/
├── NexaTask.java          # public entry + builders
├── api/                   # TaskListener, ProgressListener
├── core/                  # TaskExecutors, UiDispatcher, HostChecker
├── model/                 # TaskStatus, TaskPriority
├── util/                  # TaskLogger
├── manager/               # TaskManager (lifecycle)
└── lambda/                # BackgroundWork, callbacks
```

## Install (JitPack)

```gradle
// settings.gradle
maven { url 'https://jitpack.io' }

// app/build.gradle
implementation 'com.github.NexusTeamOfficial:NexaTask:1.1.3'
```

**Important:** Create a **new** GitHub release tag `1.1.3` (or `v1.1.3`) from the latest **main** branch — do not reuse old `v1.1.1` / `v1.1.2` tags (they pointed at wrong commits).

1. Open: https://github.com/NexusTeamOfficial/NexaTask/releases/new  
2. Tag: `1.1.3` → target **main**  
3. Publish  
4. Wait for green build: https://jitpack.io/#NexusTeamOfficial/NexaTask  

## Lambda API

```java
NexaTask.<Void, Integer, String>lambda(this)
    .doInBackground(() -> {
        return "ok";
    })
    .onPreExecute(() -> { })
    .onPostExecute(result -> { })
    .onProgressUpdate(values -> { })
    .onError(e -> { })
    .onCancelled(() -> { })
    .setTimeout(15, TimeUnit.SECONDS)
    .setPriority(TaskPriority.HIGH)
    .execute();
```

```java
NexaTask.run(this, () -> loadData())
    .onSuccess(data -> updateUi(data))
    .onError(e -> showError(e))
    .execute();
```

## Classic subclass

```java
new NexaTask<Void, Integer, String>(this) {
    @Override
    protected String doInBackground(Void... params) throws Exception {
        publishProgress(50);
        return "done";
    }
    @Override
    protected void onPostExecute(String result) { }
}.execute();
```

## Init (recommended)

```java
public class App extends Application {
    @Override public void onCreate() {
        super.onCreate();
        TaskManager.getInstance().init(this);
    }
}
```

## Split-screen safety

Pass `this` as host. When host is finishing/destroyed, UI callbacks are **skipped** — no crash. Tasks cancel only on **Activity.destroy**, never on pause/stop.

## Logging

```java
TaskLogger.setDebugEnabled(true);
TaskLogger.setMinLogLevel(TaskLogger.LogLevel.DEBUG);
```

## License

MIT
