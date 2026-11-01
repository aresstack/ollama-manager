package com.aresstack.comiccontrols.control;

import com.aresstack.comiccontrols.theme.ComicPalette;
import com.aresstack.comiccontrols.theme.ThemeColor;

import javax.swing.JButton;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;

/**
 * A round comic window chip (Ollama Manager deviation from askai): a separate MINIMIZE chip (always blue,
 * light bar) or CLOSE chip (always red, light ✕). One plain click runs the action, without any delay.
 */
public final class ComicWindowChipButton extends JButton {

    private static final long serialVersionUID = 1L;

    public enum Kind { MINIMIZE, CLOSE }

    private final ComicPalette palette;
    private final Kind kind;

    public ComicWindowChipButton(ComicPalette palette, Kind kind, final Runnable action, int size) {
        if (palette == null || kind == null || action == null) {
            throw new IllegalArgumentException("palette, kind and action must not be null");
        }
        this.palette = palette;
        this.kind = kind;
        String text = kind == Kind.CLOSE ? "Close" : "Minimize";
        setToolTipText(text);
        getAccessibleContext().setAccessibleName(text);
        setPreferredSize(new Dimension(size, size));
        setMinimumSize(new Dimension(size, size));
        setMaximumSize(new Dimension(size, size));
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setFocusable(false);
        setOpaque(false);
        setRolloverEnabled(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addActionListener(e -> action.run());
    }

    public Kind kind() {
        return kind;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Ellipse2D chip = new Ellipse2D.Float(2f, 2f, getWidth() - 5f, getHeight() - 5f);
            Color fill = kind == Kind.CLOSE ? palette.getAccentRed() : palette.getNavigationBlue();
            if (getModel().isPressed()) {
                fill = fill.darker();
            } else if (getModel().isRollover()) {
                fill = fill.brighter();
            }
            g2.setColor(fill);
            g2.fill(chip);
            g2.setColor(palette.getInk());
            g2.setStroke(new BasicStroke(1.6f));
            g2.draw(chip);
            g2.setColor(ThemeColor.of("comic.buttonPlate"));
            g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            int cx = getWidth() / 2;
            int cy = getHeight() / 2;
            int arm = Math.max(3, getWidth() / 6);
            if (kind == Kind.CLOSE) {
                g2.drawLine(cx - arm, cy - arm, cx + arm, cy + arm);
                g2.drawLine(cx + arm, cy - arm, cx - arm, cy + arm);
            } else {
                g2.drawLine(cx - arm, cy, cx + arm, cy);
            }
        } finally {
            g2.dispose();
        }
    }
}
