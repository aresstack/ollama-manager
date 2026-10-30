package com.aresstack.ollamamanager.domain.server;

/**
 * Lifecycle state of the server the manager is looking at.
 */
public enum ServerState {
    STOPPED,
    STARTING,
    RUNNING,
    STOPPING,
    FAILED
}
