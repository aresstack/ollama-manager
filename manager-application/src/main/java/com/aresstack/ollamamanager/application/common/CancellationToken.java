package com.aresstack.ollamamanager.application.common;

import java.util.ArrayList;
import java.util.List;

/**
 * Cooperative cancellation shared between caller and port implementation.
 * <p>
 * Contract: long-running port methods check {@link #throwIfCancelled()} between units of work and
 * register a callback with {@link #onCancel(Runnable)} to abort blocking IO (for example by closing
 * a stream or destroying an owned process). They close the returned {@link Registration} in a
 * {@code finally} block, so a token reused across operations does not keep stale callbacks. After
 * cancellation the method throws {@link OperationCancelledException} and leaves no partial results behind.
 */
public final class CancellationToken {

    private final List<Registration> registrations = new ArrayList<Registration>();
    private boolean cancelled;

    /** Return a token that is never cancelled. */
    public static CancellationToken none() {
        return new CancellationToken();
    }

    /**
     * Cancel and run registered callbacks once. Every callback runs even if an earlier one fails; the first
     * failure is rethrown afterwards with later ones suppressed. Further calls have no effect.
     */
    public void cancel() {
        List<Registration> toRun;
        synchronized (this) {
            if (cancelled) {
                return;
            }
            cancelled = true;
            toRun = new ArrayList<Registration>(registrations);
            registrations.clear();
        }
        RuntimeException failure = null;
        for (Registration registration : toRun) {
            try {
                registration.callback.run();
            } catch (RuntimeException e) {
                if (failure == null) {
                    failure = e;
                } else if (failure != e) {
                    failure.addSuppressed(e);
                }
            }
        }
        if (failure != null) {
            throw failure;
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

    /**
     * Register a callback; it runs immediately if the token is already cancelled. Close the returned
     * registration once the guarded work has finished so the token releases the callback.
     */
    public Registration onCancel(Runnable callback) {
        Registration registration = new Registration(callback);
        synchronized (this) {
            if (!cancelled) {
                registrations.add(registration);
                return registration;
            }
        }
        callback.run();
        return registration;
    }

    /** Return the number of callbacks still registered. */
    synchronized int registeredCallbackCount() {
        return registrations.size();
    }

    private synchronized void release(Registration registration) {
        registrations.remove(registration);
    }

    /**
     * Handle to one registered callback. Closing it deregisters the callback; closing twice has no effect.
     */
    public final class Registration implements AutoCloseable {

        private final Runnable callback;

        private Registration(Runnable callback) {
            if (callback == null) {
                throw new IllegalArgumentException("callback must not be null");
            }
            this.callback = callback;
        }

        @Override
        public void close() {
            release(this);
        }
    }
}
