package com.aresstack.comiccontrols.border;

import com.aresstack.comiccontrols.theme.AskAiTheme;

import javax.swing.plaf.UIResource;
import java.awt.BasicStroke;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;

/**
 * The rounded contour of comic input fields: the quiet {@code comic.fieldBorder} line at rest, the
 * {@code comic.navigationBlue} focus contour while the field owns the keyboard focus. Colors are read
 * from the active theme at paint time. A {@link UIResource}, so the field delegates may replace it.
 */
public final class ComicFieldBorder implements javax.swing.border.Border, UIResource {

    /** The rounding shared by fields, combos and spinners. */
    public static final int ARC = 10;

    private final Insets padding;

    public ComicFieldBorder() {
        this(new Insets(4, 8, 4, 8));
    }

    public ComicFieldBorder(Insets padding) {
        this.padding = new Insets(padding.top, padding.left, padding.bottom, padding.right);
    }

    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            boolean focused = c.isEnabled() && c.isFocusOwner();
            AskAiTheme theme = AskAiTheme.active();
            g2.setColor(focused ? theme.comicPalette().getNavigationBlue() : theme.fieldBorder());
            float stroke = focused ? 1.6f : 1.2f;
            g2.setStroke(new BasicStroke(stroke));
            float inset = stroke / 2f;
            g2.draw(new RoundRectangle2D.Float(x + inset, y + inset, width - stroke, height - stroke, ARC, ARC));
        } finally {
            g2.dispose();
        }
    }

    @Override
    public Insets getBorderInsets(Component c) {
        return new Insets(padding.top + 1, padding.left + 1, padding.bottom + 1, padding.right + 1);
    }

    @Override
    public boolean isBorderOpaque() {
        return false;
    }
}
