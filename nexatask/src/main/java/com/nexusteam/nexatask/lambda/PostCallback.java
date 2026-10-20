package com.nexusteam.nexatask.lambda;

import androidx.annotation.Nullable;

/** Result UI callback (onPostExecute / onSuccess). */
@FunctionalInterface
public interface PostCallback<R> {
    void onResult(@Nullable R result);
}
