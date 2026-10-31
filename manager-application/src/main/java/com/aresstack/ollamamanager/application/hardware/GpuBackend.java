package com.aresstack.ollamamanager.application.hardware;

/**
 * Ollama acceleration backend a GPU would use.
 */
public enum GpuBackend {
    CUDA,
    ROCM,
    /** Vulkan backend; documented by Ollama but not assessed by these rules. */
    VULKAN,
    NONE
}
