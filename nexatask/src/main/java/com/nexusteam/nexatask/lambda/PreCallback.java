package com.nexusteam.nexatask.lambda;

/**
 * No-arg UI callback (onPreExecute / onCancelled / onTimeout).
 */
@FunctionalInterface
public interface PreCallback {
    void run();
}
