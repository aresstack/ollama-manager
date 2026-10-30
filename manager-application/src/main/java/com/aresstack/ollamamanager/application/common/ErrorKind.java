package com.aresstack.ollamamanager.application.common;

/**
 * Failure categories every adapter maps its errors to, so the UI can show actionable messages.
 */
public enum ErrorKind {
    CANCELLED,
    TIMEOUT,
    CONNECTION_FAILED,
    HTTP_ERROR,
    NOT_FOUND,
    SERVICE_UNAVAILABLE,
    PORT_IN_USE,
    NOT_OLLAMA,
    NOT_OWNED,
    INVALID_STATE,
    PROCESS_FAILED,
    CHECKSUM_MISMATCH,
    INVALID_ARCHIVE,
    INSUFFICIENT_DISK,
    UNSUPPORTED_PLATFORM,
    INVALID_CONFIGURATION,
    IO
}
