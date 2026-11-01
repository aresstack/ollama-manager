package com.aresstack.ollamamanager.swing.section;

import com.aresstack.ollamamanager.domain.hardware.GpuUsageSample;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DashboardPanelTest {

    private static final long GIB = 1L << 30;

    @Test
    public void gpuBarShowsTheFreeShareOfTheVram() {
        GpuUsageSample sample = new GpuUsageSample("RTX", 4 * GIB, 16 * GIB, 7, 0);
        assertEquals(0.75, DashboardPanel.fraction(sample), 1e-9);
        assertEquals("12.0 GiB free of 16.0 GiB VRAM, 75 % free", DashboardPanel.usageCaption(sample));
    }

    @Test
    public void unknownVramIsSaidSo() {
        GpuUsageSample sample = new GpuUsageSample("RTX", null, null, 7, 0);
        assertEquals(-1, DashboardPanel.fraction(sample), 0);
        assertEquals("VRAM unknown", DashboardPanel.usageCaption(sample));
    }
}
