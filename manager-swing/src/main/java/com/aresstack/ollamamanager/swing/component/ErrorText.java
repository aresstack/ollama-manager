package com.aresstack.ollamamanager.swing.component;

import com.aresstack.ollamamanager.application.common.ErrorKind;
import com.aresstack.ollamamanager.application.common.OperationException;

/**
 * Turns exceptions into one consistent, actionable user message.
 */
public final class ErrorText {

    private ErrorText() {
    }

    /** Return a short headline plus the technical detail. */
    public static String describe(Throwable error) {
        if (error == null) {
            return "Unknown error.";
        }
        String detail = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        if (error instanceof OperationException) {
            return headline(((OperationException) error).kind()) + " " + detail;
        }
        return "Unexpected error: " + detail;
    }

    static String headline(ErrorKind kind) {
        switch (kind) {
            case CANCELLED:
                return "The operation was cancelled.";
            case TIMEOUT:
                return "The operation timed out.";
            case CONNECTION_FAILED:
                return "The Ollama server could not be reached.";
            case HTTP_ERROR:
                return "The Ollama server rejected the request.";
            case NOT_FOUND:
                return "Not found.";
            case SERVICE_UNAVAILABLE:
                return "The service is currently unavailable.";
            case PORT_IN_USE:
                return "The port is already in use.";
            case NOT_OLLAMA:
                return "The endpoint does not answer like an Ollama server.";
            case NOT_OWNED:
                return "This server was not started by Ollama Manager and cannot be controlled.";
            case INVALID_STATE:
                return "The operation is not possible right now.";
            case PROCESS_FAILED:
                return "The Ollama process failed.";
            case CHECKSUM_MISMATCH:
                return "The download is damaged (checksum mismatch).";
            case INVALID_ARCHIVE:
                return "The downloaded archive is invalid.";
            case INSUFFICIENT_DISK:
                return "Not enough disk space.";
            case UNSUPPORTED_PLATFORM:
                return "This platform is not supported.";
            case INVALID_CONFIGURATION:
                return "The configuration is invalid.";
            case IO:
            default:
                return "A file or I/O error occurred.";
        }
    }
}
