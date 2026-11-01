package com.aresstack.ollamamanager.domain.benchmark;

/**
 * Timing of one completed {@code /api/generate} request. Server durations come from Ollama's final stream
 * line; time to first token and wall time are measured by the client and include queueing on the server.
 */
public final class GenerationMetrics {

    private final long ttftMillis;
    private final long wallMillis;
    private final long loadNanos;
    private final int promptTokens;
    private final long promptEvalNanos;
    private final int evalTokens;
    private final long evalNanos;

    public GenerationMetrics(long ttftMillis, long wallMillis, long loadNanos, int promptTokens,
                             long promptEvalNanos, int evalTokens, long evalNanos) {
        if (ttftMillis < 0 || wallMillis < 0 || loadNanos < 0 || promptTokens < 0 || promptEvalNanos < 0
            || evalTokens < 0 || evalNanos < 0) {
            throw new IllegalArgumentException("metrics must not be negative");
        }
        this.ttftMillis = ttftMillis;
        this.wallMillis = wallMillis;
        this.loadNanos = loadNanos;
        this.promptTokens = promptTokens;
        this.promptEvalNanos = promptEvalNanos;
        this.evalTokens = evalTokens;
        this.evalNanos = evalNanos;
    }

    public long ttftMillis() {
        return ttftMillis;
    }

    public long wallMillis() {
        return wallMillis;
    }

    public long loadMillis() {
        return loadNanos / 1_000_000L;
    }

    public int promptTokens() {
        return promptTokens;
    }

    public int evalTokens() {
        return evalTokens;
    }

    /** Prompt processing speed in tokens per second, 0 when unknown. */
    public double promptTokensPerSecond() {
        return promptEvalNanos == 0 ? 0 : promptTokens * 1e9 / promptEvalNanos;
    }

    /** Generation speed of this chat in tokens per second, 0 when unknown. */
    public double generationTokensPerSecond() {
        return evalNanos == 0 ? 0 : evalTokens * 1e9 / evalNanos;
    }
}
