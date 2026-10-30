package com.aresstack.ollamamanager.domain.model;

import com.aresstack.ollamamanager.domain.common.Preconditions;

/**
 * A model present on disk as reported by {@code GET /api/tags}.
 */
public final class InstalledModel {

    private final ModelIdentity identity;
    private final long sizeBytes;
    private final String family;
    private final String parameterSize;
    private final String modifiedAt;

    public InstalledModel(ModelIdentity identity, long sizeBytes, String family, String parameterSize,
                          String modifiedAt) {
        this.identity = Preconditions.requireNonNull(identity, "identity");
        this.sizeBytes = Preconditions.requireNonNegative(sizeBytes, "sizeBytes");
        this.family = family;
        this.parameterSize = parameterSize;
        this.modifiedAt = modifiedAt;
    }

    public ModelIdentity identity() {
        return identity;
    }

    public long sizeBytes() {
        return sizeBytes;
    }

    /** Return the model family, or {@code null} if not reported. */
    public String family() {
        return family;
    }

    /** Return the parameter size such as {@code 8.0B}, or {@code null} if not reported. */
    public String parameterSize() {
        return parameterSize;
    }

    /** Return the modification timestamp as reported by Ollama, or {@code null}. */
    public String modifiedAt() {
        return modifiedAt;
    }

    @Override
    public String toString() {
        return "InstalledModel{" + identity + ", " + sizeBytes + " bytes}";
    }
}
