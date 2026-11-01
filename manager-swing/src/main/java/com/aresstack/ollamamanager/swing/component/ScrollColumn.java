package com.aresstack.ollamamanager.swing.component;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.LayoutManager;

/**
 * Panel for a vertical scroll pane: it always takes the viewport width, so content wraps instead of scrolling
 * sideways, and scrolls by sensible steps.
 */
public class ScrollColumn extends JPanel implements Scrollable {

    private static final long serialVersionUID = 1L;

    public ScrollColumn(LayoutManager layout) {
        super(layout);
        setOpaque(false);
    }

    /** Wrap {@code content} so it sticks to the top of a scroll pane and never scrolls horizontally. */
    public static JScrollPane wrap(javax.swing.JComponent content) {
        ScrollColumn column = new ScrollColumn(new BorderLayout());
        column.add(content, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(column);
        scroll.setBorder(null);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getViewport().setOpaque(false);
        scroll.setOpaque(false);
        return scroll;
    }

    /**
     * Lay out twice when wrapped text changed its height after receiving its width, so rows below it move down
     * instead of overlapping. One extra pass is enough; it never loops.
     */
    @Override
    protected void validateTree() {
        super.validateTree();
        if (wrappedTextChanged(this)) {
            invalidateAll(this);
            super.validateTree();
        }
    }

    private static boolean wrappedTextChanged(java.awt.Container container) {
        for (java.awt.Component child : container.getComponents()) {
            if (child instanceof javax.swing.JTextArea && ((javax.swing.JTextArea) child).getLineWrap()
                    && child.getPreferredSize().height != child.getHeight()) {
                return true;
            }
            if (child instanceof java.awt.Container && wrappedTextChanged((java.awt.Container) child)) {
                return true;
            }
        }
        return false;
    }

    private static void invalidateAll(java.awt.Container container) {
        for (java.awt.Component child : container.getComponents()) {
            if (child instanceof java.awt.Container) {
                invalidateAll((java.awt.Container) child);
            }
            child.invalidate();
        }
        container.invalidate();
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
        return Spacing.MARGIN;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
        return Math.max(Spacing.MARGIN, visible.height - Spacing.MARGIN);
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return false;
    }
}
