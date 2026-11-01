package com.aresstack.comiccontrols.control;

import com.aresstack.comiccontrols.theme.AskAiTheme;
import com.aresstack.comiccontrols.theme.ComicPalette;

import javax.swing.JComponent;
import javax.swing.JProgressBar;
import javax.swing.LookAndFeel;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.UIResource;
import javax.swing.plaf.basic.BasicProgressBarUI;
import java.awt.BasicStroke;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.RoundRectangle2D;

/**
 * The comic progress bar: a rounded {@code comic.fieldBg} track with an ink contour, filled with
 * {@code comic.accentOrange} (determinate) or a moving orange plate (indeterminate); the optional
 * progress string in ink. Colors are read at paint time.
 */
public class ComicProgressBarUI extends BasicProgressBarUI {

    private static final float OUTLINE = 1.4f;

    public static ComponentUI createUI(JComponent c) {
        return new ComicProgressBarUI();
    }

    @Override
    protected void installDefaults() {
        super.installDefaults();
        LookAndFeel.installProperty(progressBar, "opaque", Boolean.FALSE);
        if (progressBar.getBorder() == null || progressBar.getBorder() instanceof UIResource) {
            progressBar.setBorder(null);
        }
        if (progressBar.getForeground() == null || progressBar.getForeground() instanceof UIResource) {
            progressBar.setForeground(new ColorUIResource(palette().getAccentOrange()));
        }
    }

    @Override
    protected Dimension getPreferredInnerHorizontal() {
        Dimension size = super.getPreferredInnerHorizontal();
        return new Dimension(size.width, Math.max(size.height, 14));
    }

    @Override
    protected void paintDeterminate(Graphics g, JComponent c) {
        Rectangle box = innerBox();
        if (box.isEmpty()) {
            return;
        }
        int filled = getAmountFull(progressBar.getInsets(), box.width, box.height);
        Graphics2D g2 = smooth(g);
        try {
            paintTrack(g2, box);
            Shape clip = g2.getClip();
            g2.clip(track(box));
            g2.setColor(progressBar.getForeground());
            if (progressBar.getOrientation() == JProgressBar.HORIZONTAL) {
                boolean leftToRight = progressBar.getComponentOrientation().isLeftToRight();
                int x = leftToRight ? box.x : box.x + box.width - filled;
                g2.fillRect(x, box.y, filled, box.height);
            } else {
                g2.fillRect(box.x, box.y + box.height - filled, box.width, filled);
            }
            g2.setClip(clip);
            paintOutline(g2, box);
            if (progressBar.isStringPainted()) {
                paintCenteredString(g2, box);
            }
        } finally {
            g2.dispose();
        }
    }

    @Override
    protected void paintIndeterminate(Graphics g, JComponent c) {
        Rectangle box = innerBox();
        if (box.isEmpty()) {
            return;
        }
        Graphics2D g2 = smooth(g);
        try {
            paintTrack(g2, box);
            boxRect = getBox(boxRect);
            if (boxRect != null) {
                Shape clip = g2.getClip();
                g2.clip(track(box));
                g2.setColor(progressBar.getForeground());
                g2.fill(new RoundRectangle2D.Float(boxRect.x, boxRect.y, boxRect.width, boxRect.height,
                        boxRect.height, boxRect.height));
                g2.setClip(clip);
            }
            paintOutline(g2, box);
            if (progressBar.isStringPainted()) {
                paintCenteredString(g2, box);
            }
        } finally {
            g2.dispose();
        }
    }

    private void paintTrack(Graphics2D g2, Rectangle box) {
        g2.setColor(progressBar.isEnabled() ? AskAiTheme.active().fieldBg() : palette().getSurface());
        g2.fill(track(box));
    }

    private void paintOutline(Graphics2D g2, Rectangle box) {
        g2.setColor(palette().getInk());
        g2.setStroke(new BasicStroke(OUTLINE));
        g2.draw(track(box));
    }

    private void paintCenteredString(Graphics2D g2, Rectangle box) {
        String text = progressBar.getString();
        if (text == null || text.isEmpty()) {
            return;
        }
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setFont(progressBar.getFont());
        g2.setColor(palette().getInk());
        FontMetrics metrics = g2.getFontMetrics();
        g2.drawString(text, box.x + (box.width - metrics.stringWidth(text)) / 2,
                box.y + (box.height - metrics.getHeight()) / 2 + metrics.getAscent());
    }

    private Rectangle innerBox() {
        Insets insets = progressBar.getInsets();
        return new Rectangle(insets.left, insets.top,
                progressBar.getWidth() - insets.left - insets.right,
                progressBar.getHeight() - insets.top - insets.bottom);
    }

    private static RoundRectangle2D track(Rectangle box) {
        float arc = Math.min(box.width, box.height);
        return new RoundRectangle2D.Float(box.x + 0.7f, box.y + 0.7f, box.width - 1.4f, box.height - 1.4f,
                arc, arc);
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
