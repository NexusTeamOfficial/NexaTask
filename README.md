# NexaTask

[![Version](https://img.shields.io/badge/version-1.1.0-blue.svg)](#)
[![JitPack](https://jitpack.io/v/NexusTeamOfficial/NexaTask.svg)](https://jitpack.io/#NexusTeamOfficial/NexaTask)

Powerful **AsyncTask alternative** for Android with lambda API and safe UI delivery in split-screen / multi-window.

## What’s new in 1.1.0

| Fix / feature | Detail |
|---------------|--------|
| **Split-screen crash** | UI callbacks skipped when host Activity is destroyed/finishing |
| **Background safe** | Tasks are **not** cancelled on pause/stop — only on **destroy** |
| **PriorityBlockingQueue crash** | Replaced with `LinkedBlockingQueue` |
| **Lambda API** | `doInBackground` / `onPostExecute` / … as lambdas |
| **Throwable** | Worker catches `Throwable`, not only `Exception` |
| **UI exceptions swallowed** | Callback crashes never kill the process |

## Install (JitPack)

```gradle
// settings.gradle
maven { url 'https://jitpack.io' }

// app/build.gradle
implementation 'com.github.NexusTeamOfficial:NexaTask:1.1.0'
```

> Tag `1.1.0` on latest main, then wait for JitPack green build.

## Classic (AsyncTask-style)

```java
new NexaTask<Void, Integer, String>(this) {
    @Override
    protected String doInBackground(Void... params) throws Exception {
        // background work
        publishProgress(50);
        return "ok";
    }

    @Override
    protected void onPostExecute(String result) {
        // UI — only runs if Activity still alive
    }

    @Override
    protected void onProgressUpdate(Integer... values) { }

    @Override
    protected void onError(Throwable error) { }
}.setTimeout(30, TimeUnit.SECONDS)
 .setMaxRetries(2)
 .execute();
```

## Lambda API (same method names)

```java
NexaTask.<Void, Integer, String>lambda(this)
    .doInBackground(() -> {
        // background
        return "result";
    })
    .onPreExecute(() -> { /* UI */ })
    .onPostExecute(result -> { /* UI */ })
    .onProgressUpdate(values -> { })
    .onError(e -> { })
    .onCancelled(() -> { })
    .setTimeout(15, TimeUnit.SECONDS)
    .execute();
```

### Short form

```java
NexaTask.run(this, () -> loadFromNetwork())
    .onSuccess(data -> updateUi(data))
    .onError(e -> showError(e))
    .execute();
```

## Init (optional, recommended)

```java
public class App extends Application {
    @Override public void onCreate() {
        super.onCreate();
        TaskManager.getInstance().init(this); // cancel tasks only when Activity destroyed
    }
}
```

## Crash you reported

When the app was in **split-screen / background** and a task finished, `onPostExecute` could touch views on a dead Activity → crash.

**Fix:** pass `this` (Activity) as tag / host. Library checks `isFinishing()` / `isDestroyed()` and **skips UI** instead of crashing. Work still runs in background; only UI is gated.

```java
// Always bind host when updating UI:
NexaTask.lambda(this)
    .doInBackground(() -> work())
    .onPostExecute(r -> textView.setText(r))  // safe
    .execute();
```

## License

MIT
