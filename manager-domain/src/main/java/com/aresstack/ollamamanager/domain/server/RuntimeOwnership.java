package com.aresstack.ollamamanager.domain.server;

/**
 * Whether the manager owns a running server. Only owned servers may be stopped or restarted.
 */
public enum RuntimeOwnership {
    /** Started by this manager instance through a process handle it still holds. */
    OWNED,
    /** Found running; never stopped, killed or restarted by the manager. */
    EXTERNAL,
    /** No server is running. */
    NONE;

    public boolean permitsLifecycleControl() {
        return this == OWNED;
    }
}
