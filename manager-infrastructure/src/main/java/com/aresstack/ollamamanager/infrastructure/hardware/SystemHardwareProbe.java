package com.aresstack.ollamamanager.infrastructure.hardware;

import com.aresstack.ollamamanager.application.common.CancellationToken;
import com.aresstack.ollamamanager.application.common.OperationCancelledException;
import com.aresstack.ollamamanager.application.common.OperationException;
import com.aresstack.ollamamanager.application.port.HardwareProbePort;
import com.aresstack.ollamamanager.domain.hardware.GpuDescriptor;
import com.aresstack.ollamamanager.domain.hardware.GpuVendor;
import com.aresstack.ollamamanager.domain.hardware.HardwareSnapshot;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * {@link HardwareProbePort} combining nvidia-smi (accurate NVIDIA VRAM and driver) with the Windows CIM view
 * (all vendors, CPU, RAM). Failing or missing tools degrade to {@code null} values instead of errors.
 */
public final class SystemHardwareProbe implements HardwareProbePort {

    static final long TOOL_TIMEOUT_MILLIS = 10000;

    private final String operatingSystem;
    private final String architecture;
    private final int logicalProcessors;
    private final NvidiaSmi nvidiaSmi;
    private final WindowsCimInventory cim;

    SystemHardwareProbe(String operatingSystem, String architecture, int logicalProcessors, NvidiaSmi nvidiaSmi,
                        WindowsCimInventory cim) {
        this.operatingSystem = operatingSystem;
        this.architecture = architecture;
        this.logicalProcessors = Math.max(1, logicalProcessors);
        this.nvidiaSmi = nvidiaSmi;
        this.cim = cim;
    }

    /** Create the probe for the running JVM; CIM is used only on Windows. */
    public static SystemHardwareProbe forCurrentSystem() {
        String os = System.getProperty("os.name", "unknown");
        boolean windows = os.toLowerCase(Locale.ROOT).startsWith("windows");
        CommandRunner runner = new ProcessCommandRunner(windows ? Charset.forName("UTF-8") : Charset.defaultCharset());
        return new SystemHardwareProbe(os, System.getProperty("os.arch", "unknown"),
            Runtime.getRuntime().availableProcessors(),
            new NvidiaSmi(runner, NvidiaSmi.defaultExecutables(windows), TOOL_TIMEOUT_MILLIS),
            windows ? new WindowsCimInventory(runner, TOOL_TIMEOUT_MILLIS) : null);
    }

    public NvidiaSmi nvidiaSmi() {
        return nvidiaSmi;
    }

    @Override
    public HardwareSnapshot probe(CancellationToken cancellation) throws OperationException {
        List<GpuDescriptor> nvidia = nvidiaInventory(cancellation);
        WindowsCimInventory.Inventory inventory = cimInventory(cancellation);
        cancellation.throwIfCancelled();
        String cpuName = inventory == null ? null : inventory.cpuName();
        Long memory = inventory == null ? null : inventory.totalMemoryBytes();
        if (memory == null) {
            memory = jvmTotalMemory();
        }
        if (cpuName == null) {
            String identifier = System.getenv("PROCESSOR_IDENTIFIER");
            cpuName = identifier == null || identifier.trim().isEmpty() ? null : identifier.trim();
        }
        return new HardwareSnapshot(operatingSystem, architecture, cpuName, logicalProcessors, memory,
            merge(nvidia, inventory == null ? Collections.<GpuDescriptor>emptyList() : inventory.gpus()));
    }

    /** Prefer nvidia-smi entries for NVIDIA GPUs; keep CIM entries when nvidia-smi gave nothing. */
    static List<GpuDescriptor> merge(List<GpuDescriptor> nvidia, List<GpuDescriptor> cimGpus) {
        List<GpuDescriptor> merged = new ArrayList<GpuDescriptor>(nvidia);
        for (GpuDescriptor gpu : cimGpus) {
            if (gpu.vendor() != GpuVendor.NVIDIA || nvidia.isEmpty()) {
                merged.add(gpu);
            }
        }
        return merged;
    }

    private List<GpuDescriptor> nvidiaInventory(CancellationToken cancellation) throws OperationException {
        try {
            return nvidiaSmi.inventory(cancellation);
        } catch (OperationCancelledException e) {
            throw e;
        } catch (OperationException e) {
            return Collections.emptyList();
        }
    }

    private WindowsCimInventory.Inventory cimInventory(CancellationToken cancellation) throws OperationException {
        if (cim == null) {
            return null;
        }
        try {
            return cim.read(cancellation);
        } catch (OperationCancelledException e) {
            throw e;
        } catch (OperationException e) {
            return null;
        }
    }

    /**
     * Read physical memory from the JDK management bean. Newer Java 8 updates (for example Oracle 8u481)
     * deprecate {@code getTotalPhysicalMemorySize()} in favour of {@code getTotalMemorySize()}, which older
     * Java 8 updates do not have, so the deprecated call is the only one available on every Java 8 runtime.
     */
    @SuppressWarnings("deprecation")
    private static Long jvmTotalMemory() {
        OperatingSystemMXBean bean = ManagementFactory.getOperatingSystemMXBean();
        if (bean instanceof com.sun.management.OperatingSystemMXBean) {
            long total = ((com.sun.management.OperatingSystemMXBean) bean).getTotalPhysicalMemorySize();
            return total > 0 ? Long.valueOf(total) : null;
        }
        return null;
    }
}
