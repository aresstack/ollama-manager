package com.aresstack.ollamamanager.application.hardware;

import com.aresstack.ollamamanager.domain.hardware.GpuDescriptor;

/**
 * Documented compatibility of one detected GPU, with a human-readable reason.
 */
public final class GpuAssessment {

    private final GpuDescriptor gpu;
    private final GpuBackend backend;
    private final GpuSupport support;
    private final boolean overridden;
    private final String reason;

    public GpuAssessment(GpuDescriptor gpu, GpuBackend backend, GpuSupport support, boolean overridden,
                         String reason) {
        if (gpu == null || backend == null || support == null || reason == null) {
            throw new IllegalArgumentException("gpu, backend, support and reason must not be null");
        }
        this.gpu = gpu;
        this.backend = backend;
        this.support = support;
        this.overridden = overridden;
        this.reason = reason;
    }

    public GpuDescriptor gpu() {
        return gpu;
    }

    public GpuBackend backend() {
        return backend;
    }

    public GpuSupport support() {
        return support;
    }

    /** Return whether the user forced the support value manually. */
    public boolean isOverridden() {
        return overridden;
    }

    public String reason() {
        return reason;
    }

    @Override
    public String toString() {
        return gpu.name() + ": " + support + " (" + backend + ") - " + reason;
    }
}
