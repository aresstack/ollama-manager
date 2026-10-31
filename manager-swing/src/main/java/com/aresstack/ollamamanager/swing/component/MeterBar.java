package com.aresstack.ollamamanager.swing.component;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.JComponent;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * Horizontal usage meter such as "11.2 GB / 16 GB". The fill colour turns to warning and error at the configured
 * thresholds; the caption is painted inside so the control needs no extra label.
 */
public final class MeterBar extends JComponent implements Accessible {

    private static final long serialVersionUID = 1L;

    private double fraction = -1;
    private String caption = "—";
    private double warnAt = 0.8;
    private double errorAt = 0.95;

    public MeterBar() {
        setOpaque(false);
        setFocusable(true);
    }

    @Override
    public AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) {
            accessibleContext = new AccessibleMeter();
        }
        return accessibleContext;
    }

    /** Show {@code fraction} (0..1, negative for unknown) with {@code caption} painted on top. */
    public void show(double fraction, String caption) {
        this.fraction = fraction < 0 ? -1 : Math.min(1, fraction);
        this.caption = caption == null || caption.isEmpty() ? "—" : caption;
        setToolTipText(this.caption);
        getAccessibleContext().setAccessibleName(this.caption);
        revalidate();
        repaint();
    }

    /** Set the fractions at which the fill colour changes to warning and error. */
    public void setThresholds(double warnAt, double errorAt) {
        this.warnAt = warnAt;
        this.errorAt = errorAt;
        repaint();
    }

    public double fraction() {
        return fraction;
    }

    public String caption() {
        return caption;
    }

    /** Tone for the current fill level. */
    public Theme.Tone tone() {
        if (fraction < 0) {
            return Theme.Tone.NEUTRAL;
        }
        if (fraction >= errorAt) {
            return Theme.Tone.ERROR;
        }
        return fraction >= warnAt ? Theme.Tone.WARNING : Theme.Tone.SUCCESS;
    }

    @Override
    public Dimension getPreferredSize() {
        FontMetrics metrics = getFontMetrics(getFont() != null ? getFont() : Theme.baseFont());
        return new Dimension(metrics.stringWidth(caption) + 2 * Spacing.MARGIN, metrics.getHeight() + 8);
    }

    @Override
    public Dimension getMinimumSize() {
        Dimension preferred = getPreferredSize();
        return new Dimension(Spacing.MARGIN * 4, preferred.height);
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int width = getWidth();
        int height = getHeight();
        int arc = Math.min(height, 10);
        g.setColor(Theme.blend(Theme.foreground(), Theme.background(), 0.88f));
        g.fillRoundRect(0, 0, width, height, arc, arc);
        if (fraction >= 0) {
            Color fill = Theme.color(tone());
            g.setColor(Theme.blend(fill, Theme.background(), 0.45f));
            g.fillRoundRect(0, 0, (int) Math.round(width * fraction), height, arc, arc);
        }
        g.setColor(Theme.lineColor());
        g.drawRoundRect(0, 0, width - 1, height - 1, arc, arc);
        g.setFont(getFont() != null ? getFont() : Theme.baseFont());
        FontMetrics metrics = g.getFontMetrics();
        g.setColor(isEnabled() ? Theme.foreground() : Theme.mutedForeground());
        int baseline = (height - metrics.getHeight()) / 2 + metrics.getAscent();
        g.drawString(caption, Spacing.GAP, baseline);
        if (hasFocus()) {
            g.setColor(Theme.color(Theme.Tone.INFO));
            g.drawRoundRect(1, 1, width - 3, height - 3, arc, arc);
        }
        g.dispose();
    }

    /** Exposes the caption to screen readers. */
    private final class AccessibleMeter extends AccessibleJComponent {
        private static final long serialVersionUID = 1L;

        @Override
        public AccessibleRole getAccessibleRole() {
            return AccessibleRole.PROGRESS_BAR;
        }
    }
}
