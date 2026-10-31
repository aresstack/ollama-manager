package com.aresstack.ollamamanager.domain.hardware;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;

public class GpuValuesTest {

    @Test
    public void computeCapabilityIsOptional() {
        GpuDescriptor unknown = new GpuDescriptor(GpuVendor.NVIDIA, "RTX 5080", null, "610.88", true);
        GpuDescriptor known = new GpuDescriptor(GpuVendor.NVIDIA, "RTX 5080", null, "610.88", true, "12.0");

        assertNull(unknown.computeCapability());
        assertEquals("12.0", known.computeCapability());
        assertNotEquals(unknown, known);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsMalformedComputeCapability() {
        new GpuDescriptor(GpuVendor.NVIDIA, "RTX 5080", null, null, true, "sm_120");
    }

    @Test
    public void usageSampleKeepsUnreportedValuesUnknown() {
        GpuUsageSample sample = new GpuUsageSample("RTX 5080", 1024L, null, null, 0);

        assertNull(sample.totalVramBytes());
        assertNull(sample.utilizationPercent());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsUsedAboveTotal() {
        new GpuUsageSample("RTX 5080", 2048L, 1024L, 50, 0);
    }
}
