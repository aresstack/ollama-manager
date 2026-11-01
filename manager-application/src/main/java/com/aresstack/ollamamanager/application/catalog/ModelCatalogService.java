package com.aresstack.ollamamanager.application.catalog;

import com.aresstack.ollamamanager.application.common.CancellationToken;
import com.aresstack.ollamamanager.application.common.ErrorKind;
import com.aresstack.ollamamanager.application.common.OperationException;
import com.aresstack.ollamamanager.application.common.ProgressListener;
import com.aresstack.ollamamanager.application.common.TransferProgress;
import com.aresstack.ollamamanager.application.model.ModelService;
import com.aresstack.ollamamanager.application.port.ModelCatalogPort;
import com.aresstack.ollamamanager.domain.server.Endpoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Find models in the Ollama library or on Hugging Face and install a chosen variant. Installation is always
 * {@link ModelService#pull} on the running server, so it downloads into that server's model directory and works
 * the same for owned and existing installations.
 */
public final class ModelCatalogService {

    private final Map<CatalogSource, ModelCatalogPort> catalogs =
        new EnumMap<CatalogSource, ModelCatalogPort>(CatalogSource.class);
    private final ModelService models;

    public ModelCatalogService(ModelService models, List<? extends ModelCatalogPort> ports) {
        if (models == null || ports == null) {
            throw new IllegalArgumentException("models and ports must not be null");
        }
        this.models = models;
        for (ModelCatalogPort port : ports) {
            catalogs.put(port.source(), port);
        }
    }

    public List<CatalogSource> sources() {
        return Collections.unmodifiableList(new ArrayList<CatalogSource>(catalogs.keySet()));
    }

    public List<CatalogModel> search(CatalogSource source, String query, CancellationToken cancellation)
        throws OperationException {
        String trimmed = query == null ? "" : query.trim();
        if (trimmed.isEmpty()) {
            throw new OperationException(ErrorKind.INVALID_CONFIGURATION, "enter a search term");
        }
        return port(source).search(trimmed, cancellation);
    }

    public List<CatalogVariant> variants(CatalogModel model, CancellationToken cancellation)
        throws OperationException {
        return port(model.source()).variants(model.id(), cancellation);
    }

    /** Download and register the variant on the running server. */
    public void install(Endpoint endpoint, CatalogVariant variant, ProgressListener<TransferProgress> listener,
                        CancellationToken cancellation) throws OperationException {
        models.pull(endpoint, variant.pullName(), listener, cancellation);
    }

    private ModelCatalogPort port(CatalogSource source) throws OperationException {
        ModelCatalogPort port = catalogs.get(source);
        if (port == null) {
            throw new OperationException(ErrorKind.INVALID_STATE, source + " is not available in this build");
        }
        return port;
    }
}
