package com.aresstack.ollamamanager.application.common;


import java.util.concurrent.locks.ReentrantLock;

/**
 * Serializes operations that change which Ollama binaries a manager-owned process uses: server start, stop,
 * restart, attaching an external server, and uninstalling a version. Create one instance per process and share
 * it between {@code ServerLifecycleService} and the installation use cases.
 *
 * <p>The lock never waits: a second operation fails fast with {@code INVALID_STATE} instead of blocking a
 * worker thread for the length of a server start. Downloads and extraction do not take it, so a long install
 * never blocks lifecycle operations; only the uninstall check and the deletion run under it, together.
 */
public final class LifecycleLock {

    /** Work that runs while the lock is held. */
    public interface Action<T> {
        T run() throws OperationException;
    }

    private final ReentrantLock lock = new ReentrantLock();

    /** Run {@code action} under the lock, or fail with {@code INVALID_STATE} if another operation holds it. */
    public <T> T run(String operation, Action<T> action) throws OperationException {
        if (!lock.tryLock()) {
            throw new OperationException(ErrorKind.INVALID_STATE,
                "cannot " + operation + " while another server or uninstall operation is in progress");
        }
        try {
            return action.run();
        } finally {
            lock.unlock();
        }
    }

    /** Return whether an operation currently holds the lock; for diagnostics and tests only. */
    public boolean isHeld() {
        return lock.isLocked();
    }
}
