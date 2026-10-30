package com.aresstack.ollamamanager.infrastructure.hardware;

import com.aresstack.ollamamanager.application.common.CancellationToken;
import com.aresstack.ollamamanager.application.common.ErrorKind;
import com.aresstack.ollamamanager.application.common.OperationException;
import com.aresstack.ollamamanager.application.hardware.GpuStatistics;
import com.aresstack.ollamamanager.domain.hardware.GpuDescriptor;
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
        List<GpuDescriptor> gpus = NvidiaSmi.parseInventory("NVIDIA GeForce RTX 5080, 16303, 576.88\r\n");
        assertEquals(1, gpus.size());
        assertEquals(new GpuDescriptor(GpuVendor.NVIDIA, "NVIDIA GeForce RTX 5080", 16303 * MIB, "576.88", true),
            gpus.get(0));
    }

    @Test
    public void parsesMultiGpuAndNamesWithCommas() {
        List<GpuDescriptor> gpus = NvidiaSmi.parseInventory(
            "NVIDIA GeForce RTX 4090, 24564, 560.94\nTesla K80, rev B, 11441, 470.82\n");
        assertEquals(2, gpus.size());
        assertEquals("Tesla K80, rev B", gpus.get(1).name());
        assertEquals(Long.valueOf(11441 * MIB), gpus.get(1).vramBytes());
    }

    @Test
    public void keepsUnknownMemoryAsNull() {
        List<GpuDescriptor> gpus = NvidiaSmi.parseInventory("NVIDIA GeForce GTX 1060, [N/A], [N/A]");
        assertNull(gpus.get(0).vramBytes());
        assertNull(gpus.get(0).driverVersion());
    }

    @Test
    public void rejectsMalformedOutput() {
        assertTrue(NvidiaSmi.parseInventory("NVIDIA-SMI has failed because it couldn't communicate").isEmpty());
        assertTrue(NvidiaSmi.parseInventory("RTX, lots, 1.0").isEmpty());
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
        assertTrue(smi.read(CancellationToken.none()).isEmpty());
    }

    @Test
    public void parsesStatistics() throws Exception {
        FakeCommandRunner runner = new FakeCommandRunner().respond("nvidia-smi", NvidiaSmi.STATISTICS_QUERY,
            CommandResult.completed(0, "0, NVIDIA GeForce RTX 5080, 87, 9120, 16303\n1, NVIDIA T400, [N/A], 0, 2048"));
        List<GpuStatistics> stats = new NvidiaSmi(runner, Arrays.asList("nvidia-smi"), 100)
            .read(CancellationToken.none());
        assertEquals(2, stats.size());
        assertEquals(Integer.valueOf(87), stats.get(0).utilizationPercent());
        assertEquals(Long.valueOf(9120 * MIB), stats.get(0).memoryUsedBytes());
        assertNull(stats.get(1).utilizationPercent());
    }
}
