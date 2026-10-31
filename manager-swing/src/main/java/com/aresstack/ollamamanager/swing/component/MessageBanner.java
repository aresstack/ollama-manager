package com.aresstack.ollamamanager.swing.component;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.FlowLayout;

/**
 * Inline notice that stays in the layout instead of a modal dialog: a tinted bar with the message, an optional
 * action button and an optional Dismiss button. Hidden while there is nothing to say. Long messages wrap, so
 * place the banner where its width is fixed, for example in the NORTH slot of a border layout.
 */
public final class MessageBanner extends JPanel {

    private static final long serialVersionUID = 1L;

    private final JTextArea text = new JTextArea();
    private final JPanel buttons = new JPanel(new FlowLayout(FlowLayout.TRAILING, Spacing.GAP, 0));
    private final JButton dismiss = new JButton("Dismiss");
    private Theme.Tone tone = Theme.Tone.INFO;

    public MessageBanner() {
        super(new BorderLayout(Spacing.GAP, 0));
        text.setEditable(false);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setOpaque(false);
        text.setBorder(null);
        text.setFocusable(true);
        buttons.setOpaque(false);
        dismiss.addActionListener(event -> clear());
        add(text, BorderLayout.CENTER);
        add(buttons, BorderLayout.EAST);
        setVisible(false);
        applyTone();
    }

    /** Show {@code message} with the colour of {@code tone}; keeps the current action buttons. */
    public void show(Theme.Tone tone, String message) {
        this.tone = tone == null ? Theme.Tone.INFO : tone;
        text.setText(message == null ? "" : message);
        applyTone();
        setVisible(message != null && !message.isEmpty());
        revalidate();
        repaint();
    }

    public void info(String message) {
        show(Theme.Tone.INFO, message);
    }

    public void warning(String message) {
        show(Theme.Tone.WARNING, message);
    }

    public void error(String message) {
        show(Theme.Tone.ERROR, message);
    }

    /** Show an error built from {@code error} with the uniform wording of {@link ErrorText}. */
    public void error(String action, Throwable error) {
        show(Theme.Tone.ERROR, action + " failed. " + ErrorText.describe(error));
    }

    /** Hide the banner and drop its action buttons. */
    public void clear() {
        buttons.removeAll();
        setVisible(false);
        revalidate();
        repaint();
    }

    /** Replace the action buttons by {@code action}; pass {@code dismissible} to add a Dismiss button. */
    public void setActions(JButton action, boolean dismissible) {
        buttons.removeAll();
        if (action != null) {
            buttons.add(action);
        }
        if (dismissible) {
            buttons.add(dismiss);
        }
        buttons.revalidate();
    }

    public Theme.Tone tone() {
        return tone;
    }

    public String message() {
        return text.getText();
    }

    @Override
    public void updateUI() {
        super.updateUI();
        if (text != null) {
            applyTone();
        }
    }

    private void applyTone() {
        setBackground(Theme.tint(tone));
        setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 3, 0, 0, Theme.color(tone)),
            BorderFactory.createEmptyBorder(Spacing.GAP, Spacing.GAP, Spacing.GAP, Spacing.GAP)));
        text.setFont(Theme.baseFont());
        text.setForeground(Theme.foreground());
    }
}
