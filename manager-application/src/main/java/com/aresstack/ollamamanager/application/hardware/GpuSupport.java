package com.aresstack.ollamamanager.application.hardware;

/**
 * Whether Ollama documents a GPU as usable. Presence of a device never implies {@link #SUPPORTED} at runtime.
 */
public enum GpuSupport {
    SUPPORTED,
    DRIVER_UPDATE_REQUIRED,
    UNSUPPORTED,
    UNKNOWN
}
