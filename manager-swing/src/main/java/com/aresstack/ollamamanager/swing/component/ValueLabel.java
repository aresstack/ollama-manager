package com.aresstack.ollamamanager.swing.component;

import javax.swing.JTextField;

/**
 * Read-only, focusable value display. Unlike a plain label it is reachable with Tab and its text can be copied.
 */
public final class ValueLabel extends JTextField {

    private static final long serialVersionUID = 1L;

    public ValueLabel() {
        this("—");
    }

    public ValueLabel(String text) {
        super(text);
        setEditable(false);
        setBorder(null);
        setOpaque(false);
    }

    /** Show {@code text}, or a dash when it is empty. */
    public void show(String text) {
        setText(text == null || text.isEmpty() ? "—" : text);
        setCaretPosition(0);
    }
}
