package com.aresstack.comiccontrols.control;

import com.aresstack.comiccontrols.theme.ResearchUiMetrics;
import com.aresstack.comiccontrols.theme.ResearchUiPainter;
import com.aresstack.comiccontrols.theme.ResearchUiPalette;

import javax.swing.Icon;
import javax.swing.JButton;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;

/**
 * A 34×34 icon-only button of the research-UI design study (the chats-footer gear): transparent at
 * rest, {@link ResearchUiPalette#SECONDARY_SURFACE} on hover, muted glyph that brightens on hover.
 * The icon paints with the button's CURRENT foreground, so a foreground-aware icon follows along.
 */
public final class ResearchIconButton extends JButton {

    private static final int SIZE = ResearchUiMetrics.FOOTER_ICON_BUTTON;

    private Color hoverFill = ResearchUiPalette.SECONDARY_SURFACE;
    private Color hoverGlyph = ResearchUiPalette.TEXT_PRIMARY;

    public ResearchIconButton(Icon icon, String tooltip) {
        super(icon);
        setToolTipText(tooltip);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setOpaque(false);
        setRolloverEnabled(true);
        setForeground(ResearchUiPalette.TEXT_MUTED);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        boolean hovered = getModel().isRollover() || getModel().isPressed();
        setForeground(hovered ? hoverGlyph : ResearchUiPalette.TEXT_MUTED);
        if (hovered) {
            Graphics2D g2 = ResearchUiPainter.prepare(graphics);
            try {
                ResearchUiPainter.fillRound(g2, 0, 0, getWidth(), getHeight(),
                        ResearchUiMetrics.RADIUS_CONTROL, hoverFill);
            } finally {
                g2.dispose();
            }
        }
        super.paintComponent(graphics);
    }

    /**
     * The hover look (e.g. a destructive action: danger-red fill, light glyph); the default is the
     * secondary surface with the primary glyph.
     */
    public void setHoverColors(Color fill, Color glyph) {
        if (fill == null || glyph == null) {
            throw new IllegalArgumentException("fill and glyph must not be null");
        }
        this.hoverFill = fill;
        this.hoverGlyph = glyph;
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(SIZE, SIZE);
    }

    @Override
    public Dimension getMinimumSize() {
        return getPreferredSize();
    }

    @Override
    public Dimension getMaximumSize() {
        return getPreferredSize();
    }
}
