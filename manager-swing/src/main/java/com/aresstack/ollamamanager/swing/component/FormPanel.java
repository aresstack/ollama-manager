package com.aresstack.ollamamanager.swing.component;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

/**
 * Two-column label/value form. Each row can carry a tooltip that explains the field; the label is bound to the
 * field so that its mnemonic and screen readers reach the right component.
 */
public final class FormPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private int row;

    public FormPanel() {
        super(new GridBagLayout());
        setOpaque(false);
    }

    /** Add a row and return the label for further customisation. */
    public JLabel addRow(String labelText, JComponent field, String tooltip) {
        JLabel label = new JLabel(labelText + ":");
        label.setLabelFor(field);
        if (tooltip != null && !tooltip.isEmpty()) {
            label.setToolTipText(tooltip);
            field.setToolTipText(tooltip);
        }
        GridBagConstraints left = new GridBagConstraints();
        left.gridx = 0;
        left.gridy = row;
        left.anchor = GridBagConstraints.LINE_START;
        left.insets = new Insets(2, 0, 2, Spacing.GAP);
        add(label, left);

        GridBagConstraints right = new GridBagConstraints();
        right.gridx = 1;
        right.gridy = row;
        right.weightx = 1;
        right.fill = GridBagConstraints.HORIZONTAL;
        right.insets = new Insets(2, 0, 2, 0);
        add(field, right);
        row++;
        return label;
    }

    /** Push all rows to the top. */
    public void addFiller() {
        GridBagConstraints filler = new GridBagConstraints();
        filler.gridy = row++;
        filler.weighty = 1;
        add(new JPanel(), filler);
    }
}
