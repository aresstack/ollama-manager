package com.aresstack.ollamamanager.application.benchmark;

import com.aresstack.ollamamanager.application.profile.ProfileEnvironment;
import com.aresstack.ollamamanager.domain.hardware.GpuFingerprint;
import com.aresstack.ollamamanager.domain.model.ModelIdentity;

/**
 * What a benchmark run was measured on: the GPU, the server version and the exact model. A profile saved from
 * the run is scoped to these, never to whatever runs when the profile is saved. Each value may be {@code null}
 * when it could not be determined at run time.
 */
public final class BenchmarkEnvironment {

    private final GpuFingerprint gpuFingerprint;
    private final String ollamaVersion;
    private final ModelIdentity model;

    public BenchmarkEnvironment(GpuFingerprint gpuFingerprint, String ollamaVersion, ModelIdentity model) {
        this.gpuFingerprint = gpuFingerprint;
        this.ollamaVersion = ollamaVersion == null || ollamaVersion.trim().isEmpty() ? null : ollamaVersion;
        this.model = model;
    }

    /** Take the environment of a run of {@code modelName} from a snapshot captured during the run. */
    public static BenchmarkEnvironment from(ProfileEnvironment.Snapshot snapshot, String modelName) {
        ModelIdentity model = ModelIdentity.named(modelName);
        if (snapshot.installedModels() != null) {
            for (ModelIdentity installed : snapshot.installedModels()) {
                if (installed.name().equalsIgnoreCase(model.name())) {
                    model = installed;
                }
            }
        }
        return new BenchmarkEnvironment(snapshot.gpuFingerprint(), snapshot.ollamaVersion(), model);
    }

    public GpuFingerprint gpuFingerprint() {
        return gpuFingerprint;
    }

    public String ollamaVersion() {
        return ollamaVersion;
    }

    public ModelIdentity model() {
        return model;
    }

    /** Whether every value a profile scope needs is known. */
    public boolean isComplete() {
        return gpuFingerprint != null && ollamaVersion != null && model != null;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof BenchmarkEnvironment)) {
            return false;
        }
        BenchmarkEnvironment that = (BenchmarkEnvironment) other;
        return equal(gpuFingerprint, that.gpuFingerprint) && equal(ollamaVersion, that.ollamaVersion)
            && equal(model, that.model);
    }

    @Override
    public int hashCode() {
        int result = gpuFingerprint == null ? 0 : gpuFingerprint.hashCode();
        result = 31 * result + (ollamaVersion == null ? 0 : ollamaVersion.hashCode());
        return 31 * result + (model == null ? 0 : model.hashCode());
    }

    private static boolean equal(Object a, Object b) {
        return a == null ? b == null : a.equals(b);
    }

    @Override
    public String toString() {
        return "BenchmarkEnvironment{gpu=" + gpuFingerprint + ", ollama=" + ollamaVersion + ", model=" + model + '}';
    }
}
