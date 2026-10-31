package com.aresstack.ollamamanager.swing.component;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Component;
import java.awt.GridBagLayout;

/**
 * Centred message for lists and sections that have nothing to show yet, with an optional next step. It says what
 * is missing and what to do, instead of showing an empty table.
 */
public final class EmptyState extends JPanel {

    private static final long serialVersionUID = 1L;

    private final JLabel title = new JLabel();
    private final JLabel detail = new JLabel();
    private final JPanel column = new JPanel();

    public EmptyState(String titleText, String detailText) {
        super(new GridBagLayout());
        setOpaque(false);
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
        column.setOpaque(false);
        column.setBorder(BorderFactory.createEmptyBorder(Spacing.MARGIN, Spacing.MARGIN, Spacing.MARGIN,
            Spacing.MARGIN));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        detail.setAlignmentX(Component.CENTER_ALIGNMENT);
        column.add(title);
        column.add(Box.createVerticalStrut(Spacing.GAP / 2));
        column.add(detail);
        add(column);
        setText(titleText, detailText);
        applyLook();
    }

    public void setText(String titleText, String detailText) {
        title.setText(titleText);
        detail.setText(detailText == null || detailText.isEmpty() ? " "
            : "<html><div style='text-align:center'>" + Html.escape(detailText) + "</div></html>");
    }

    /** Add a button or link below the text. */
    public void setAction(JComponent action) {
        if (action instanceof JButton) {
            ((JButton) action).setAlignmentX(Component.CENTER_ALIGNMENT);
        }
        column.add(Box.createVerticalStrut(Spacing.GAP));
        column.add(action);
        column.revalidate();
    }

    @Override
    public void updateUI() {
        super.updateUI();
        if (title != null) {
            applyLook();
        }
    }

    private void applyLook() {
        title.setFont(Theme.derived(1.15f, true));
        detail.setForeground(Theme.mutedForeground());
    }
}
