package com.aresstack.ollamamanager.domain.hardware;

import com.aresstack.ollamamanager.domain.common.Preconditions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Hardware facts relevant for package choice and profile scoping. Unknown numeric values are {@code null}.
 */
public final class HardwareSnapshot {

    private final String operatingSystem;
    private final String architecture;
    private final String cpuName;
    private final int logicalProcessors;
    private final Long totalMemoryBytes;
    private final List<GpuDescriptor> gpus;

    public HardwareSnapshot(String operatingSystem, String architecture, String cpuName, int logicalProcessors,
                            Long totalMemoryBytes, List<GpuDescriptor> gpus) {
        this.operatingSystem = Preconditions.requireNonBlank(operatingSystem, "operatingSystem");
        this.architecture = Preconditions.requireNonBlank(architecture, "architecture");
        this.cpuName = cpuName;
        this.logicalProcessors = Preconditions.requireInRange(logicalProcessors, 1, Integer.MAX_VALUE,
            "logicalProcessors");
        this.totalMemoryBytes = totalMemoryBytes;
        this.gpus = Collections.unmodifiableList(new ArrayList<GpuDescriptor>(
            Preconditions.requireNonNull(gpus, "gpus")));
    }

    public String operatingSystem() {
        return operatingSystem;
    }

    public String architecture() {
        return architecture;
    }

    /** Return the CPU model name, or {@code null} if unknown. */
    public String cpuName() {
        return cpuName;
    }

    public int logicalProcessors() {
        return logicalProcessors;
    }

    /** Return installed RAM in bytes, or {@code null} if unknown. */
    public Long totalMemoryBytes() {
        return totalMemoryBytes;
    }

    public List<GpuDescriptor> gpus() {
        return gpus;
    }

    public GpuFingerprint gpuFingerprint() {
        return GpuFingerprint.of(gpus);
    }
}
