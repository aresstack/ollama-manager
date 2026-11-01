package com.aresstack.ollamamanager.swing.component;

import javax.swing.JPanel;
import java.awt.BorderLayout;

/**
 * Body for an area that is not connected yet. It states plainly what is missing instead of showing fake data.
 */
public final class PlaceholderPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    public PlaceholderPanel(String message) {
        super(new BorderLayout());
        setOpaque(false);
        add(new EmptyState("Not available yet", message), BorderLayout.CENTER);
    }
}
