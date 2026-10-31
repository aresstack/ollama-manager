package com.aresstack.ollamamanager.infrastructure.hardware;

import com.aresstack.ollamamanager.application.common.CancellationToken;
import com.aresstack.ollamamanager.application.common.OperationCancelledException;
import com.aresstack.ollamamanager.application.hardware.GpuCompatibilityRules;
import com.aresstack.ollamamanager.application.hardware.GpuSupport;
import com.aresstack.ollamamanager.domain.hardware.GpuDescriptor;
import com.aresstack.ollamamanager.domain.hardware.GpuFingerprint;
import com.aresstack.ollamamanager.domain.hardware.GpuVendor;
import com.aresstack.ollamamanager.domain.hardware.HardwareSnapshot;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SystemHardwareProbeTest {

    private static final String CIM = "{\"gpus\":[{\"Name\":\"NVIDIA GeForce RTX 5080\","
        + "\"AdapterCompatibility\":\"NVIDIA\",\"DriverVersion\":\"32.0.15.7688\",\"AdapterRAM\":4293918720,"
        + "\"PNPDeviceID\":\"PCI\\\\VEN_10DE&DEV_2C02\"},{\"Name\":\"Intel(R) UHD Graphics 770\","
        + "\"AdapterCompatibility\":\"Intel Corporation\",\"DriverVersion\":\"31.0\",\"AdapterRAM\":1073741824,"
        + "\"PNPDeviceID\":\"PCI\\\\VEN_8086&DEV_A780\"}],\"registry\":[],\"cpu\":\"Intel(R) Core(TM) i7-14700K\","
        + "\"memory\":68719476736}";

    private static SystemHardwareProbe probe(FakeCommandRunner runner) {
        return new SystemHardwareProbe("Windows 11", "amd64", 28,
            new NvidiaSmi(runner, Arrays.asList("nvidia-smi"), 100), new WindowsCimInventory(runner, 100));
    }

    @Test
    public void prefersNvidiaSmiOverCimForNvidia() throws Exception {
        FakeCommandRunner runner = new FakeCommandRunner()
            .respond("nvidia-smi", NvidiaSmi.INVENTORY_QUERY,
                CommandResult.completed(0, "NVIDIA GeForce RTX 5080, 16303, 576.88"))
            .respond("powershell.exe", "-NoProfile", CommandResult.completed(0, CIM));
        HardwareSnapshot snapshot = probe(runner).probe(CancellationToken.none());
        assertEquals(2, snapshot.gpus().size());
        assertEquals("576.88", snapshot.gpus().get(0).driverVersion());
        assertEquals("Intel(R) Core(TM) i7-14700K", snapshot.cpuName());
        assertEquals(Long.valueOf(68719476736L), snapshot.totalMemoryBytes());
        assertEquals("NVIDIA|NVIDIA GeForce RTX 5080|17094934528|576.88", snapshot.gpuFingerprint().value());
    }

    @Test
    public void fallsBackToCimWhenNvidiaSmiTimesOut() throws Exception {
        FakeCommandRunner runner = new FakeCommandRunner().timeout("nvidia-smi", "--query-gpu=")
            .respond("powershell.exe", "-NoProfile", CommandResult.completed(0, CIM));
        HardwareSnapshot snapshot = probe(runner).probe(CancellationToken.none());
        GpuDescriptor nvidia = snapshot.gpus().get(0);
        assertEquals(GpuVendor.NVIDIA, nvidia.vendor());
        assertEquals(null, nvidia.vramBytes());
    }

    @Test
    public void nvidiaOnlyViaCimKeepsCorrectDriverAssessment() throws Exception {
        FakeCommandRunner runner = new FakeCommandRunner()
            .respond("powershell.exe", "-NoProfile", CommandResult.completed(0, CIM));
        HardwareSnapshot snapshot = probe(runner).probe(CancellationToken.none());
        GpuDescriptor nvidia = snapshot.gpus().get(0);
        assertEquals("32.0.15.7688", nvidia.driverVersion());
        assertEquals(GpuSupport.SUPPORTED, new GpuCompatibilityRules().assess(nvidia).support());
    }

    @Test
    public void noToolsGivesCpuOnlySnapshot() throws Exception {
        HardwareSnapshot snapshot = probe(new FakeCommandRunner()).probe(CancellationToken.none());
        assertTrue(snapshot.gpus().isEmpty());
        assertEquals(GpuFingerprint.CPU_ONLY, snapshot.gpuFingerprint());
        assertEquals(28, snapshot.logicalProcessors());
        assertFalse("failed CIM must not count as a verified CPU-only system", snapshot.isGpuDiscoveryComplete());
        assertNull(snapshot.verifiedGpuFingerprint());
    }

    @Test
    public void workingCimMarksDiscoveryComplete() throws Exception {
        FakeCommandRunner runner = new FakeCommandRunner()
            .respond("powershell.exe", "-NoProfile", CommandResult.completed(0, CIM));
        HardwareSnapshot snapshot = probe(runner).probe(CancellationToken.none());
        assertTrue(snapshot.isGpuDiscoveryComplete());
        assertEquals(snapshot.gpuFingerprint(), snapshot.verifiedGpuFingerprint());
    }

    @Test(expected = OperationCancelledException.class)
    public void honoursCancellation() throws Exception {
        CancellationToken token = new CancellationToken();
        token.cancel();
        probe(new FakeCommandRunner()).probe(token);
    }
}
