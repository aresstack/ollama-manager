package com.aresstack.ollamamanager.domain.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-request model options sent in the {@code options} object of a request the manager itself issues.
 * They never change the server and never apply to third-party clients.
 */
public final class ModelOptions {

    private static final ModelOptions NONE = new ModelOptions(null, null, null, null);

    private final Integer numCtx;
    private final Integer numPredict;
    private final Double temperature;
    private final Integer seed;

    public ModelOptions(Integer numCtx, Integer numPredict, Double temperature, Integer seed) {
        if (numCtx != null && numCtx < 1) {
            throw new IllegalArgumentException("numCtx must be positive: " + numCtx);
        }
        if (numPredict != null && numPredict < -2) {
            throw new IllegalArgumentException("numPredict must be -2, -1 or non-negative: " + numPredict);
        }
        if (temperature != null && (temperature < 0 || temperature.isNaN())) {
            throw new IllegalArgumentException("temperature must not be negative: " + temperature);
        }
        this.numCtx = numCtx;
        this.numPredict = numPredict;
        this.temperature = temperature;
        this.seed = seed;
    }

    public static ModelOptions none() {
        return NONE;
    }

    public Integer numCtx() {
        return numCtx;
    }

    public Integer numPredict() {
        return numPredict;
    }

    public Double temperature() {
        return temperature;
    }

    public Integer seed() {
        return seed;
    }

    public ModelOptions withNumCtx(Integer value) {
        return new ModelOptions(value, numPredict, temperature, seed);
    }

    /**
     * Return the set options keyed by their Ollama API names.
     */
    public Map<String, Object> toRequestOptions() {
        Map<String, Object> options = new LinkedHashMap<String, Object>();
        putIfSet(options, "num_ctx", numCtx);
        putIfSet(options, "num_predict", numPredict);
        putIfSet(options, "temperature", temperature);
        putIfSet(options, "seed", seed);
        return Collections.unmodifiableMap(options);
    }

    private static void putIfSet(Map<String, Object> options, String key, Object value) {
        if (value != null) {
            options.put(key, value);
        }
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ModelOptions && toRequestOptions().equals(((ModelOptions) other).toRequestOptions());
    }

    @Override
    public int hashCode() {
        return toRequestOptions().hashCode();
    }

    @Override
    public String toString() {
        return "ModelOptions" + toRequestOptions();
    }
}
