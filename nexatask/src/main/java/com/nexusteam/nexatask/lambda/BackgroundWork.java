package com.nexusteam.nexatask.lambda;

/**
 * Background work for lambda API — runs on worker thread.
 */
@FunctionalInterface
public interface BackgroundWork<R> {
    R run() throws Exception;
}
