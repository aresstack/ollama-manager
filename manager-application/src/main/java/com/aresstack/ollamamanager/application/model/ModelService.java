package com.aresstack.ollamamanager.application.model;

import com.aresstack.ollamamanager.application.common.CancellationToken;
import com.aresstack.ollamamanager.application.common.ErrorKind;
import com.aresstack.ollamamanager.application.common.OperationException;
import com.aresstack.ollamamanager.application.common.ProgressListener;
import com.aresstack.ollamamanager.application.common.TransferProgress;
import com.aresstack.ollamamanager.application.port.OllamaApiPort;
import com.aresstack.ollamamanager.domain.model.InstalledModel;
import com.aresstack.ollamamanager.domain.model.LoadedModel;
import com.aresstack.ollamamanager.domain.model.ModelIdentity;
import com.aresstack.ollamamanager.domain.model.ModelOptions;
import com.aresstack.ollamamanager.domain.server.Endpoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Model use cases on top of {@link OllamaApiPort}: inventory, pull, preload with placement check, unload
 * and confirmed delete. Works for owned and external servers alike.
 */
public final class ModelService {

    private final OllamaApiPort api;

    public ModelService(OllamaApiPort api) {
        if (api == null) {
            throw new IllegalArgumentException("api must not be null");
        }
        this.api = api;
    }

    /**
     * Return installed models from {@code /api/tags} merged with loaded models from {@code /api/ps} by exact
     * name and digest. A loaded model whose digest differs from the one on disk appears as its own entry.
     */
    public List<ModelEntry> inventory(Endpoint endpoint, CancellationToken cancellation) throws OperationException {
        List<InstalledModel> installed = api.installedModels(endpoint, cancellation);
        List<LoadedModel> loaded = new ArrayList<LoadedModel>(api.loadedModels(endpoint, cancellation));
        List<ModelEntry> entries = new ArrayList<ModelEntry>();
        for (InstalledModel model : installed) {
            LoadedModel match = removeMatch(loaded, model.identity());
            entries.add(new ModelEntry(model, match));
        }
        for (LoadedModel model : loaded) {
            entries.add(new ModelEntry(null, model));
        }
        return Collections.unmodifiableList(entries);
    }

    public void pull(Endpoint endpoint, String modelName, ProgressListener<TransferProgress> listener,
                     CancellationToken cancellation) throws OperationException {
        api.pull(endpoint, modelName, listener, cancellation);
    }

    /**
     * Load a model with per-request {@code options} and return its placement as {@code /api/ps} reports it.
     * The options affect only this manager's request, never other clients.
     *
     * @throws OperationException {@code INVALID_STATE} if the model is not listed as loaded afterwards
     */
    public LoadedModel preloadAndVerify(Endpoint endpoint, String modelName, ModelOptions options, String keepAlive,
                                        CancellationToken cancellation) throws OperationException {
        api.preload(endpoint, modelName, options, keepAlive, cancellation);
        String name = ModelIdentity.named(modelName).name();
        for (LoadedModel model : api.loadedModels(endpoint, cancellation)) {
            if (model.identity().name().equals(name)) {
                return model;
            }
        }
        throw new OperationException(ErrorKind.INVALID_STATE, name + " is not reported as loaded after preload");
    }

    public void unload(Endpoint endpoint, String modelName, CancellationToken cancellation)
        throws OperationException {
        api.unload(endpoint, modelName, cancellation);
    }

    /**
     * Delete a model from disk. {@code confirmedModelName} must be the name the user confirmed in a dialog;
     * a missing or different confirmation is rejected before any request is sent.
     */
    public void delete(Endpoint endpoint, String modelName, String confirmedModelName,
                       CancellationToken cancellation) throws OperationException {
        String name = ModelIdentity.named(modelName).name();
        if (confirmedModelName == null || !ModelIdentity.named(confirmedModelName).name().equals(name)) {
            throw new OperationException(ErrorKind.INVALID_STATE, "deleting " + name + " requires user confirmation");
        }
        api.delete(endpoint, name, cancellation);
    }

    private static LoadedModel removeMatch(List<LoadedModel> loaded, ModelIdentity identity) {
        for (int i = 0; i < loaded.size(); i++) {
            ModelIdentity candidate = loaded.get(i).identity();
            boolean sameName = candidate.name().equals(identity.name());
            boolean digestsAgree = candidate.digest() == null || identity.digest() == null
                || candidate.digest().equals(identity.digest());
            if (sameName && digestsAgree) {
                return loaded.remove(i);
            }
        }
        return null;
    }
}
