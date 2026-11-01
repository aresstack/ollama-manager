package com.aresstack.comiccontrols.control;

import com.aresstack.comiccontrols.theme.ComicPalette;
import com.aresstack.comiccontrols.theme.ThemeColor;

import javax.swing.JComponent;
import java.awt.BasicStroke;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.function.IntConsumer;

/**
 * A comic two-or-more-way TOGGLE: one rounded pill with an ink contour, split into segments ("Text | Audio").
 * The selected segment is filled with the navigation blue and light text (like a selected
 * {@link ComicToggleButton}), an unselected one warms up yellow under the pointer. Clicking a segment
 * selects it and notifies the listener. Theme colors, live.
 */
public class ComicSegmentedToggle extends JComponent {

    private static final long serialVersionUID = 1L;
    private static final int HEIGHT = 24;
    private static final int SEGMENT_PADDING = 14;

    private final String[] labels;
    private final ComicPalette palette = ComicPalette.defaultPalette();
    private int toggleHeight = HEIGHT;
    private int selected;
    private int hovered = -1;
    private IntConsumer listener;

    public ComicSegmentedToggle(String... labels) {
        if (labels == null || labels.length < 2) {
            throw new IllegalArgumentException("a toggle needs at least two segments");
        }
        this.labels = labels.clone();
        setOpaque(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent event) {
                int at = segmentAt(event.getX());
                if (at != hovered) {
                    hovered = at;
                    repaint();
                }
            }

            @Override
            public void mouseExited(MouseEvent event) {
                hovered = -1;
                repaint();
            }

            @Override
            public void mousePressed(MouseEvent event) {
                int at = segmentAt(event.getX());
                if (at >= 0 && at != selected) {
                    setSelectedIndex(at);
                    if (listener != null) {
                        listener.accept(at);
                    }
                }
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
    }

    /** Called with the newly selected segment index after a user click. */
    public void setSelectionListener(IntConsumer listener) {
        this.listener = listener;
    }

    /** Select a segment programmatically (no listener call). */
    public void setSelectedIndex(int index) {
        if (index >= 0 && index < labels.length && index != selected) {
            selected = index;
            repaint();
        }
    }

    /** The toggle's height in pixels (default 24); a slim title-bar toggle uses less. */
    public void setToggleHeight(int height) {
        if (height > 0 && height != toggleHeight) {
            toggleHeight = height;
            revalidate();
            repaint();
        }
    }

    public int getSelectedIndex() {
        return selected;
    }

    /** The design font: it alone defines the segment widths, so a slim toggle only loses height. */
    private Font layoutFont() {
        Font font = getFont();
        return (font != null ? font : new Font(Font.SANS_SERIF, Font.PLAIN, 12)).deriveFont(Font.BOLD, DESIGN_SIZE);
    }

    /** The font the labels are drawn with: the design font, scaled down to fit a slim toggle. */
    private Font labelFont() {
        return layoutFont().deriveFont(fontSize());
    }

    private static final float DESIGN_SIZE = 12.5f;

    /**
     * 12.5 pt at the default height; a slim toggle scales the LABEL down with its height so the text
     * is never clipped (a 12 px title-bar toggle cut the 12.5 pt label at top and bottom). The width
     * stays that of the design size.
     */
    float fontSize() {
        return Math.min(DESIGN_SIZE, toggleHeight * FONT_TO_HEIGHT);
    }

    /** Bold label size relative to the toggle height: line height ~1.2 x size stays inside the plate. */
    private static final float FONT_TO_HEIGHT = 0.78f;

    private int segmentWidth(FontMetrics metrics, int index) {
        return metrics.stringWidth(labels[index]) + 2 * SEGMENT_PADDING;
    }

    private int segmentAt(int x) {
        FontMetrics metrics = getFontMetrics(layoutFont());
        int left = 0;
        for (int i = 0; i < labels.length; i++) {
            int width = segmentWidth(metrics, i);
            if (x >= left && x < left + width) {
                return i;
            }
            left += width;
        }
        return -1;
    }

    @Override
    public Dimension getPreferredSize() {
        FontMetrics metrics = getFontMetrics(layoutFont());
        int width = 0;
        for (int i = 0; i < labels.length; i++) {
            width += segmentWidth(metrics, i);
        }
        return new Dimension(width + 1, toggleHeight);
    }

    @Override
    public Dimension getMaximumSize() {
        return getPreferredSize();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            FontMetrics metrics = g2.getFontMetrics(layoutFont());
            g2.setFont(labelFont());
            FontMetrics label = g2.getFontMetrics();
            int h = Math.min(getHeight(), toggleHeight) - 2;
            int top = (getHeight() - h) / 2;
            RoundRectangle2D pill = new RoundRectangle2D.Float(1f, top, getWidth() - 2f, h, h, h);
            g2.setColor(ThemeColor.of("comic.buttonPlate"));
            g2.fill(pill);
            java.awt.Shape clip = g2.getClip();
            g2.clip(pill);
            int left = 0;
            for (int i = 0; i < labels.length; i++) {
                int width = segmentWidth(metrics, i);
                if (i == selected) {
                    g2.setColor(palette.getNavigationBlue());
                    g2.fillRect(left, top, width, h);
                } else if (i == hovered) {
                    g2.setColor(palette.getAccentYellow());
                    g2.fillRect(left, top, width, h);
                }
                left += width;
            }
            g2.setClip(clip);
            // segment separators and the contour
            g2.setColor(palette.getInk());
            g2.setStroke(new BasicStroke(1.6f));
            left = 0;
            for (int i = 0; i < labels.length - 1; i++) {
                left += segmentWidth(metrics, i);
                g2.drawLine(left, top + 1, left, top + h - 1);
            }
            g2.draw(pill);
            left = 0;
            for (int i = 0; i < labels.length; i++) {
                int width = segmentWidth(metrics, i);
                g2.setColor(i == selected ? ThemeColor.of("comic.buttonPlate") : palette.getInk());
                g2.drawString(labels[i], left + (width - label.stringWidth(labels[i])) / 2,
                        top + (h - label.getHeight()) / 2 + label.getAscent());
                left += width;
            }
        } finally {
            g2.dispose();
        }
    }
}
