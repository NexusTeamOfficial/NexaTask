package com.nexusteam.nexatask.api;

import androidx.annotation.Nullable;

/** Progress updates published from background (delivered on main thread). */
public interface ProgressListener<T> {
    void onProgress(@Nullable T... progress);
}
