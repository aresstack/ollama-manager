package com.aresstack.ollamamanager.application.port;

import com.aresstack.ollamamanager.application.common.CancellationToken;
import com.aresstack.ollamamanager.application.common.OperationException;
import com.aresstack.ollamamanager.domain.distribution.OllamaRelease;

/**
 * Reads official Ollama releases from GitHub. Implemented in issue #4.
 */
public interface ReleaseCatalogPort {

    /**
     * Return the latest stable release with SHA-256 values filled in from its {@code sha256sum.txt}.
     */
    OllamaRelease latestStable(CancellationToken cancellation) throws OperationException;
}
