package com.aresstack.comiccontrols.theme;

import javax.swing.AbstractButton;
import javax.swing.Icon;
import javax.swing.plaf.UIResource;
import java.awt.BasicStroke;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.io.Serializable;

/**
 * The radio mark of popup menu entries (e.g. the chat mode menu): an ink ring on the field paper, the chosen
 * entry filled with the navigation blue — instead of the look and feel's grey radio. Theme colors, live.
 */
final class ComicMenuRadioIcon implements Icon, UIResource, Serializable {

    private static final long serialVersionUID = 1L;
    private static final int SIZE = 11;

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        boolean selected = c instanceof AbstractButton && ((AbstractButton) c).isSelected();
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            ComicPalette palette = ComicPalette.defaultPalette();
            g2.setColor(ThemeColor.of("comic.fieldBg"));
            g2.fillOval(x, y, SIZE - 1, SIZE - 1);
            g2.setColor(palette.getInk());
            g2.setStroke(new BasicStroke(1.4f));
            g2.drawOval(x, y, SIZE - 1, SIZE - 1);
            if (selected) {
                g2.setColor(palette.getNavigationBlue());
                g2.fillOval(x + 3, y + 3, SIZE - 6, SIZE - 6);
            }
        } finally {
            g2.dispose();
        }
    }

    @Override
    public int getIconWidth() {
        return SIZE;
    }

    @Override
    public int getIconHeight() {
        return SIZE;
    }
}
