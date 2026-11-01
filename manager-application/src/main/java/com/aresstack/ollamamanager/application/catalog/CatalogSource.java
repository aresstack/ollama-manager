package com.aresstack.ollamamanager.application.catalog;

/** Where installable models are searched. Both sources are installed through the server's own pull. */
public enum CatalogSource {
    /** The official library on ollama.com. */
    OLLAMA_LIBRARY("Ollama library"),
    /** GGUF repositories on huggingface.co, pulled by Ollama as {@code hf.co/<repo>:<quant>}. */
    HUGGING_FACE("Hugging Face (GGUF)");

    private final String title;

    CatalogSource(String title) {
        this.title = title;
    }

    public String title() {
        return title;
    }

    @Override
    public String toString() {
        return title;
    }
}
