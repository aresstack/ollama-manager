package com.aresstack.ollamamanager.application.port;

import com.aresstack.ollamamanager.application.common.CancellationToken;
import com.aresstack.ollamamanager.application.common.OperationException;
import com.aresstack.ollamamanager.application.common.ProgressListener;
import com.aresstack.ollamamanager.application.common.TransferProgress;
import com.aresstack.ollamamanager.domain.model.InstalledModel;
import com.aresstack.ollamamanager.domain.model.LoadedModel;
import com.aresstack.ollamamanager.domain.model.ModelDetails;
import com.aresstack.ollamamanager.domain.model.ModelOptions;
import com.aresstack.ollamamanager.domain.server.Endpoint;

import java.util.List;

/**
 * Ollama REST client. Works against owned and external servers alike. Implemented in issue #6.
 * HTTP 404 maps to {@code NOT_FOUND}, 503 to {@code SERVICE_UNAVAILABLE}, a non-Ollama answer to
 * {@code NOT_OLLAMA}.
 */
public interface OllamaApiPort {

    /** Return the version from {@code GET /api/version}. */
    String version(Endpoint endpoint, CancellationToken cancellation) throws OperationException;

    /**
     * Return models on disk from {@code GET /api/tags}. This is the source of exact identities: each entry
     * carries the digest reported by Ollama, never an invented one.
     */
    List<InstalledModel> installedModels(Endpoint endpoint, CancellationToken cancellation) throws OperationException;

    /** Return models in memory from {@code GET /api/ps}. */
    List<LoadedModel> loadedModels(Endpoint endpoint, CancellationToken cancellation) throws OperationException;

    /** Return descriptive metadata from {@code POST /api/show}; it contains no digest. */
    ModelDetails show(Endpoint endpoint, String modelName, CancellationToken cancellation) throws OperationException;

    /** Pull a model, streaming NDJSON progress to {@code listener}. */
    void pull(Endpoint endpoint, String modelName, ProgressListener<TransferProgress> listener,
              CancellationToken cancellation) throws OperationException;

    /** Load a model via an empty {@code /api/generate} with {@code keep_alive} and the given options. */
    void preload(Endpoint endpoint, String modelName, ModelOptions options, String keepAlive,
                 CancellationToken cancellation) throws OperationException;

    /** Unload a model via {@code /api/generate} with {@code keep_alive: 0}. */
    void unload(Endpoint endpoint, String modelName, CancellationToken cancellation) throws OperationException;

    /** Delete a model from disk. Callers must have obtained user confirmation first. */
    void delete(Endpoint endpoint, String modelName, CancellationToken cancellation) throws OperationException;
}
