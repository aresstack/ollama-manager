package com.aresstack.ollamamanager.application.benchmark;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What to measure: one model, a list of parallel chat counts, and the per-request settings.
 */
public final class ConcurrencyBenchmarkRequest {

    private final String model;
    private final List<Integer> levels;
    private final Integer numCtx;
    private final int maxTokens;
    private final int roundsPerChat;
    private final String prompt;
    private final boolean stopWhenUnstable;

    public ConcurrencyBenchmarkRequest(String model, List<Integer> levels, Integer numCtx, int maxTokens,
                                       int roundsPerChat, String prompt,
                                       boolean stopWhenUnstable) {
        if (model == null || model.trim().isEmpty()) {
            throw new IllegalArgumentException("model must not be blank");
        }
        if (levels == null || levels.isEmpty()) {
            throw new IllegalArgumentException("at least one parallelism level is required");
        }
        List<Integer> sorted = new ArrayList<Integer>();
        for (Integer level : levels) {
            if (level == null || level < 1 || level > 256) {
                throw new IllegalArgumentException("parallelism must be between 1 and 256: " + level);
            }
            if (!sorted.contains(level)) {
                sorted.add(level);
            }
        }
        Collections.sort(sorted);
        if (numCtx != null && numCtx < 1) {
            throw new IllegalArgumentException("numCtx must be positive");
        }
        if (maxTokens < 1 || roundsPerChat < 1) {
            throw new IllegalArgumentException("maxTokens and roundsPerChat must be positive");
        }
        this.model = model.trim();
        this.levels = Collections.unmodifiableList(sorted);
        this.numCtx = numCtx;
        this.maxTokens = maxTokens;
        this.roundsPerChat = roundsPerChat;
        this.prompt = prompt == null || prompt.trim().isEmpty() ? DEFAULT_PROMPT : prompt;
        this.stopWhenUnstable = stopWhenUnstable;
    }

    /** Prompt that makes every model write steadily until the token limit. */
    public static final String DEFAULT_PROMPT =
        "Write a detailed, well structured essay about the history of computing, from mechanical calculators "
            + "to modern GPUs. Keep writing.";

    public String model() {
        return model;
    }

    public List<Integer> levels() {
        return levels;
    }

    public Integer numCtx() {
        return numCtx;
    }

    public int maxTokens() {
        return maxTokens;
    }

    public int roundsPerChat() {
        return roundsPerChat;
    }

    public String prompt() {
        return prompt;
    }

    public boolean stopWhenUnstable() {
        return stopWhenUnstable;
    }
}
