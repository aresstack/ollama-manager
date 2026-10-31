package com.aresstack.ollamamanager.swing.component;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;

/**
 * Common frame for every section: title, one-line description, optional toolbar and content.
 * Fonts are derived from the look-and-feel label font so that font scaling applies.
 */
public class SectionPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private final JLabel title = new JLabel();
    private final JLabel description = new JLabel();
    private final JPanel toolbar = new JPanel();

    public SectionPanel(String titleText, String descriptionText) {
        super(new BorderLayout(0, Spacing.GAP));
        setBorder(BorderFactory.createEmptyBorder(Spacing.MARGIN, Spacing.MARGIN, Spacing.MARGIN, Spacing.MARGIN));
        title.setText(titleText);
        description.setText(descriptionText);
        toolbar.setLayout(new BoxLayout(toolbar, BoxLayout.X_AXIS));
        toolbar.setOpaque(false);

        JPanel header = new JPanel(new BorderLayout(Spacing.GAP, 2));
        header.setOpaque(false);
        JPanel texts = new JPanel(new BorderLayout(0, 2));
        texts.setOpaque(false);
        texts.add(title, BorderLayout.NORTH);
        texts.add(description, BorderLayout.CENTER);
        header.add(texts, BorderLayout.CENTER);
        header.add(toolbar, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);
        applyFonts();
    }

    /** Add a toolbar component on the right side of the header. */
    public void addToolbarItem(JComponent component) {
        if (toolbar.getComponentCount() > 0) {
            toolbar.add(Box.createHorizontalStrut(Spacing.GAP));
        }
        toolbar.add(component);
    }

    /** Set the section body. */
    public void setBody(JComponent body) {
        add(body, BorderLayout.CENTER);
    }

    @Override
    public void updateUI() {
        super.updateUI();
        if (title != null) {
            applyFonts();
        }
    }

    private void applyFonts() {
        title.setFont(Theme.derived(1.5f, true));
        description.setFont(Theme.baseFont());
        description.setForeground(Theme.mutedForeground());
    }
}
