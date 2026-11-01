package com.aresstack.comiccontrols.control;

import com.aresstack.comiccontrols.theme.AskAiTheme;
import com.aresstack.comiccontrols.theme.ComicPalette;

import javax.swing.JComponent;
import javax.swing.LookAndFeel;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.UIResource;
import javax.swing.plaf.basic.BasicTabbedPaneUI;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;

/**
 * The comic tab delegate: every tab is a rounded plate with an ink contour like {@link ComicButton} — the
 * resting {@code comic.buttonPlate}, the warm focus cream under the pointer, the SELECTED tab in
 * {@code comic.accentYellow} — over a {@code comic.surface} content area with an ink contour. Keyboard
 * focus shows as a dashed navigation-blue ring inside the selected tab. Behavior (mnemonics, scrolling
 * tab runs, keyboard) stays {@link BasicTabbedPaneUI}'s. Colors are read at paint time.
 */
public class ComicTabbedPaneUI extends BasicTabbedPaneUI {

    private static final int ARC = 10;
    private static final float OUTLINE = 1.6f;

    public static ComponentUI createUI(JComponent c) {
        return new ComicTabbedPaneUI();
    }

    @Override
    protected void installDefaults() {
        super.installDefaults();
        tabInsets = new Insets(4, 12, 4, 12);
        selectedTabPadInsets = new Insets(0, 0, 0, 0);
        tabAreaInsets = new Insets(4, 6, 0, 6);
        contentBorderInsets = new Insets(2, 2, 2, 2);
        LookAndFeel.installProperty(tabPane, "opaque", Boolean.TRUE);
        if (tabPane.getBackground() == null || tabPane.getBackground() instanceof UIResource) {
            tabPane.setBackground(new ColorUIResource(palette().getSurface()));
        }
        if (tabPane.getForeground() == null || tabPane.getForeground() instanceof UIResource) {
            tabPane.setForeground(new ColorUIResource(palette().getInk()));
        }
    }

    /** Pure: the plate color of a tab in its state. */
    static Color tabFill(AskAiTheme theme, boolean enabled, boolean selected, boolean rollover) {
        ComicPalette palette = theme.comicPalette();
        if (!enabled) {
            return palette.getSurface();
        }
        if (selected) {
            return palette.getAccentYellow();
        }
        return rollover ? theme.warmFocus() : theme.buttonPlate();
    }

    @Override
    protected void paintTabBackground(Graphics g, int tabPlacement, int tabIndex, int x, int y, int w, int h,
                                      boolean isSelected) {
        Graphics2D g2 = smooth(g);
        try {
            g2.setColor(tabFill(AskAiTheme.active(), tabPane.isEnabledAt(tabIndex), isSelected,
                    tabIndex == getRolloverTab()));
            g2.fill(plate(x, y, w, h));
        } finally {
            g2.dispose();
        }
    }

    @Override
    protected void paintTabBorder(Graphics g, int tabPlacement, int tabIndex, int x, int y, int w, int h,
                                  boolean isSelected) {
        Graphics2D g2 = smooth(g);
        try {
            Color ink = palette().getInk();
            g2.setColor(tabPane.isEnabledAt(tabIndex) ? ink : ink.brighter().brighter());
            g2.setStroke(new BasicStroke(OUTLINE));
            g2.draw(plate(x, y, w, h));
        } finally {
            g2.dispose();
        }
    }

    @Override
    protected void paintFocusIndicator(Graphics g, int tabPlacement, Rectangle[] rects, int tabIndex,
                                       Rectangle iconRect, Rectangle textRect, boolean isSelected) {
        if (!tabPane.hasFocus() || !isSelected || tabIndex < 0 || tabIndex >= rects.length) {
            return;
        }
        Rectangle r = rects[tabIndex];
        Graphics2D g2 = smooth(g);
        try {
            g2.setColor(palette().getNavigationBlue());
            g2.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f,
                    new float[]{3f, 2f}, 0f));
            g2.draw(new RoundRectangle2D.Float(r.x + 4f, r.y + 4f, r.width - 8f, r.height - 8f, ARC - 4, ARC - 4));
        } finally {
            g2.dispose();
        }
    }

    @Override
    protected void paintContentBorder(Graphics g, int tabPlacement, int selectedIndex) {
        Rectangle area = contentArea(tabPlacement);
        Graphics2D g2 = smooth(g);
        try {
            g2.setColor(palette().getSurface());
            g2.fill(new RoundRectangle2D.Float(area.x, area.y, area.width, area.height, ARC, ARC));
            g2.setColor(palette().getInk());
            g2.setStroke(new BasicStroke(OUTLINE));
            g2.draw(new RoundRectangle2D.Float(area.x + 0.8f, area.y + 0.8f, area.width - 1.6f, area.height - 1.6f,
                    ARC, ARC));
        } finally {
            g2.dispose();
        }
    }

    @Override
    protected int getTabLabelShiftY(int tabPlacement, int tabIndex, boolean isSelected) {
        return 0; // plates do not jump
    }

    private Rectangle contentArea(int tabPlacement) {
        int width = tabPane.getWidth();
        int height = tabPane.getHeight();
        Insets insets = tabPane.getInsets();
        int x = insets.left;
        int y = insets.top;
        int w = width - insets.right - insets.left;
        int h = height - insets.top - insets.bottom;
        int runs = runCount;
        switch (tabPlacement) {
            case LEFT:
                int tabW = calculateTabAreaWidth(tabPlacement, runs, maxTabWidth);
                x += tabW;
                w -= tabW;
                break;
            case RIGHT:
                w -= calculateTabAreaWidth(tabPlacement, runs, maxTabWidth);
                break;
            case BOTTOM:
                h -= calculateTabAreaHeight(tabPlacement, runs, maxTabHeight);
                break;
            case TOP:
            default:
                int tabH = calculateTabAreaHeight(tabPlacement, runs, maxTabHeight);
                y += tabH;
                h -= tabH;
        }
        return new Rectangle(x, y, Math.max(0, w), Math.max(0, h));
    }

    private static RoundRectangle2D plate(int x, int y, int w, int h) {
        return new RoundRectangle2D.Float(x + 1f, y + 1f, w - 2f, h - 2f, ARC, ARC);
    }

    private static ComicPalette palette() {
        return AskAiTheme.active().comicPalette();
    }

    private static Graphics2D smooth(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        return g2;
    }
}
