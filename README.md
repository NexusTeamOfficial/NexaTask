# NexaTask

### Modern AsyncTask replacement for Android — pure Java, lambda-first, crash-safe

[![JitPack](https://jitpack.io/v/NexusTeamOfficial/NexaTask.svg)](https://jitpack.io/#NexusTeamOfficial/NexaTask)
![minSdk](https://img.shields.io/badge/minSdk-21-blue)
![license](https://img.shields.io/badge/license-MIT-green)
[![Java](https://img.shields.io/badge/Java-8%2B-orange.svg)](https://www.java.com)
[![Android](https://img.shields.io/badge/Platform-Android-3DDC84.svg)](https://developer.android.com)

> **Drop-in AsyncTask alternative** with Java 8 lambdas, lifecycle-aware UI callbacks, split-screen safety, priorities, timeouts, and zero Kotlin dependency.

```java
NexaTask.run(this, () -> api.fetchUser())
    .onSuccess(user -> bindUi(user))
    .onError(e -> showError(e))
    .execute();
```

![NexaTask demo](docs/demo.gif)

---

## Why NexaTask?

| Problem with AsyncTask | NexaTask fix |
|------------------------|-------------|
| Deprecated & verbose | Lambda builders + classic subclass |
| Crashes after rotation / split-screen | Host-aware UI — callbacks skipped if Activity/Fragment dead |
| No timeout / priority | Built-in timeout + `TaskPriority` |
| Weak error handling | First-class `onError` |
| Kotlin-only modern APIs | **100% Java** — works in Java & Kotlin apps |

Built for production apps that still want a simple background → UI flow without RxJava, Coroutines, or WorkManager overhead.

See the [sample app](samples/demo-app/) for a runnable demo (button → spinner → result, safe across rotation).

---

## Features

- **Lambda API** — `doInBackground`, `onPreExecute`, `onPostExecute`, `onProgressUpdate`, `onError`, `onCancelled`
- **Classic subclass** — same method names as AsyncTask for easy migration
- **Lifecycle-safe** — no UI update after `Activity`/`Fragment` destroy (split-screen safe)
- **Priority queue** — `LOW` / `NORMAL` / `HIGH`
- **Timeouts** — cancel work if it runs too long
- **TaskManager** — track & cancel tasks by host
- **TaskLogger** — structured debug logging
- **Min SDK 21** · **Pure Java** · **JitPack** one-line install

---

## Installation

### 1. Add JitPack repository

**settings.gradle** (or `settings.gradle.kts`):

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

### 2. Add dependency

**app/build.gradle**:

```gradle
dependencies {
    implementation 'com.github.NexusTeamOfficial:NexaTask:v1.1.3'
}
```

Sync Gradle — done.

---

## Quick start

### One-liner background + UI

```java
NexaTask.run(this, () -> repository.loadFeed())
    .onSuccess(list -> adapter.submitList(list))
    .onError(Throwable::printStackTrace)
    .execute();
```

### Full lambda (AsyncTask-style methods)

```java
NexaTask.<Void, Integer, String>lambda(this)
    .doInBackground(() -> {
        // background thread
        return networkClient.fetchTitle();
    })
    .onPreExecute(() -> progressBar.setVisibility(View.VISIBLE))
    .onPostExecute(title -> {
        progressBar.setVisibility(View.GONE);
        textView.setText(title);
    })
    .onProgressUpdate(values -> progressBar.setProgress(values[0]))
    .onError(e -> Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show())
    .onCancelled(() -> progressBar.setVisibility(View.GONE))
    .setTimeout(20, TimeUnit.SECONDS)
    .setPriority(TaskPriority.HIGH)
    .execute();
```

### Classic subclass (easy AsyncTask migration)

```java
new NexaTask<Void, Integer, String>(this) {
    @Override
    protected void onPreExecute() {
        progressBar.setVisibility(View.VISIBLE);
    }

    @Override
    protected String doInBackground(Void... params) throws Exception {
        for (int i = 0; i <= 100; i += 10) {
            if (isCancelled()) return null;
            publishProgress(i);
            Thread.sleep(50);
        }
        return "Done";
    }

    @Override
    protected void onProgressUpdate(Integer... values) {
        progressBar.setProgress(values[0]);
    }

    @Override
    protected void onPostExecute(String result) {
        progressBar.setVisibility(View.GONE);
        textView.setText(result);
    }

    @Override
    protected void onError(Exception e) {
        Toast.makeText(MainActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
    }
}.execute();
```

---

## Init (recommended)

In your `Application` class so TaskManager can observe process lifecycle:

```java
public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        TaskManager.getInstance().init(this);
    }
}
```

```xml
<!-- AndroidManifest.xml -->
<application android:name=".App" ... >
```

---

## Lifecycle & split-screen safety

Always pass a **host** (`Activity`, `Fragment`, or host object supported by the library):

```java
NexaTask.run(this, () -> heavyWork()).onSuccess(this::updateUi).execute();
```

| Event | Behavior |
|-------|----------|
| Activity/Fragment **destroyed** | UI callbacks **skipped** — no crash |
| Split-screen / multi-window | Same host checks — safe |
| Pause / Stop | Task **keeps running** (like AsyncTask) |
| Explicit `cancel(true)` | Cooperative cancel + `onCancelled` |

---

## Logging

```java
TaskLogger.setDebugEnabled(true);
TaskLogger.setMinLogLevel(TaskLogger.LogLevel.DEBUG);
```

Filter Logcat: tag **`NexaTask`**.

---

## Architecture

```text
com.nexusteam.nexatask/
├── NexaTask.java              # public API + lambda builders
├── api/                       # TaskListener, ProgressListener, TaskCallbacks
├── core/                      # TaskExecutors, UiDispatcher, HostChecker
├── model/                     # TaskStatus, TaskPriority
├── util/ · utils/             # TaskLogger
├── manager/                   # TaskManager (lifecycle registry)
└── lambda/                    # BackgroundWork, PostCallback, ErrorCallback, …
```

| Layer | Role |
|-------|------|
| **api** | Public listener interfaces |
| **core** | Thread pool, main-thread dispatch, host alive checks |
| **model** | Status & priority enums |
| **manager** | Register / cancel tasks per host |
| **lambda** | Functional interfaces for builders |

---

## API cheat sheet

| Method | Thread | Notes |
|--------|--------|-------|
| `doInBackground` | Background | Required work |
| `onPreExecute` | Main | Before background |
| `onPostExecute` | Main | Success result |
| `onProgressUpdate` | Main | From `publishProgress` |
| `onError` | Main | Exceptions from background |
| `onCancelled` | Main | After cancel |
| `setTimeout` | — | Auto-cancel after duration |
| `setPriority` | — | `LOW` / `NORMAL` / `HIGH` |
| `cancel(mayInterrupt)` | — | Cooperative cancel |

---

## Requirements

| Item | Value |
|------|--------|
| Language | **Java 8+** (no Kotlin required) |
| Min SDK | **21** |
| Compile SDK | 33+ recommended |
| Version | **v1.1.3** |
| Distribution | JitPack AAR |

---

## Migration from AsyncTask

1. Change `extends AsyncTask<…>` → `extends NexaTask<…>` and pass `this` in constructor.
2. Or rewrite with `NexaTask.lambda(this).doInBackground(…).onPostExecute(…).execute()`.
3. Move `catch` logic into `onError` if you want clean separation.

---

## Sample app

Minimal demo that runs a background task, shows a spinner, handles success/error, and survives rotation / split-screen:

→ [samples/demo-app/](samples/demo-app/)

---

## Links

- **JitPack:** [jitpack.io/#NexusTeamOfficial/NexaTask](https://jitpack.io/#NexusTeamOfficial/NexaTask)
- **Releases:** [github.com/NexusTeamOfficial/NexaTask/releases](https://github.com/NexusTeamOfficial/NexaTask/releases)
- **Issues:** [github.com/NexusTeamOfficial/NexaTask/issues](https://github.com/NexusTeamOfficial/NexaTask/issues)

---

## License

```text
MIT License
Copyright (c) NexusTeam Official
```

Free for commercial and personal use.

---

<p align="center">
  <b>Star the repo</b> if NexaTask saves you from another AsyncTask crash.
  <br/>
  Built with Java · Shipped via JitPack · Made for Android
</p>
