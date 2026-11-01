package com.aresstack.comiccontrols.control;

import com.aresstack.comiccontrols.theme.ThemeColor;

import javax.swing.JButton;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;

/**
 * The small round HELP button (the MainframeMate help button): a filled navigation-blue circle with a bold
 * light "?" in the middle, hand cursor, no focus, clicks only inside the circle. Theme colors, live.
 */
public class ComicHelpButton extends JButton {

    private static final long serialVersionUID = 1L;

    public ComicHelpButton(int size, String tooltip) {
        super("?");
        Dimension dimension = new Dimension(size, size);
        setPreferredSize(dimension);
        setMinimumSize(dimension);
        setMaximumSize(dimension);
        setBorderPainted(false);
        setContentAreaFilled(false);
        setFocusPainted(false);
        setOpaque(false);
        setFocusable(false);
        setRolloverEnabled(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setToolTipText(tooltip);
        putClientProperty(ComicButtonUI.PLAIN_BUTTON_PROPERTY, Boolean.TRUE); // paints itself
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int diameter = Math.min(getWidth(), getHeight()) - 1;
            int x = (getWidth() - diameter) / 2;
            int y = (getHeight() - diameter) / 2;
            boolean hot = getModel().isRollover() || getModel().isPressed();
            g2.setColor(hot ? ThemeColor.of("comic.accentOrange") : ThemeColor.of("comic.navigationBlue"));
            g2.fill(new Ellipse2D.Double(x, y, diameter, diameter));
            g2.setColor(ThemeColor.of("comic.buttonPlate"));
            Font base = getFont() != null ? getFont() : new Font(Font.SANS_SERIF, Font.PLAIN, 12);
            g2.setFont(base.deriveFont(Font.BOLD, Math.max(12f, diameter * 0.6f)));
            FontMetrics metrics = g2.getFontMetrics();
            g2.drawString("?", (getWidth() - metrics.stringWidth("?")) / 2,
                    (getHeight() - metrics.getHeight()) / 2 + metrics.getAscent());
        } finally {
            g2.dispose();
        }
    }

    /** Clicks count only inside the circle. */
    @Override
    public boolean contains(int x, int y) {
        int radius = Math.min(getWidth(), getHeight()) / 2;
        int dx = x - getWidth() / 2;
        int dy = y - getHeight() / 2;
        return dx * dx + dy * dy <= radius * radius;
    }
}
