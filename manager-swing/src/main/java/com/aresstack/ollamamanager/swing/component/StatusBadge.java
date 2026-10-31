package com.aresstack.ollamamanager.swing.component;

import javax.swing.JLabel;
import javax.swing.Icon;
import javax.swing.SwingConstants;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * Label with a coloured dot that shows a state at a glance, for example "running", "stopped" or "external".
 * The dot scales with the font so it stays visible when the UI is enlarged.
 */
public final class StatusBadge extends JLabel {

    private static final long serialVersionUID = 1L;

    private Theme.Tone tone = Theme.Tone.NEUTRAL;
    private boolean pulsing;

    public StatusBadge() {
        this("—", Theme.Tone.NEUTRAL);
    }

    public StatusBadge(String text, Theme.Tone tone) {
        super(text);
        setHorizontalAlignment(SwingConstants.LEADING);
        setIconTextGap(6);
        setFocusable(true);
        show(text, tone);
    }

    /** Show {@code text} in the colour of {@code tone}. */
    public void show(String text, Theme.Tone tone) {
        this.tone = tone == null ? Theme.Tone.NEUTRAL : tone;
        setText(text == null || text.isEmpty() ? "—" : text);
        setIcon(new Dot());
        getAccessibleContext().setAccessibleDescription(this.tone.name().toLowerCase(java.util.Locale.ROOT));
        repaint();
    }

    /** Draw the dot hollow while something is in progress. */
    public void setPulsing(boolean pulsing) {
        this.pulsing = pulsing;
        repaint();
    }

    public Theme.Tone tone() {
        return tone;
    }

    @Override
    public void updateUI() {
        super.updateUI();
        if (tone != null) {
            setIcon(new Dot());
        }
    }

    private final class Dot implements Icon {
        private int size() {
            return Math.max(8, Math.round(getFont().getSize2D() * 0.7f));
        }

        @Override
        public void paintIcon(Component component, Graphics graphics, int x, int y) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color color = isEnabled() ? Theme.color(tone) : Theme.mutedForeground();
            int size = size();
            if (pulsing) {
                g.setColor(color);
                g.setStroke(new java.awt.BasicStroke(Math.max(1.5f, size / 5f)));
                g.drawOval(x + 1, y + 1, size - 2, size - 2);
            } else {
                g.setColor(color);
                g.fillOval(x, y, size, size);
            }
            g.dispose();
        }

        @Override
        public int getIconWidth() {
            return size();
        }

        @Override
        public int getIconHeight() {
            return size();
        }
    }
}
