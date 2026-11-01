package com.aresstack.ollamamanager.swing.section;

import com.aresstack.ollamamanager.domain.hardware.GpuUsageSample;
import com.aresstack.ollamamanager.swing.component.MeterBar;
import com.aresstack.ollamamanager.swing.job.JobManager;
import com.aresstack.ollamamanager.swing.job.UiDispatcher;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DashboardPanelTest {

    private static final long GIB = 1024L * 1024 * 1024;

    @Test
    public void gpuBarShowsWhatIsOccupiedAndWhatIsFree() {
        GpuUsageSample sample = new GpuUsageSample("RTX", 4 * GIB, 16 * GIB, 7, 0);
        assertEquals(0.25, DashboardPanel.usedFraction(sample), 1e-9);
        String caption = DashboardPanel.usageCaption(sample);
        assertTrue(caption, caption.contains("4.0 GiB used"));
        assertTrue(caption, caption.contains("12.0 GiB free"));
        assertTrue(caption, caption.contains("25 % used"));
        String tooltip = DashboardPanel.usageTooltip(sample);
        assertTrue(tooltip, tooltip.contains("GPU busy: 7 %"));
    }

    @Test
    public void gpuRowKeepsTheTelemetryTooltip() {
        DashboardPanel panel = new DashboardPanel(new DashboardPresenter(TestServices.builder().build(),
            new JobManager(command -> { }, UiDispatcher.inline())));
        GpuUsageSample sample = new GpuUsageSample("RTX", 4 * GIB, 16 * GIB, 7, 0);
        panel.showGpuUsage(java.util.Collections.singletonList(sample));
        MeterBar meter = find(panel, MeterBar.class);
        assertTrue(meter.getToolTipText(), meter.getToolTipText().contains("GPU busy: 7 %"));
    }

    private static <T> T find(java.awt.Container root, Class<T> type) {
        for (java.awt.Component child : root.getComponents()) {
            if (type.isInstance(child)) {
                return type.cast(child);
            }
            if (child instanceof java.awt.Container) {
                T found = find((java.awt.Container) child, type);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /** Maximize, then restore: the content must fit the smaller width again (no horizontal overflow). */
    @Test
    public void contentShrinksBackAfterTheWindowWasWide() {
        DashboardPanel panel = new DashboardPanel(new DashboardPresenter(TestServices.builder().build(),
            new JobManager(command -> { }, UiDispatcher.inline())));
        panel.showGpuUsage(java.util.Collections.singletonList(new GpuUsageSample("RTX", 4 * GIB, 16 * GIB, 7, 0)));
        find(panel, com.aresstack.ollamamanager.swing.component.MessageBanner.class).info("This server was found"
            + " running but not started by Ollama Manager. Models can be managed on it. Restart replaces it with a"
            + " server started from the same installation with the stored server settings; Stop ends it.");
        layout(panel, 900, 600);
        layout(panel, 1800, 900);
        layout(panel, 900, 600);
        snapshot(panel, System.getProperty("dashboard.snapshot"));
        javax.swing.JViewport viewport = find(panel, javax.swing.JViewport.class);
        assertTrue("view " + viewport.getView().getWidth() + " > viewport " + viewport.getWidth(),
            viewport.getView().getWidth() <= viewport.getWidth());
    }

    private static void layout(java.awt.Container panel, int width, int height) {
        panel.setSize(width, height);
        // two passes, like Swing's revalidate after wrapped text got its new width
        layoutTree(panel);
        layoutTree(panel);

    }

    private static void layoutTree(java.awt.Container container) {
        container.doLayout();
        for (java.awt.Component child : container.getComponents()) {
            if (child instanceof java.awt.Container) {
                layoutTree((java.awt.Container) child);
            }
        }
    }

    private static void snapshot(java.awt.Component panel, String file) {
        if (file == null) {
            return;
        }
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(panel.getWidth(), panel.getHeight(),
            java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = image.createGraphics();
        panel.printAll(g);
        g.dispose();
        try {
            javax.imageio.ImageIO.write(image, "png", new java.io.File(file));
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    public void unknownMemoryStaysNeutral() {
        GpuUsageSample sample = new GpuUsageSample("RTX", null, null, 50, 0);
        assertTrue(DashboardPanel.usedFraction(sample) < 0);
        assertEquals("VRAM unknown", DashboardPanel.usageCaption(sample));
    }
}
