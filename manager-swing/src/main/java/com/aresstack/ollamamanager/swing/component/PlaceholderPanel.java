package com.aresstack.ollamamanager.swing.component;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;

/**
 * Body for an area that is not connected yet. It states plainly what is missing instead of showing fake data.
 */
public final class PlaceholderPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    public PlaceholderPanel(String message) {
        super(new BorderLayout());
        JLabel label = new JLabel("<html><div style='text-align:center'>" + Html.escape(message) + "</div></html>",
            SwingConstants.CENTER);
        label.setEnabled(false);
        add(label, BorderLayout.CENTER);
    }
}
