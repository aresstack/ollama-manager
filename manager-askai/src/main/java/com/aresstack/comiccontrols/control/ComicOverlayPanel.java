package com.aresstack.comiccontrols.control;

import com.aresstack.comiccontrols.theme.AskAiTheme;
import com.aresstack.comiccontrols.theme.ComicPalette;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.geom.Ellipse2D;

/**
 * A comic OVERLAY: a dimmed backdrop with a centered rounded plate (ink contour) carrying a title
 * row and arbitrary content, closable via the round comic ✕ in the top-right corner (ink outline,
 * red on hover). The backdrop swallows all mouse events, so whatever lies underneath (e.g. the
 * chat transcript) is untouchable while the overlay is up. Meant to fill its host component — the
 * host adds it on a higher layer and removes it again through the close callback.
 */
public class ComicOverlayPanel extends JPanel {

    private static final int MARGIN = 24;

    private final ComicPalette palette;
    private final CloseButton closeButton;
    private final JPanel plate;
    private Dimension plateLimit;

    public ComicOverlayPanel(String title, JComponent content, Runnable closeAction) {
        this(title, content, closeAction, ComicPalette.defaultPalette());
    }

    public ComicOverlayPanel(String title, JComponent content, Runnable closeAction,
                             ComicPalette palette) {
        super(new GridBagLayout());
        if (content == null || closeAction == null || palette == null) {
            throw new IllegalArgumentException("content, closeAction and palette must not be null");
        }
        this.palette = palette;
        setOpaque(false);
        // Swallow every mouse event so the content BELOW the overlay is unreachable.
        addMouseListener(new MouseAdapter() { });
        addMouseMotionListener(new javax.swing.event.MouseInputAdapter() { });
        addMouseWheelListener(e -> { });

        plate = new ComicSectionPanel(palette);
        plate.setLayout(new BorderLayout(0, 6));
        plate.setBorder(BorderFactory.createEmptyBorder(10, 14, 12, 14));

        JLabel heading = new JLabel(title == null ? "" : title);
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 13f));
        heading.setForeground(palette.getInk());
        JPanel titleRow = new JPanel(new BorderLayout(8, 0));
        titleRow.setOpaque(false);
        titleRow.add(heading, BorderLayout.CENTER);
        closeButton = new CloseButton(palette, closeAction);
        titleRow.add(closeButton, BorderLayout.EAST);
        plate.add(titleRow, BorderLayout.NORTH);
        plate.add(content, BorderLayout.CENTER);

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.fill = GridBagConstraints.BOTH;
        constraints.weightx = 1;
        constraints.weighty = 1;
        constraints.insets = new java.awt.Insets(MARGIN, MARGIN, MARGIN, MARGIN);
        add(plate, constraints);
    }

    /**
     * Caps the plate at {@code limit} (centered, backdrop all around) when the overlay is larger, e.g. when it
     * covers a whole maximized window; {@code null} lets the plate fill the overlay minus the margin (default).
     */
    public void setPlateLimit(Dimension limit) {
        this.plateLimit = limit == null ? null : new Dimension(limit);
        revalidate();
    }

    @Override
    public void doLayout() {
        int horizontal = MARGIN;
        int vertical = MARGIN;
        if (plateLimit != null) {
            horizontal = Math.max(MARGIN, (getWidth() - plateLimit.width) / 2);
            vertical = Math.max(MARGIN, (getHeight() - plateLimit.height) / 2);
        }
        GridBagLayout layout = (GridBagLayout) getLayout();
        GridBagConstraints constraints = layout.getConstraints(plate);
        constraints.insets = new java.awt.Insets(vertical, horizontal, vertical, horizontal);
        layout.setConstraints(plate, constraints);
        super.doLayout();
    }

    /** The overlay's ✕ (for tests and keyboard wiring). */
    public CloseButton closeButton() {
        return closeButton;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setColor(AskAiTheme.active().overlayDim()); // the dimmed backdrop
            g2.fillRect(0, 0, getWidth(), getHeight());
        } finally {
            g2.dispose();
        }
        super.paintComponent(g);
    }

    /** The stylish comic ✕: a round ink-outlined chip, white at rest, RED (critical) on hover. */
    public static final class CloseButton extends JButton {

        private final ComicPalette palette;

        public CloseButton(ComicPalette palette, final Runnable closeAction) {
            this.palette = palette;
            setToolTipText("Close");
            setFocusable(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setRolloverEnabled(true);
            addActionListener(e -> closeAction.run());
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(24, 24);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                Ellipse2D chip = new Ellipse2D.Float(2f, 2f, getWidth() - 5f, getHeight() - 5f);
                boolean hot = getModel().isRollover() || getModel().isPressed();
                g2.setColor(hot ? palette.getAccentRed() : AskAiTheme.active().buttonPlate());
                g2.fill(chip);
                g2.setColor(palette.getInk());
                g2.setStroke(new BasicStroke(1.6f));
                g2.draw(chip);
                // the ✕ strokes
                g2.setColor(hot ? AskAiTheme.active().buttonPlate() : palette.getInk());
                g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                int cx = getWidth() / 2;
                int cy = getHeight() / 2;
                g2.drawLine(cx - 4, cy - 4, cx + 4, cy + 4);
                g2.drawLine(cx + 4, cy - 4, cx - 4, cy + 4);
            } finally {
                g2.dispose();
            }
        }
    }

}
