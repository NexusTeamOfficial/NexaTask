package com.nexusteam.nexatask.api;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Full lifecycle listener (main thread). */
public interface TaskListener<T> {
    void onPreExecute();
    void onPostExecute(@Nullable T result);
    void onCancelled();
    void onError(@NonNull Throwable error);
    void onTimeout();
}
