package com.aresstack.ollamamanager.swing.component;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Dimension;

/**
 * Group of related values with a bold heading and an optional trailing component (for example a badge or a
 * button). Replaces titled borders so headings scale with the font and all groups share the same padding.
 */
public class CardPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private final JLabel heading = new JLabel();
    private final JPanel header = new JPanel(new BorderLayout(Spacing.GAP, 0));
    private final JPanel body = new JPanel(new BorderLayout());
    private JComponent trailing;

    public CardPanel(String title) {
        super(new BorderLayout(0, Spacing.GAP));
        heading.setText(title);
        header.setOpaque(false);
        header.add(heading, BorderLayout.CENTER);
        body.setOpaque(false);
        add(header, BorderLayout.NORTH);
        add(body, BorderLayout.CENTER);
        applyLook();
    }

    public CardPanel(String title, JComponent content) {
        this(title);
        setContent(content);
    }

    /** Replace the card content. */
    public final void setContent(JComponent content) {
        body.removeAll();
        body.add(content, BorderLayout.CENTER);
        body.revalidate();
    }

    /** Place {@code component} at the right end of the heading line. */
    public final void setTrailing(JComponent component) {
        if (trailing != null) {
            header.remove(trailing);
        }
        trailing = component;
        if (component != null) {
            header.add(component, BorderLayout.EAST);
        }
        header.revalidate();
        header.repaint();
    }

    public final void setTitle(String title) {
        heading.setText(title);
    }

    @Override
    public Dimension getMaximumSize() {
        // Let vertical box layouts keep cards at their preferred height instead of stretching them.
        Dimension preferred = getPreferredSize();
        return new Dimension(Integer.MAX_VALUE, preferred.height);
    }

    @Override
    public void updateUI() {
        super.updateUI();
        if (heading != null) {
            applyLook();
        }
    }

    private void applyLook() {
        heading.setFont(Theme.derived(1.1f, true));
        setBackground(Theme.background());
        setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Theme.lineColor()),
            BorderFactory.createEmptyBorder(Spacing.GAP + 2, Spacing.MARGIN - 4, Spacing.MARGIN - 4,
                Spacing.MARGIN - 4)));
    }
}
