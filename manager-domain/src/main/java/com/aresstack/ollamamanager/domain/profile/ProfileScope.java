package com.aresstack.ollamamanager.domain.profile;

import com.aresstack.ollamamanager.domain.common.Preconditions;
import com.aresstack.ollamamanager.domain.hardware.GpuFingerprint;
import com.aresstack.ollamamanager.domain.model.ModelIdentity;

/**
 * What a profile or measurement is valid for: exact model, GPU set, Ollama version and workload.
 */
public final class ProfileScope {

    private final ModelIdentity model;
    private final GpuFingerprint gpuFingerprint;
    private final String ollamaVersion;
    private final String workload;

    public ProfileScope(ModelIdentity model, GpuFingerprint gpuFingerprint, String ollamaVersion, String workload) {
        this.model = Preconditions.requireNonNull(model, "model");
        this.gpuFingerprint = Preconditions.requireNonNull(gpuFingerprint, "gpuFingerprint");
        this.ollamaVersion = Preconditions.requireNonBlank(ollamaVersion, "ollamaVersion");
        this.workload = Preconditions.requireNonBlank(workload, "workload");
    }

    public ModelIdentity model() {
        return model;
    }

    public GpuFingerprint gpuFingerprint() {
        return gpuFingerprint;
    }

    public String ollamaVersion() {
        return ollamaVersion;
    }

    /** Return the workload label such as {@code chat} or {@code batch-summaries}. */
    public String workload() {
        return workload;
    }

    /**
     * Return whether a result measured for this scope is still valid in {@code current}.
     */
    public boolean isValidFor(ProfileScope current) {
        return model.sameArtifactAs(current.model) && gpuFingerprint.equals(current.gpuFingerprint)
            && ollamaVersion.equals(current.ollamaVersion) && workload.equals(current.workload);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof ProfileScope)) {
            return false;
        }
        ProfileScope that = (ProfileScope) other;
        return model.equals(that.model) && gpuFingerprint.equals(that.gpuFingerprint)
            && ollamaVersion.equals(that.ollamaVersion) && workload.equals(that.workload);
    }

    @Override
    public int hashCode() {
        int result = model.hashCode();
        result = 31 * result + gpuFingerprint.hashCode();
        result = 31 * result + ollamaVersion.hashCode();
        return 31 * result + workload.hashCode();
    }

    @Override
    public String toString() {
        return "ProfileScope{" + model + ", " + gpuFingerprint + ", " + ollamaVersion + ", " + workload + "}";
    }
}
