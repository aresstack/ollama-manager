package com.aresstack.ollamamanager.application.common;

import java.util.ArrayList;
import java.util.List;

/**
 * Cooperative cancellation shared between caller and port implementation.
 * <p>
 * Contract: long-running port methods check {@link #throwIfCancelled()} between units of work and
 * register a callback with {@link #onCancel(Runnable)} to abort blocking IO (for example by closing
 * a stream or destroying an owned process). After cancellation the method throws
 * {@link OperationCancelledException} and leaves no partial results behind.
 */
public final class CancellationToken {

    private final List<Runnable> callbacks = new ArrayList<Runnable>();
    private boolean cancelled;

    /** Return a token that is never cancelled. */
    public static CancellationToken none() {
        return new CancellationToken();
    }

    /** Cancel and run registered callbacks once. Further calls have no effect. */
    public void cancel() {
        List<Runnable> toRun;
        synchronized (this) {
            if (cancelled) {
                return;
            }
            cancelled = true;
            toRun = new ArrayList<Runnable>(callbacks);
            callbacks.clear();
        }
        for (Runnable callback : toRun) {
            callback.run();
        }
    }

    public synchronized boolean isCancelled() {
        return cancelled;
    }

    public void throwIfCancelled() throws OperationCancelledException {
        if (isCancelled()) {
            throw new OperationCancelledException("operation cancelled");
        }
    }

    /** Register a callback; it runs immediately if the token is already cancelled. */
    public void onCancel(Runnable callback) {
        synchronized (this) {
            if (!cancelled) {
                callbacks.add(callback);
                return;
            }
        }
        callback.run();
    }
}
