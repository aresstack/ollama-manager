package com.aresstack.ollamamanager.application.port;

import com.aresstack.ollamamanager.application.catalog.CatalogModel;
import com.aresstack.ollamamanager.application.catalog.CatalogSource;
import com.aresstack.ollamamanager.application.catalog.CatalogVariant;
import com.aresstack.ollamamanager.application.common.CancellationToken;
import com.aresstack.ollamamanager.application.common.OperationException;

import java.util.List;

/** Searches one online model catalog. Implementations only read; installing is the server's pull. */
public interface ModelCatalogPort {

    CatalogSource source();

    List<CatalogModel> search(String query, CancellationToken cancellation) throws OperationException;

    /** Installable variants of {@code modelId}; fails with {@code NOT_FOUND} if the model does not exist. */
    List<CatalogVariant> variants(String modelId, CancellationToken cancellation) throws OperationException;
}
