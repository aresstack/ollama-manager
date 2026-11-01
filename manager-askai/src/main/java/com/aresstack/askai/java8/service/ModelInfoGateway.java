package com.aresstack.askai.java8.service;

import com.aresstack.askai.java8.client.OllamaModelInfoView;

/**
 * The single call the post-install verification needs: fetch a model's {@code /api/show} details.
 * Backed in production by {@code AskAiOllamaClient.getModelInfo} (no second {@code /api/show} client
 * is built); the interface exists so {@link InstalledModelVerificationService} can be unit-tested
 * with a scripted fake instead of a live Ollama server.
 */
public interface ModelInfoGateway {

    /** @return the {@code /api/show} view for {@code modelName}; throws when the call fails. */
    OllamaModelInfoView getModelInfo(String modelName) throws Exception;
}
