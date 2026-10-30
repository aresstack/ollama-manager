package com.aresstack.ollamamanager.application.hardware;

/**
 * Ollama acceleration backend a GPU would use.
 */
public enum GpuBackend {
    CUDA,
    ROCM,
    NONE
}
