package com.aresstack.ollamamanager.domain.model;

import com.aresstack.ollamamanager.domain.common.Preconditions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Descriptive model metadata from {@code POST /api/show}. Contains no digest: the digest comes only from
 * {@code /api/tags} or {@code /api/ps}. Unknown values are {@code null}.
 */
public final class ModelDetails {

    private final String name;
    private final String family;
    private final String parameterSize;
    private final String quantizationLevel;
    private final String format;
    private final Integer contextLength;
    private final List<String> capabilities;

    public ModelDetails(String name, String family, String parameterSize, String quantizationLevel, String format,
                        Integer contextLength) {
        this(name, family, parameterSize, quantizationLevel, format, contextLength, null);
    }

    public ModelDetails(String name, String family, String parameterSize, String quantizationLevel, String format,
                        Integer contextLength, List<String> capabilities) {
        this.capabilities = capabilities == null ? Collections.<String>emptyList()
            : Collections.unmodifiableList(new ArrayList<String>(capabilities));
        this.name = Preconditions.requireNonBlank(name, "name");
        this.family = family;
        this.parameterSize = parameterSize;
        this.quantizationLevel = quantizationLevel;
        this.format = format;
        this.contextLength = contextLength;
    }

    public String name() {
        return name;
    }

    public String family() {
        return family;
    }

    public String parameterSize() {
        return parameterSize;
    }

    public String quantizationLevel() {
        return quantizationLevel;
    }

    public String format() {
        return format;
    }

    /** Return the model's maximum context length as reported in {@code model_info}, or {@code null}. */
    public Integer contextLength() {
        return contextLength;
    }

    /** Return the capability tags reported by {@code /api/show} (completion, embedding, vision, ...); empty if none. */
    public List<String> capabilities() {
        return capabilities;
    }
}
