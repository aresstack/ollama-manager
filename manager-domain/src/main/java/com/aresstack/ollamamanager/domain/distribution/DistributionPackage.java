package com.aresstack.ollamamanager.domain.distribution;

import java.util.Set;

/**
 * Official Windows x64 Ollama ZIP assets. Add-ons overlay a base distribution of the same version.
 */
public enum DistributionPackage {
    BASE("ollama-windows-amd64.zip"),
    ROCM("ollama-windows-amd64-rocm.zip"),
    MLX("ollama-windows-amd64-mlx.zip");

    private final String assetName;

    DistributionPackage(String assetName) {
        this.assetName = assetName;
    }

    public String assetName() {
        return assetName;
    }

    public boolean isAddOn() {
        return this != BASE;
    }

    /**
     * Reject selections without the base package.
     */
    public static void requireValidSelection(Set<DistributionPackage> selection) {
        if (selection == null || !selection.contains(BASE)) {
            throw new IllegalArgumentException("the base package is required: " + selection);
        }
    }
}
