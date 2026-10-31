package com.aresstack.ollamamanager.application.hardware;

import com.aresstack.ollamamanager.domain.model.LoadedModel;

import java.util.List;

/**
 * Runtime evidence of GPU use, derived only from VRAM that Ollama reports in {@code /api/ps}.
 */
public enum GpuUsageEvidence {
    NO_MODEL_LOADED,
    CPU_ONLY,
    PARTIAL_OFFLOAD,
    FULL_GPU;

    public static GpuUsageEvidence fromLoadedModels(List<LoadedModel> loaded) {
        if (loaded == null || loaded.isEmpty()) {
            return NO_MODEL_LOADED;
        }
        long size = 0;
        long vram = 0;
        for (LoadedModel model : loaded) {
            size += model.sizeBytes();
            vram += Math.min(model.vramBytes(), model.sizeBytes());
        }
        if (vram == 0) {
            return CPU_ONLY;
        }
        return vram >= size ? FULL_GPU : PARTIAL_OFFLOAD;
    }
}
