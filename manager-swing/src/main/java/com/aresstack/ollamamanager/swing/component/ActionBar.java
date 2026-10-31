package com.aresstack.ollamamanager.swing.component;

import javax.swing.AbstractButton;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.SwingConstants;
import java.awt.Dimension;

/**
 * Row of buttons with uniform gaps. Primary actions go on the left, destructive ones after a separator on the
 * right, so every section orders its actions the same way.
 */
public final class ActionBar extends JPanel {

    private static final long serialVersionUID = 1L;

    private final JPanel leading = new JPanel();
    private final JPanel trailing = new JPanel();

    public ActionBar() {
        setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
        setOpaque(false);
        leading.setLayout(new BoxLayout(leading, BoxLayout.X_AXIS));
        leading.setOpaque(false);
        trailing.setLayout(new BoxLayout(trailing, BoxLayout.X_AXIS));
        trailing.setOpaque(false);
        super.add(leading);
        super.add(Box.createHorizontalGlue());
        super.add(trailing);
    }

    /** Add {@code component} to the left group. */
    public ActionBar addAction(JComponent component) {
        append(leading, component);
        return this;
    }

    /** Add {@code component} to the right group, used for destructive actions such as Delete. */
    public ActionBar addTrailing(JComponent component) {
        append(trailing, component);
        return this;
    }

    /** Add a thin vertical separator to the left group. */
    public ActionBar addSeparator() {
        JSeparator separator = new JSeparator(SwingConstants.VERTICAL);
        int height = Theme.baseFont().getSize() * 2;
        separator.setMaximumSize(new Dimension(1, height));
        separator.setPreferredSize(new Dimension(1, height));
        separator.setAlignmentY(CENTER_ALIGNMENT);
        append(leading, separator);
        return this;
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
    }

    private static void append(JPanel group, JComponent component) {
        if (group.getComponentCount() > 0) {
            group.add(Box.createHorizontalStrut(Spacing.GAP));
        }
        if (component instanceof AbstractButton) {
            component.setAlignmentY(CENTER_ALIGNMENT);
        }
        group.add(component);
    }
}
