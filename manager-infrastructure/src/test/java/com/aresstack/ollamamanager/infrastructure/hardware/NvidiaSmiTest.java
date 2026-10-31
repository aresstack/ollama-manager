package com.aresstack.ollamamanager.infrastructure.hardware;

import com.aresstack.ollamamanager.application.common.CancellationToken;
import com.aresstack.ollamamanager.application.common.ErrorKind;
import com.aresstack.ollamamanager.application.common.OperationException;
import com.aresstack.ollamamanager.domain.hardware.GpuDescriptor;
import com.aresstack.ollamamanager.domain.hardware.GpuUsageSample;
import com.aresstack.ollamamanager.domain.hardware.GpuVendor;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class NvidiaSmiTest {

    private static final long MIB = 1024L * 1024L;

    @Test
    public void parsesRtx5080() {
        List<GpuDescriptor> gpus = NvidiaSmi.parseInventory("NVIDIA GeForce RTX 5080, 16303, 576.88\r\n", false);
        assertEquals(1, gpus.size());
        assertEquals(new GpuDescriptor(GpuVendor.NVIDIA, "NVIDIA GeForce RTX 5080", 16303 * MIB, "576.88", true),
            gpus.get(0));
    }

    @Test
    public void parsesMultiGpuAndNamesWithCommas() {
        List<GpuDescriptor> gpus = NvidiaSmi.parseInventory(
            "NVIDIA GeForce RTX 4090, 24564, 560.94\nTesla K80, rev B, 11441, 470.82\n", false);
        assertEquals(2, gpus.size());
        assertEquals("Tesla K80, rev B", gpus.get(1).name());
        assertEquals(Long.valueOf(11441 * MIB), gpus.get(1).vramBytes());
    }

    @Test
    public void keepsUnknownMemoryAsNull() {
        List<GpuDescriptor> gpus = NvidiaSmi.parseInventory("NVIDIA GeForce GTX 1060, [N/A], [N/A]", false);
        assertNull(gpus.get(0).vramBytes());
        assertNull(gpus.get(0).driverVersion());
    }

    @Test
    public void rejectsMalformedOutput() {
        assertTrue(NvidiaSmi.parseInventory("NVIDIA-SMI has failed because it couldn't communicate", false).isEmpty());
        assertTrue(NvidiaSmi.parseInventory("RTX, lots, 1.0", false).isEmpty());
    }

    @Test
    public void missingToolGivesEmptyInventory() throws Exception {
        NvidiaSmi smi = new NvidiaSmi(new FakeCommandRunner(), Arrays.asList("nvidia-smi", "C:\\x\\nvidia-smi.exe"),
            100);
        assertTrue(smi.inventory(CancellationToken.none()).isEmpty());
    }

    @Test
    public void fallsBackToSecondExecutable() throws Exception {
        FakeCommandRunner runner = new FakeCommandRunner().respond("C:\\x\\nvidia-smi.exe",
            NvidiaSmi.INVENTORY_QUERY, CommandResult.completed(0, "NVIDIA GeForce RTX 3060, 12288, 552.22"));
        NvidiaSmi smi = new NvidiaSmi(runner, Arrays.asList("nvidia-smi", "C:\\x\\nvidia-smi.exe"), 100);
        assertEquals("NVIDIA GeForce RTX 3060", smi.inventory(CancellationToken.none()).get(0).name());
    }

    @Test
    public void nonZeroExitGivesEmptyInventory() throws Exception {
        FakeCommandRunner runner = new FakeCommandRunner().respond("nvidia-smi", NvidiaSmi.INVENTORY_QUERY,
            CommandResult.completed(9, "No devices were found"));
        assertTrue(new NvidiaSmi(runner, Arrays.asList("nvidia-smi"), 100).inventory(CancellationToken.none())
            .isEmpty());
    }

    @Test
    public void timeoutPropagatesFromInventoryButNotFromStatistics() throws Exception {
        FakeCommandRunner runner = new FakeCommandRunner().timeout("nvidia-smi", "--query-gpu=");
        NvidiaSmi smi = new NvidiaSmi(runner, Arrays.asList("nvidia-smi"), 100);
        try {
            smi.inventory(CancellationToken.none());
            fail();
        } catch (OperationException e) {
            assertEquals(ErrorKind.TIMEOUT, e.kind());
        }
        assertTrue(smi.sample(CancellationToken.none()).isEmpty());
    }

    @Test
    public void parsesStatistics() throws Exception {
        FakeCommandRunner runner = new FakeCommandRunner().respond("nvidia-smi", NvidiaSmi.STATISTICS_QUERY,
            CommandResult.completed(0, "0, NVIDIA GeForce RTX 5080, 87, 9120, 16303\n1, NVIDIA T400, [N/A], 0, 2048"));
        List<GpuUsageSample> stats = new NvidiaSmi(runner, Arrays.asList("nvidia-smi"), 100)
            .sample(CancellationToken.none());
        assertEquals(2, stats.size());
        assertEquals(Integer.valueOf(87), stats.get(0).utilizationPercent());
        assertEquals(Long.valueOf(9120 * MIB), stats.get(0).usedVramBytes());
        assertNull(stats.get(1).utilizationPercent());
        assertEquals(Long.valueOf(16303 * MIB), stats.get(0).totalVramBytes());
        assertTrue(stats.get(0).sampledAtEpochMillis() > 0);
    }

    @Test
    public void implausibleStatisticsBecomeNull() {
        List<GpuUsageSample> stats = NvidiaSmi.parseStatistics("0, GPU, 140, 9000, 4000", 1L);
        assertNull(stats.get(0).utilizationPercent());
        assertNull(stats.get(0).usedVramBytes());
        assertEquals(Long.valueOf(4000 * MIB), stats.get(0).totalVramBytes());
    }

    @Test
    public void missingToolGivesEmptyTelemetry() throws Exception {
        assertTrue(new NvidiaSmi(new FakeCommandRunner(), Arrays.asList("nvidia-smi"), 100)
            .sample(CancellationToken.none()).isEmpty());
    }

    @Test
    public void readsComputeCapabilityWhenDriverSupportsIt() throws Exception {
        FakeCommandRunner runner = new FakeCommandRunner().respond("nvidia-smi", NvidiaSmi.INVENTORY_WITH_CC_QUERY,
            CommandResult.completed(0, "NVIDIA GeForce RTX 5080, 16303, 610.88, 12.0"));
        GpuDescriptor gpu = new NvidiaSmi(runner, Arrays.asList("nvidia-smi"), 100)
            .inventory(CancellationToken.none()).get(0);
        assertEquals("12.0", gpu.computeCapability());
        assertEquals("610.88", gpu.driverVersion());
    }

    @Test
    public void fallsBackToPlainQueryWhenComputeCapabilityIsRejected() throws Exception {
        FakeCommandRunner runner = new FakeCommandRunner()
            .respond("nvidia-smi", NvidiaSmi.INVENTORY_WITH_CC_QUERY,
                CommandResult.completed(2, "Field \"compute_cap\" is not a valid field to query."))
            .respond("nvidia-smi", NvidiaSmi.INVENTORY_QUERY,
                CommandResult.completed(0, "NVIDIA GeForce GTX 1060, 6144, 472.12"));
        List<GpuDescriptor> fallback = new NvidiaSmi(runner, Arrays.asList("nvidia-smi"), 100)
            .inventory(CancellationToken.none());
        assertEquals("NVIDIA GeForce GTX 1060", fallback.get(0).name());
        assertNull(fallback.get(0).computeCapability());
        List<GpuDescriptor> gpus = NvidiaSmi.parseInventory("NVIDIA GeForce GTX 1060, 6144, 472.12, [N/A]", true);
        assertNull(gpus.get(0).computeCapability());
        assertTrue(NvidiaSmi.parseInventory("NVIDIA GeForce GTX 1060, 6144, 472.12, abc", true).isEmpty());
    }
}
