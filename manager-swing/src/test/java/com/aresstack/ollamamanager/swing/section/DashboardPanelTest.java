package com.aresstack.ollamamanager.swing.section;

import com.aresstack.ollamamanager.domain.hardware.GpuUsageSample;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DashboardPanelTest {

    private static final long GIB = 1024L * 1024 * 1024;

    @Test
    public void gpuBarShowsWhatIsFree() {
        GpuUsageSample sample = new GpuUsageSample("RTX", 4 * GIB, 16 * GIB, 7, 0);
        assertEquals(0.75, DashboardPanel.freeFraction(sample), 1e-9);
        String caption = DashboardPanel.freeCaption(sample);
        assertTrue(caption, caption.contains("VRAM free"));
        assertTrue(caption, caption.contains("75 % free"));
        String tooltip = DashboardPanel.usageTooltip(sample);
        assertTrue(tooltip, tooltip.contains("In use:"));
        assertTrue(tooltip, tooltip.contains("GPU busy: 7 %"));
    }

    @Test
    public void unknownMemoryStaysNeutral() {
        GpuUsageSample sample = new GpuUsageSample("RTX", null, null, 50, 0);
        assertTrue(DashboardPanel.freeFraction(sample) < 0);
        assertEquals("VRAM unknown", DashboardPanel.freeCaption(sample));
    }
}
