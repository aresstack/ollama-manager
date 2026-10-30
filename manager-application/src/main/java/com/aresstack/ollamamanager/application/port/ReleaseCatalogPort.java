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

    /**
     * Return the release with this tag (for example {@code v0.12.6}), with SHA-256 values filled in.
     * Fails with {@code NOT_FOUND} if it does not exist; pre-releases and drafts are rejected the same way.
     */
    OllamaRelease findRelease(String version, CancellationToken cancellation) throws OperationException;
}
