package com.aresstack.comiccontrols.control;

import com.aresstack.comiccontrols.theme.ComicPalette;

import javax.swing.JComponent;
import javax.swing.JSplitPane;
import javax.swing.LookAndFeel;
import javax.swing.border.Border;
import javax.swing.plaf.BorderUIResource;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.UIResource;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * THE comic split-pane delegate — the drawer's divider for EVERY split pane, in both orientations: a quiet
 * fill, an ink center line along the divider and a small yellow grip pill in its middle; no bevel, no dots,
 * no look-and-feel border. Registered globally ({@code UIManager "SplitPaneUI"}) by the theme, and used by
 * {@link ComicSplitPane}. Colors are the living theme palette.
 */
public class ComicSplitPaneUI extends BasicSplitPaneUI {

    /** The divider thickness (slim, like the drawer's). */
    public static final int DIVIDER_THICKNESS = 7;

    private final ComicPalette palette;

    public ComicSplitPaneUI() {
        this(ComicPalette.defaultPalette());
    }

    public ComicSplitPaneUI(ComicPalette palette) {
        this.palette = palette == null ? ComicPalette.defaultPalette() : palette;
    }

    public static ComponentUI createUI(JComponent c) {
        return new ComicSplitPaneUI();
    }

    @Override
    protected void installDefaults() {
        super.installDefaults();
        LookAndFeel.installProperty(splitPane, "dividerSize", Integer.valueOf(DIVIDER_THICKNESS));
        if (splitPane.getDividerSize() == DIVIDER_THICKNESS) {
            dividerSize = DIVIDER_THICKNESS; // BasicSplitPaneUI lays out with its own cached size
            if (divider != null) {
                divider.setDividerSize(DIVIDER_THICKNESS);
            }
        }
        Border border = splitPane.getBorder();
        if (border == null || border instanceof UIResource) {
            splitPane.setBorder(new BorderUIResource.EmptyBorderUIResource(0, 0, 0, 0));
        }
        if (divider != null) {
            divider.setBorder(null);
        }
    }

    @Override
    public BasicSplitPaneDivider createDefaultDivider() {
        return new ComicDivider(this, palette);
    }

    /** The quiet comic divider: neutral fill, ink center line, small yellow grip pill (orientation-aware). */
    static final class ComicDivider extends BasicSplitPaneDivider {

        private static final long serialVersionUID = 1L;
        private final ComicPalette palette;

        ComicDivider(BasicSplitPaneUI ui, ComicPalette palette) {
            super(ui);
            this.palette = palette;
            setBorder(null);
        }

        @Override
        public void paint(Graphics g) {
            int width = getWidth();
            int height = getHeight();
            if (width <= 0 || height <= 0) {
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color base = splitPane != null ? splitPane.getBackground() : getBackground();
                g2.setColor(base != null ? base : palette.getSurface());
                g2.fillRect(0, 0, width, height);
                boolean sideBySide = splitPane == null
                        || splitPane.getOrientation() == JSplitPane.HORIZONTAL_SPLIT;
                paintLine(g2, sideBySide, sideBySide ? width : height, sideBySide ? height : width);
            } finally {
                g2.dispose();
            }
        }

        /**
         * Ink line along the divider and the yellow grip pill in its middle; {@code thickness} across the
         * divider, {@code length} along it ({@code vertical}: a left/right divider, else a top/bottom one).
         */
        private void paintLine(Graphics2D g2, boolean vertical, int thickness, int length) {
            int center = thickness / 2;
            int lineLength = Math.max(2, length - 16);
            g2.setColor(palette.getInk());
            if (vertical) {
                g2.fillRoundRect(center - 1, 8, 2, lineLength, 2, 2);
            } else {
                g2.fillRoundRect(8, center - 1, lineLength, 2, 2, 2);
            }
            int gripLength = Math.min(36, length / 3);
            if (gripLength > 10 && thickness >= 6) {
                int gripStart = (length - gripLength) / 2;
                g2.setColor(palette.getAccentYellow());
                if (vertical) {
                    g2.fillRoundRect(center - 2, gripStart, 5, gripLength, 5, 5);
                    g2.setColor(palette.getInk());
                    g2.drawRoundRect(center - 2, gripStart, 5, gripLength, 5, 5);
                } else {
                    g2.fillRoundRect(gripStart, center - 2, gripLength, 5, 5, 5);
                    g2.setColor(palette.getInk());
                    g2.drawRoundRect(gripStart, center - 2, gripLength, 5, 5, 5);
                }
            }
        }
    }
}
