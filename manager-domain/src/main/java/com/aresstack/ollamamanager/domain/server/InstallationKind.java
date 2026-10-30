package com.aresstack.ollamamanager.domain.server;

/**
 * Origin of an Ollama server and therefore what the manager may do with it.
 */
public enum InstallationKind {
    /** Portable distribution downloaded and started by the manager. */
    MANAGED_PORTABLE(true),
    /** Regular installation whose binary the manager uses to start its own server. */
    MANAGED_INSTALLED(true),
    /** Server that was already running; the manager only observes it. */
    EXTERNAL_ATTACHED(false);

    private final boolean startableByManager;

    InstallationKind(boolean startableByManager) {
        this.startableByManager = startableByManager;
    }

    public boolean isStartableByManager() {
        return startableByManager;
    }
}
