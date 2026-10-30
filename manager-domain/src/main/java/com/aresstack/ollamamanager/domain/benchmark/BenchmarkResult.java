package com.aresstack.ollamamanager.domain.benchmark;

import com.aresstack.ollamamanager.domain.common.Preconditions;
import com.aresstack.ollamamanager.domain.profile.ProfileScope;

/**
 * Aggregated measurement of one parallelism candidate in one scope.
 */
public final class BenchmarkResult {

    private final ProfileScope scope;
    private final int parallelism;
    private final int totalRequests;
    private final int failedRequests;
    private final long completedOutputTokens;
    private final long elapsedMillis;
    private final long p50LatencyMillis;
    private final long p95LatencyMillis;
    private final long measuredAtEpochMillis;

    public BenchmarkResult(ProfileScope scope, int parallelism, int totalRequests, int failedRequests,
                           long completedOutputTokens, long elapsedMillis, long p50LatencyMillis,
                           long p95LatencyMillis, long measuredAtEpochMillis) {
        this.scope = Preconditions.requireNonNull(scope, "scope");
        this.parallelism = Preconditions.requireInRange(parallelism, 1, Integer.MAX_VALUE, "parallelism");
        this.totalRequests = Preconditions.requireInRange(totalRequests, 1, Integer.MAX_VALUE, "totalRequests");
        this.failedRequests = Preconditions.requireInRange(failedRequests, 0, totalRequests, "failedRequests");
        this.completedOutputTokens = Preconditions.requireNonNegative(completedOutputTokens, "completedOutputTokens");
        this.elapsedMillis = Preconditions.requireNonNegative(elapsedMillis, "elapsedMillis");
        this.p50LatencyMillis = Preconditions.requireNonNegative(p50LatencyMillis, "p50LatencyMillis");
        this.p95LatencyMillis = Preconditions.requireNonNegative(p95LatencyMillis, "p95LatencyMillis");
        if (p95LatencyMillis < p50LatencyMillis) {
            throw new IllegalArgumentException("p95 latency must not be below p50 latency");
        }
        this.measuredAtEpochMillis = measuredAtEpochMillis;
    }

    public ProfileScope scope() {
        return scope;
    }

    public int parallelism() {
        return parallelism;
    }

    public int totalRequests() {
        return totalRequests;
    }

    public int failedRequests() {
        return failedRequests;
    }

    public long completedOutputTokens() {
        return completedOutputTokens;
    }

    public long elapsedMillis() {
        return elapsedMillis;
    }

    public long p50LatencyMillis() {
        return p50LatencyMillis;
    }

    public long p95LatencyMillis() {
        return p95LatencyMillis;
    }

    public long measuredAtEpochMillis() {
        return measuredAtEpochMillis;
    }

    /** Return completed output tokens per wall-clock second across all concurrent requests. */
    public double throughputTokensPerSecond() {
        return elapsedMillis == 0 ? 0 : completedOutputTokens * 1000.0 / elapsedMillis;
    }

    /** Return whether this measurement still applies to {@code current}. */
    public boolean isValidFor(ProfileScope current) {
        return scope.isValidFor(current);
    }
}
