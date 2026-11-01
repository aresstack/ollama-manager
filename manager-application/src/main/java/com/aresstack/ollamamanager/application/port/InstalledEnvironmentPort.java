package com.aresstack.ollamamanager.application.port;

import com.aresstack.ollamamanager.application.common.CancellationToken;
import com.aresstack.ollamamanager.application.common.OperationException;

import java.util.Map;

/**
 * Reads the {@code OLLAMA_*} variables an existing Ollama installation is configured with, for example the
 * Windows user and system environment or a systemd service.
 */
public interface InstalledEnvironmentPort {

    /**
     * @return variable name (upper case) to value, with the value that wins for an Ollama server started on this
     *         system; empty when nothing is configured
     */
    Map<String, String> ollamaVariables(CancellationToken cancellation) throws OperationException;

    /** @return short description of where the values come from, for example "Windows user environment" */
    String sourceDescription();
}
