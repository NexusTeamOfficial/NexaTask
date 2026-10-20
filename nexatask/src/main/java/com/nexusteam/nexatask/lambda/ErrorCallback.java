package com.nexusteam.nexatask.lambda;

import androidx.annotation.NonNull;

/**
 * Error UI callback (onError).
 */
@FunctionalInterface
public interface ErrorCallback {
    void onError(@NonNull Throwable error);
}
