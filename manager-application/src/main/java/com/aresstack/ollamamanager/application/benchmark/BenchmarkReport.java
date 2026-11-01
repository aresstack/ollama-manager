package com.aresstack.ollamamanager.application.benchmark;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One concurrency sweep for one model and context size, with the derived answer to "how many parallel chats are optimal at this setting".
 */
public final class BenchmarkReport {

    private final String id;
    private final long startedAtEpochMillis;
    private final String model;
    private final Integer numCtx;
    private final int maxTokens;
    private final String serverNumParallel;
    private final long loadMillis;
    private final List<StageResult> stages;
    private final boolean complete;

    public BenchmarkReport(String id, long startedAtEpochMillis, String model, Integer numCtx, int maxTokens,
                           String serverNumParallel, long loadMillis,
                           List<StageResult> stages, boolean complete) {
        if (id == null || model == null || stages == null) {
            throw new IllegalArgumentException("id, model and stages must not be null");
        }
        this.id = id;
        this.startedAtEpochMillis = startedAtEpochMillis;
        this.model = model;
        this.numCtx = numCtx;
        this.maxTokens = maxTokens;
        this.serverNumParallel = serverNumParallel;
        this.loadMillis = loadMillis;
        this.stages = Collections.unmodifiableList(new ArrayList<StageResult>(stages));
        this.complete = complete;
    }

    public String id() {
        return id;
    }

    public long startedAtEpochMillis() {
        return startedAtEpochMillis;
    }

    public String model() {
        return model;
    }

    /** @return the context size sent with every request, or {@code null} for the server default */
    public Integer numCtx() {
        return numCtx;
    }

    public int maxTokens() {
        return maxTokens;
    }

    /** @return OLLAMA_NUM_PARALLEL as configured when the run started, or {@code null} for the server default */
    public String serverNumParallel() {
        return serverNumParallel;
    }

    public long loadMillis() {
        return loadMillis;
    }

    public List<StageResult> stages() {
        return stages;
    }

    /** @return {@code false} when the run was cancelled or stopped early */
    public boolean complete() {
        return complete;
    }

    /** Highest parallelism without errors and without leaving the GPU, regardless of speed, or {@code null}. */
    public StageResult maxStable() {
        StageResult best = null;
        for (StageResult stage : stages) {
            if (stage.isStable() && (best == null || stage.parallel() > best.parallel())) {
                best = stage;
            }
        }
        return best;
    }

    /**
     * The optimum: the stable stage with the highest total tokens per second, or {@code null}. More chats than
     * this only share the same throughput and make each chat slower.
     */
    public StageResult optimum() {
        StageResult best = null;
        for (StageResult stage : stages) {
            if (stage.isStable() && (best == null || stage.aggregateTps() > best.aggregateTps())) {
                best = stage;
            }
        }
        return best;
    }
}
