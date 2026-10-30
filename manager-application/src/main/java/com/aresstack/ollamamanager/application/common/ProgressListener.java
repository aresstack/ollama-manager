package com.aresstack.ollamamanager.application.common;

/**
 * Receives progress updates from a background thread. Implementations must not block and must
 * dispatch UI work to the Swing event dispatch thread themselves.
 *
 * @param <T> progress value type
 */
public interface ProgressListener<T> {

    void onProgress(T progress);

    static <T> ProgressListener<T> ignoring() {
        return progress -> { };
    }
}
