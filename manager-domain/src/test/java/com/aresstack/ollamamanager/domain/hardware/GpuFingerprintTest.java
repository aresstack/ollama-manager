package com.aresstack.ollamamanager.domain.hardware;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class GpuFingerprintTest {

    private static final GpuDescriptor RTX = new GpuDescriptor(GpuVendor.NVIDIA, "RTX 5080", 17179869184L, "581.57",
        true);
    private static final GpuDescriptor IGPU = new GpuDescriptor(GpuVendor.INTEL, "UHD 770", null, null, false);

    @Test
    public void integratedOnlyIsCpuOnly() {
        assertEquals(GpuFingerprint.CPU_ONLY, GpuFingerprint.of(Collections.singletonList(IGPU)));
    }

    @Test
    public void orderDoesNotMatter() {
        GpuDescriptor amd = new GpuDescriptor(GpuVendor.AMD, "RX 7900 XTX", null, null, true);

        assertEquals(GpuFingerprint.of(Arrays.asList(RTX, amd)), GpuFingerprint.of(Arrays.asList(amd, RTX)));
    }

    @Test
    public void driverChangeChangesFingerprint() {
        GpuDescriptor updated = new GpuDescriptor(GpuVendor.NVIDIA, "RTX 5080", 17179869184L, "590.00", true);

        assertNotEquals(GpuFingerprint.of(Collections.singletonList(RTX)),
            GpuFingerprint.of(Collections.singletonList(updated)));
    }
}
