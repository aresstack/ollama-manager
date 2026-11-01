package com.aresstack.ollamamanager.application.catalog;

import java.util.List;

/** One installable variant of a catalog model, identified by the exact name passed to the server's pull. */
public final class CatalogVariant {

    private final String pullName;
    private final String size;
    private final String context;
    private final List<String> inputs;
    private final boolean recommended;

    public CatalogVariant(String pullName, String size, String context, List<String> inputs, boolean recommended) {
        if (pullName == null || pullName.trim().isEmpty()) {
            throw new IllegalArgumentException("pullName is required");
        }
        this.pullName = pullName.trim();
        this.size = CatalogModel.text(size);
        this.context = CatalogModel.text(context);
        this.inputs = CatalogModel.copy(inputs);
        this.recommended = recommended;
    }

    /** For example {@code qwen3:8b} or {@code hf.co/unsloth/Qwen3-8B-GGUF:Q4_K_M}. */
    public String pullName() {
        return pullName;
    }

    /** Download size as text, for example {@code 5.2GB}; empty when unknown. */
    public String size() {
        return size;
    }

    public String context() {
        return context;
    }

    public List<String> inputs() {
        return inputs;
    }

    /** The source's default ({@code latest} tag) or a common balanced quantization. */
    public boolean recommended() {
        return recommended;
    }
}
