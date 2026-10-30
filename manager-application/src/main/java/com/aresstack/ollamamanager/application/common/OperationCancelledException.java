package com.aresstack.ollamamanager.application.common;

/**
 * Raised when an operation stops because its {@link CancellationToken} was cancelled.
 */
public final class OperationCancelledException extends OperationException {

    private static final long serialVersionUID = 1L;

    public OperationCancelledException(String message) {
        super(ErrorKind.CANCELLED, message);
    }
}
