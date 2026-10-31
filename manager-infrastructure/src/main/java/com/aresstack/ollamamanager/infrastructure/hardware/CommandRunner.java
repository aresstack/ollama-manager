package com.aresstack.ollamamanager.infrastructure.hardware;

import com.aresstack.ollamamanager.application.common.CancellationToken;
import com.aresstack.ollamamanager.application.common.OperationException;

import java.util.List;

/**
 * Runs a short diagnostic command. Throws {@code TIMEOUT}, {@code CANCELLED} or {@code IO} on failure;
 * returns {@link CommandResult#notFound()} if the executable does not exist.
 */
public interface CommandRunner {

    CommandResult run(List<String> command, long timeoutMillis, CancellationToken cancellation)
        throws OperationException;
}
