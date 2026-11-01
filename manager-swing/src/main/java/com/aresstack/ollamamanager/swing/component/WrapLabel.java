package com.aresstack.ollamamanager.swing.component;

import javax.swing.JTextArea;
import java.awt.Dimension;

/**
 * Read-only text that wraps at the width it is given, for descriptions and hints. Unlike an HTML label it
 * reports a bounded preferred width, so it never forces a horizontal scrollbar.
 */
public class WrapLabel extends JTextArea {

    private static final long serialVersionUID = 1L;

    public WrapLabel(String text) {
        super(text);
        setEditable(false);
        setLineWrap(true);
        setWrapStyleWord(true);
        setOpaque(false);
        setBorder(null);
        setFocusable(false);
        setFont(Theme.baseFont());
        setForeground(Theme.mutedForeground());
    }

    /** Hint text in the muted colour. */
    public static WrapLabel muted(String text) {
        return new WrapLabel(text);
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
    }

    @Override
    public void updateUI() {
        super.updateUI();
        setFont(Theme.baseFont());
    }
}
