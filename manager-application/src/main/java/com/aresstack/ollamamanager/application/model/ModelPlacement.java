package com.aresstack.ollamamanager.application.model;

import com.aresstack.ollamamanager.domain.model.LoadedModel;

/**
 * Where a loaded model resides, derived only from the {@code size_vram} and {@code size} values that
 * {@code /api/ps} reports. Nothing is inferred from available VRAM.
 */
public enum ModelPlacement {
    /** All reported bytes are in GPU memory. */
    GPU,
    /** Part of the model is in GPU memory, the rest in system memory. */
    SPLIT,
    /** No reported bytes are in GPU memory. */
    CPU;

    public static ModelPlacement of(LoadedModel model) {
        if (model.vramBytes() == 0) {
            return CPU;
        }
        return model.vramBytes() >= model.sizeBytes() ? GPU : SPLIT;
    }
}
