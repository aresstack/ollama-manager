package com.aresstack.ollamamanager.application.hardware;

/**
 * Live statistics of one GPU from a vendor tool. Unknown values are {@code null}, never guessed.
 */
public final class GpuStatistics {

    private final int index;
    private final String name;
    private final Integer utilizationPercent;
    private final Long memoryUsedBytes;
    private final Long memoryTotalBytes;

    public GpuStatistics(int index, String name, Integer utilizationPercent, Long memoryUsedBytes,
                         Long memoryTotalBytes) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        this.index = index;
        this.name = name;
        this.utilizationPercent = utilizationPercent;
        this.memoryUsedBytes = memoryUsedBytes;
        this.memoryTotalBytes = memoryTotalBytes;
    }

    public int index() {
        return index;
    }

    public String name() {
        return name;
    }

    /** Return GPU utilisation in percent, or {@code null} if unknown. */
    public Integer utilizationPercent() {
        return utilizationPercent;
    }

    /** Return used VRAM in bytes, or {@code null} if unknown. */
    public Long memoryUsedBytes() {
        return memoryUsedBytes;
    }

    /** Return total VRAM in bytes, or {@code null} if unknown. */
    public Long memoryTotalBytes() {
        return memoryTotalBytes;
    }
}
