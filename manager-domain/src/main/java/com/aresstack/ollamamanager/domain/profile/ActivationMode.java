package com.aresstack.ollamamanager.domain.profile;

/**
 * How a profile can be brought into effect on the current server.
 */
public enum ActivationMode {
    /** Server settings already match; only per-request options change. */
    REQUEST_OPTIONS_ONLY,
    /** The owned server must be restarted after the user confirms. */
    RESTART_OWNED_SERVER,
    /** The server is external; server settings cannot be applied automatically. */
    CANNOT_APPLY_TO_EXTERNAL,
    /** No server runs; settings apply on the next start. */
    APPLY_ON_NEXT_START
}
