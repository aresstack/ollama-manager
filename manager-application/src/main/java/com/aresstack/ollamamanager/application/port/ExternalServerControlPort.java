package com.aresstack.ollamamanager.application.port;

import com.aresstack.ollamamanager.application.common.CancellationToken;
import com.aresstack.ollamamanager.application.common.OperationException;
import com.aresstack.ollamamanager.domain.server.Endpoint;

/**
 * Finds and terminates an Ollama server that the manager did not start. Implemented per operating system; only
 * used after the user confirmed the stop or restart.
 */
public interface ExternalServerControlPort {

    /**
     * Find the process that listens on the endpoint's TCP port.
     *
     * @return the process, or {@code null} when no listener is found or the system does not reveal it
     */
    ExternalServerProcess find(Endpoint endpoint, CancellationToken cancellation) throws OperationException;

    /**
     * Terminate the process and, if {@link ExternalServerProcess#hasOllamaSupervisor()}, its supervising Ollama
     * process first, so that the server is not started again. Requests termination, waits up to the grace period
     * and then forces it. Returns once the processes are gone.
     */
    void terminate(ExternalServerProcess process, long gracePeriodMillis, CancellationToken cancellation)
        throws OperationException;
}
