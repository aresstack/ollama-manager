package com.aresstack.ollamamanager.swing.component;

import org.junit.Test;

import javax.swing.JButton;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Headless checks of the widget state logic; painting is not exercised here. */
public class WidgetsTest {

    @Test
    public void meterBarMapsFractionToTone() {
        MeterBar meter = new MeterBar();
        assertEquals(Theme.Tone.NEUTRAL, meter.tone());
        meter.show(0.5, "8 GB / 16 GB");
        assertEquals(Theme.Tone.SUCCESS, meter.tone());
        meter.show(0.85, "x");
        assertEquals(Theme.Tone.WARNING, meter.tone());
        meter.show(1.7, "x");
        assertEquals(1.0, meter.fraction(), 0.0);
        assertEquals(Theme.Tone.ERROR, meter.tone());
        meter.show(-3, null);
        assertEquals("—", meter.caption());
        assertEquals(Theme.Tone.NEUTRAL, meter.tone());
    }

    @Test
    public void messageBannerShowsAndClears() {
        MessageBanner banner = new MessageBanner();
        assertFalse(banner.isVisible());
        banner.warning("Server is external");
        assertTrue(banner.isVisible());
        assertEquals(Theme.Tone.WARNING, banner.tone());
        assertTrue(banner.message().contains("Server is external"));
        banner.setActions(new JButton("Retry"), true);
        banner.clear();
        assertFalse(banner.isVisible());
        banner.error("Pull", new IllegalStateException("boom"));
        assertTrue(banner.message().contains("Pull failed."));
        banner.show(Theme.Tone.INFO, "");
        assertFalse(banner.isVisible());
    }

    @Test
    public void statusBadgeKeepsToneAndFallsBackToDash() {
        StatusBadge badge = new StatusBadge();
        badge.show(null, null);
        assertEquals("—", badge.getText());
        assertEquals(Theme.Tone.NEUTRAL, badge.tone());
        badge.show("running", Theme.Tone.SUCCESS);
        assertEquals(Theme.Tone.SUCCESS, badge.tone());
        assertTrue(badge.getIcon().getIconWidth() >= 8);
    }

    @Test
    public void tablesFitRowHeightToFont() {
        JTable table = new JTable(new DefaultTableModel(new Object[] {"a", "b"}, 0));
        Tables.setup(table, "Test");
        assertTrue(table.getRowHeight() >= table.getFontMetrics(table.getFont()).getHeight());
        assertFalse(table.getTableHeader().getReorderingAllowed());
        Tables.alignRight(table, 1);
        Tables.setWidthInChars(table, 0, 10);
        assertTrue(table.getColumnModel().getColumn(0).getPreferredWidth() > 0);
    }

    @Test
    public void actionBarGroupsButtons() {
        ActionBar bar = new ActionBar().addAction(new JButton("Pull")).addSeparator().addTrailing(new JButton("Delete"));
        assertEquals(3, bar.getComponentCount());
        assertEquals(3, ((java.awt.Container) bar.getComponent(0)).getComponentCount());
        assertEquals(1, ((java.awt.Container) bar.getComponent(2)).getComponentCount());
    }

    @Test
    public void meterBarIsReachableThroughTheAccessibilityTree() {
        javax.swing.JPanel parent = new javax.swing.JPanel();
        MeterBar meter = new MeterBar();
        parent.add(meter);
        meter.show(0.5, "8 GB / 16 GB");
        assertEquals(1, parent.getAccessibleContext().getAccessibleChildrenCount());
        assertEquals("8 GB / 16 GB", meter.getAccessibleContext().getAccessibleName());
    }

    @Test
    public void cardPanelReplacesTrailingComponent() {
        CardPanel card = new CardPanel("Server");
        JButton first = new JButton("a");
        JButton second = new JButton("b");
        card.setTrailing(first);
        card.setTrailing(second);
        assertFalse(first.getParent() != null);
        assertTrue(second.getParent() != null);
    }

    @Test
    public void cardPanelDoesNotStretchVertically() {
        CardPanel card = new CardPanel("Server", new JButton("x"));
        card.setSize(400, 400);
        card.doLayout();
        assertEquals(card.getPreferredSize().height, card.getMaximumSize().height);
        assertEquals(Integer.MAX_VALUE, card.getMaximumSize().width);
    }
}
