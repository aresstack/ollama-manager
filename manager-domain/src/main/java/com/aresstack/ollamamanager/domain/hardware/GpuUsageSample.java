package com.aresstack.ollamamanager.domain.hardware;

import com.aresstack.ollamamanager.domain.common.Preconditions;

/**
 * One measured GPU usage reading. Values the tool did not report stay {@code null}; nothing is estimated.
 */
public final class GpuUsageSample {

    private final String gpuName;
    private final Long usedVramBytes;
    private final Long totalVramBytes;
    private final Integer utilizationPercent;
    private final long sampledAtEpochMillis;

    public GpuUsageSample(String gpuName, Long usedVramBytes, Long totalVramBytes, Integer utilizationPercent,
                          long sampledAtEpochMillis) {
        this.gpuName = Preconditions.requireNonBlank(gpuName, "gpuName");
        if (usedVramBytes != null) {
            Preconditions.requireNonNegative(usedVramBytes, "usedVramBytes");
        }
        if (totalVramBytes != null) {
            Preconditions.requireNonNegative(totalVramBytes, "totalVramBytes");
        }
        if (usedVramBytes != null && totalVramBytes != null && usedVramBytes > totalVramBytes) {
            throw new IllegalArgumentException("usedVramBytes must not exceed totalVramBytes");
        }
        if (utilizationPercent != null) {
            Preconditions.requireInRange(utilizationPercent, 0, 100, "utilizationPercent");
        }
        this.usedVramBytes = usedVramBytes;
        this.totalVramBytes = totalVramBytes;
        this.utilizationPercent = utilizationPercent;
        this.sampledAtEpochMillis = sampledAtEpochMillis;
    }

    public String gpuName() {
        return gpuName;
    }

    /** Return used video memory in bytes, or {@code null} if not reported. */
    public Long usedVramBytes() {
        return usedVramBytes;
    }

    /** Return total video memory in bytes, or {@code null} if not reported. */
    public Long totalVramBytes() {
        return totalVramBytes;
    }

    /** Return GPU utilization in percent, or {@code null} if not reported. */
    public Integer utilizationPercent() {
        return utilizationPercent;
    }

    public long sampledAtEpochMillis() {
        return sampledAtEpochMillis;
    }
}
