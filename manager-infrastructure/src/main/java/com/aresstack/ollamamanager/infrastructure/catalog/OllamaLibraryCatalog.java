package com.aresstack.ollamamanager.infrastructure.catalog;

import com.aresstack.ollamamanager.application.catalog.CatalogModel;
import com.aresstack.ollamamanager.application.catalog.CatalogSource;
import com.aresstack.ollamamanager.application.catalog.CatalogVariant;
import com.aresstack.ollamamanager.application.common.CancellationToken;
import com.aresstack.ollamamanager.application.common.ErrorKind;
import com.aresstack.ollamamanager.application.common.OperationException;
import com.aresstack.ollamamanager.application.port.ModelCatalogPort;
import com.aresstack.ollamamanager.infrastructure.distribution.HttpTransport;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Searches ollama.com/library. The resulting pull names ({@code name:tag}) are pulled by the running server. */
public final class OllamaLibraryCatalog implements ModelCatalogPort {

    public static final String DEFAULT_BASE = "https://ollama.com";
    private static final Pattern BASE_NAME = Pattern.compile("[a-z0-9][a-z0-9._-]*");
    private static final int MAX_HTML_BYTES = 8 * 1024 * 1024;
    private static final Map<String, String> HEADERS = Collections.singletonMap("Accept", "text/html");

    private final HttpTransport http;
    private final String base;
    private final OllamaLibraryHtmlParser parser = new OllamaLibraryHtmlParser();

    public OllamaLibraryCatalog(HttpTransport http, String base) {
        this.http = http;
        this.base = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    public static OllamaLibraryCatalog defaults() {
        return new OllamaLibraryCatalog(HttpTransport.defaults(), DEFAULT_BASE);
    }

    @Override
    public CatalogSource source() {
        return CatalogSource.OLLAMA_LIBRARY;
    }

    @Override
    public List<CatalogModel> search(String query, CancellationToken cancellation) throws OperationException {
        String html = http.getText(base + "/search?q=" + Urls.encode(query), HEADERS, MAX_HTML_BYTES, cancellation);
        return parser.parseSearchResults(html);
    }

    @Override
    public List<CatalogVariant> variants(String modelId, CancellationToken cancellation) throws OperationException {
        String name = modelId == null ? "" : modelId.trim();
        if (!BASE_NAME.matcher(name).matches()) {
            throw new OperationException(ErrorKind.INVALID_CONFIGURATION, "invalid Ollama library name: " + modelId);
        }
        String html = http.getText(base + "/library/" + name + "/tags", HEADERS, MAX_HTML_BYTES, cancellation);
        return parser.parseVariants(name, html);
    }
}
