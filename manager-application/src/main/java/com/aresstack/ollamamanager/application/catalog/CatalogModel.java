package com.aresstack.ollamamanager.application.catalog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One search hit: a library model or a Hugging Face repository, before a variant is chosen. */
public final class CatalogModel {

    private final CatalogSource source;
    private final String id;
    private final String description;
    private final List<String> capabilities;
    private final List<String> sizes;
    private final String popularity;
    private final String updated;

    public CatalogModel(CatalogSource source, String id, String description, List<String> capabilities,
                        List<String> sizes, String popularity, String updated) {
        if (source == null || id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("source and id are required");
        }
        this.source = source;
        this.id = id.trim();
        this.description = text(description);
        this.capabilities = copy(capabilities);
        this.sizes = copy(sizes);
        this.popularity = text(popularity);
        this.updated = text(updated);
    }

    public CatalogSource source() {
        return source;
    }

    /** Library base name (for example {@code qwen3}) or Hugging Face repository id ({@code owner/name}). */
    public String id() {
        return id;
    }

    public String description() {
        return description;
    }

    public List<String> capabilities() {
        return capabilities;
    }

    /** Parameter sizes such as {@code 8b}; empty when the source does not tell. */
    public List<String> sizes() {
        return sizes;
    }

    /** Pulls or downloads as the source shows them, for example {@code 1.2M}. */
    public String popularity() {
        return popularity;
    }

    public String updated() {
        return updated;
    }

    static String text(String value) {
        return value == null ? "" : value.trim();
    }

    static List<String> copy(List<String> values) {
        return values == null ? Collections.<String>emptyList()
            : Collections.unmodifiableList(new ArrayList<String>(values));
    }
}
