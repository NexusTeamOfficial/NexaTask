# NexaTask demo app

Minimal Android sample that shows:

- Background work via `NexaTask.lambda`
- Spinner while running
- Success / error on the main thread
- **No crash** after rotation or when the host is destroyed (lifecycle-safe UI)

## How to run

This folder is a **standalone** mini project. Open it in Android Studio, or copy `MainActivity` + layout into your own app and depend on NexaTask:

```gradle
implementation 'com.github.NexusTeamOfficial:NexaTask:v1.1.3'
```

1. Tap **Run task** → spinner appears, work runs on a background thread.
2. Result (or error) updates the TextView.
3. Rotate the device while the task is running — UI callbacks are skipped if the old Activity is dead; start a new task on the new Activity if needed.

## Key idea

Always pass the Activity as **host** (`this`). NexaTask checks host aliveness before posting UI updates.
