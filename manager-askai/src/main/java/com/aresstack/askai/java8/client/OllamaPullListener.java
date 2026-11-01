package com.aresstack.askai.java8.client;

/**
 * Receives progress updates while an Ollama model is being pulled.
 */
public interface OllamaPullListener {

    void onProgress(OllamaPullProgress progress);
}
