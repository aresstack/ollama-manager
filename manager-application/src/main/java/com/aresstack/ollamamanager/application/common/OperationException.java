package com.aresstack.ollamamanager.application.common;

/**
 * Checked failure raised by every port. Adapters must not leak library-specific exceptions.
 */
public class OperationException extends Exception {

    private static final long serialVersionUID = 1L;

    private final ErrorKind kind;

    public OperationException(ErrorKind kind, String message) {
        this(kind, message, null);
    }

    public OperationException(ErrorKind kind, String message, Throwable cause) {
        super(message, cause);
        if (kind == null) {
            throw new IllegalArgumentException("kind must not be null");
        }
        this.kind = kind;
    }

    public ErrorKind kind() {
        return kind;
    }
}
