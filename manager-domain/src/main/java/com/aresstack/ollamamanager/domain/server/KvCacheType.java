package com.aresstack.ollamamanager.domain.server;

import java.util.Locale;

/**
 * Quantization of the key/value cache, applied server-wide via {@code OLLAMA_KV_CACHE_TYPE}.
 */
public enum KvCacheType {
    F16("f16", "Full precision (f16)"),
    Q8_0("q8_0", "Half memory (q8_0)"),
    Q4_0("q4_0", "Quarter memory (q4_0)");

    private final String environmentValue;
    private final String label;

    KvCacheType(String environmentValue, String label) {
        this.environmentValue = environmentValue;
        this.label = label;
    }

    public String environmentValue() {
        return environmentValue;
    }

    public String label() {
        return label;
    }

    public static KvCacheType fromEnvironmentValue(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        for (KvCacheType type : values()) {
            if (type.environmentValue.equals(normalized)) {
                return type;
            }
        }
        throw new IllegalArgumentException("unknown KV cache type: " + value);
    }
}
