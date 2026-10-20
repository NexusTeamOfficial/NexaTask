package com.nexusteam.nexatask.lambda;

import androidx.annotation.Nullable;

/**
 * Progress UI callback (onProgressUpdate).
 */
@FunctionalInterface
public interface ProgressCallback<P> {
    void onProgress(@Nullable P... values);
}
