package com.aresstack.ollamamanager.application.benchmark;

/**
 * Outcome of one concurrency stage: {@code parallel} chats sent at the same time. Total throughput is all
 * generated tokens divided by the stage's wall-clock time.
 */
public final class StageResult {

    /** Share of the model in VRAM from which it counts as fully on the GPU. */
    static final double FULL_GPU = 0.999;

    private final int parallel;
    private final int requests;
    private final int failures;
    private final double perChatTpsAvg;
    private final double perChatTpsMin;
    private final double aggregateTps;
    private final long ttftP50Millis;
    private final long ttftP95Millis;
    private final long latencyP50Millis;
    private final long latencyP95Millis;
    private final long wallMillis;
    private final Long peakVramBytes;
    private final Double gpuShare;
    private final String firstError;

    public StageResult(int parallel, int requests, int failures, double perChatTpsAvg, double perChatTpsMin,
                       double aggregateTps, long ttftP50Millis, long ttftP95Millis, long latencyP50Millis,
                       long latencyP95Millis, long wallMillis,
                       Long peakVramBytes, Double gpuShare, String firstError) {
        this.parallel = parallel;
        this.requests = requests;
        this.failures = failures;
        this.perChatTpsAvg = perChatTpsAvg;
        this.perChatTpsMin = perChatTpsMin;
        this.aggregateTps = aggregateTps;
        this.ttftP50Millis = ttftP50Millis;
        this.ttftP95Millis = ttftP95Millis;
        this.latencyP50Millis = latencyP50Millis;
        this.latencyP95Millis = latencyP95Millis;
        this.wallMillis = wallMillis;
        this.peakVramBytes = peakVramBytes;
        this.gpuShare = gpuShare;
        this.firstError = firstError;
    }

    public int parallel() {
        return parallel;
    }

    public int requests() {
        return requests;
    }

    public int failures() {
        return failures;
    }

    public double perChatTpsAvg() {
        return perChatTpsAvg;
    }

    public double perChatTpsMin() {
        return perChatTpsMin;
    }

    public double aggregateTps() {
        return aggregateTps;
    }

    public long ttftP50Millis() {
        return ttftP50Millis;
    }

    public long ttftP95Millis() {
        return ttftP95Millis;
    }

    /** Median time from sending a request until its last token. */
    public long latencyP50Millis() {
        return latencyP50Millis;
    }

    public long latencyP95Millis() {
        return latencyP95Millis;
    }

    /** Time from the synchronized start until the last chat of the stage finished. */
    public long wallMillis() {
        return wallMillis;
    }

    /** @return peak GPU memory in use during the stage, or {@code null} without telemetry */
    public Long peakVramBytes() {
        return peakVramBytes;
    }

    /** @return share of the model in VRAM after the stage, or {@code null} when unknown */
    public Double gpuShare() {
        return gpuShare;
    }

    /** @return the first error message, or {@code null} */
    public String firstError() {
        return firstError;
    }

    /** No failed request and the model was not pushed out of VRAM. */
    public boolean isStable() {
        return failures == 0 && requests > 0 && (gpuShare == null || gpuShare >= FULL_GPU);
    }
}
