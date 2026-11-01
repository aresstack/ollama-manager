package com.aresstack.ollamamanager.swing.component;

import javax.swing.JTextField;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * Text field that paints a muted hint while it is empty, for example the default that applies when nothing is
 * entered. The hint is never part of the value.
 */
public class PlaceholderTextField extends JTextField {

    private static final long serialVersionUID = 1L;

    private String placeholder = "";

    public PlaceholderTextField(int columns) {
        super(columns);
    }

    public void setPlaceholder(String text) {
        placeholder = text == null ? "" : text;
        repaint();
    }

    public String placeholder() {
        return placeholder;
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        if (placeholder.isEmpty() || !getText().isEmpty()) {
            return;
        }
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Theme.mutedForeground());
        g.setFont(getFont());
        int baseline = getBaseline(getWidth(), getHeight());
        g.drawString(placeholder, getInsets().left, baseline > 0 ? baseline : getHeight() / 2);
        g.dispose();
    }
}
