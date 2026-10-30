package com.aresstack.ollamamanager.domain.model;

import com.aresstack.ollamamanager.domain.common.Preconditions;

/**
 * A model currently held in memory as reported by {@code GET /api/ps}. Placement is shown as reported.
 */
public final class LoadedModel {

    private final ModelIdentity identity;
    private final long sizeBytes;
    private final long vramBytes;
    private final Integer contextLength;
    private final String expiresAt;

    public LoadedModel(ModelIdentity identity, long sizeBytes, long vramBytes, Integer contextLength,
                       String expiresAt) {
        this.identity = Preconditions.requireNonNull(identity, "identity");
        this.sizeBytes = Preconditions.requireNonNegative(sizeBytes, "sizeBytes");
        this.vramBytes = Preconditions.requireNonNegative(vramBytes, "vramBytes");
        if (vramBytes > sizeBytes) {
            throw new IllegalArgumentException("vramBytes must not exceed sizeBytes");
        }
        this.contextLength = contextLength;
        this.expiresAt = expiresAt;
    }

    public ModelIdentity identity() {
        return identity;
    }

    public long sizeBytes() {
        return sizeBytes;
    }

    public long vramBytes() {
        return vramBytes;
    }

    /** Return the share of the model resident in GPU memory, between 0 and 1. */
    public double gpuShare() {
        return sizeBytes == 0 ? 0 : (double) vramBytes / sizeBytes;
    }

    /** Return the context length the model was loaded with, or {@code null} if not reported. */
    public Integer contextLength() {
        return contextLength;
    }

    /** Return the unload time as reported by Ollama, or {@code null}. */
    public String expiresAt() {
        return expiresAt;
    }
}
