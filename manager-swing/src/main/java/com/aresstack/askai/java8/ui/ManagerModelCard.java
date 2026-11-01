package com.aresstack.askai.java8.ui;

import com.aresstack.comiccontrols.control.ComicButton;
import com.aresstack.comiccontrols.theme.ThemeColor;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The model card of askai-java8 ({@code OllamaModelCard}) with the same layout, colours, status badge and
 * capability icons, but fed with plain text so Ollama Manager can use it, and with Load, Unload and Delete
 * actions. Lives in askai's package because {@link CapabilityIcons} is package-private there.
 */
public final class ManagerModelCard extends JPanel {

    private static final long serialVersionUID = 1L;

    /** One action button on the card. */
    public static final class Action {
        final String text;
        final String tooltip;
        final Runnable run;
        final boolean primary;
        final boolean destructive;

        private Action(String text, String tooltip, Runnable run, boolean primary, boolean destructive) {
            this.text = text;
            this.tooltip = tooltip;
            this.run = run;
            this.primary = primary;
            this.destructive = destructive;
        }

        public static Action primary(String text, String tooltip, Runnable run) {
            return new Action(text, tooltip, run, true, false);
        }

        public static Action normal(String text, String tooltip, Runnable run) {
            return new Action(text, tooltip, run, false, false);
        }

        public static Action destructive(String text, String tooltip, Runnable run) {
            return new Action(text, tooltip, run, false, true);
        }
    }

    private final JLabel capabilityIconLabel = new JLabel();
    private final List<JButton> buttons = new java.util.ArrayList<JButton>();
    private Set<ModelCapability> capabilities = EnumSet.noneOf(ModelCapability.class);

    public ManagerModelCard(String title, String line1, String line2, boolean running, Action... actions) {
        setLayout(new BorderLayout(12, 0));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ThemeColor.of("models.cardBorder")),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)));
        // A running model is told apart by the progress-bar accent and its badge; installed models carry no badge.
        setBackground(running ? runningBackground() : ThemeColor.of("models.cardBackground"));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 96));

        JPanel text = new JPanel(new GridLayout(0, 1, 0, 3));
        text.setOpaque(false);
        text.add(new JLabel(title));
        if (!line1.isEmpty()) {
            text.add(new JLabel(line1));
        }
        if (!line2.isEmpty()) {
            text.add(new JLabel(line2));
        }
        add(text, BorderLayout.CENTER);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
        capabilityIconLabel.setToolTipText("Model capabilities (from /api/show)");
        right.add(capabilityIconLabel);
        if (running) {
            JLabel status = new JLabel("RUNNING");
            status.setOpaque(true);
            status.setForeground(ThemeColor.of("bubble.userForeground"));
            status.setBackground(com.aresstack.ollamamanager.swing.component.Theme.runningAccent());
            status.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
            right.add(status);
        }
        for (final Action action : actions) {
            if (action == null) {
                continue;
            }
            JButton button = new ComicButton(action.text);
            button.setToolTipText(action.tooltip);
            if (action.primary) {
                button.setFont(button.getFont().deriveFont(Font.BOLD));
                button.setForeground(ThemeColor.of("models.primaryActionText"));
            } else if (action.destructive) {
                button.setForeground(ThemeColor.of("models.destructiveActionText"));
            }
            button.addActionListener(event -> action.run.run());
            buttons.add(button);
            right.add(button);
        }
        add(right, BorderLayout.EAST);
    }

    /** Card background of a running model: the progress-bar accent washed towards the card background. */
    private static java.awt.Color runningBackground() {
        return com.aresstack.ollamamanager.swing.component.Theme.blend(
                com.aresstack.ollamamanager.swing.component.Theme.runningAccent(),
                ThemeColor.of("models.cardBackground"), 0.35f);
    }

    /** Enable or disable all action buttons, for example while another model operation runs. */
    public void setActionsEnabled(boolean enabled) {
        for (JButton button : buttons) {
            button.setEnabled(enabled);
        }
    }

    /** @return the capabilities currently shown on the card */
    public Set<ModelCapability> shownCapabilities() {
        return capabilities;
    }

    /** Render the capability tags reported by {@code /api/show} as icons, exactly like askai. */
    public void setCapabilities(List<String> capabilityTags) {
        capabilities = ModelCapability.fromOllamaTags(capabilityTags);
        capabilityIconLabel.setIcon(capabilities.isEmpty() ? null : CapabilityIcons.forCapabilities(capabilities));
        capabilityIconLabel.setToolTipText(capabilities.isEmpty()
                ? "Model capabilities (from /api/show)" : ModelCapability.tooltipHtml(capabilities));
        revalidate();
        repaint();
    }

    /** Join non-empty parts with " | " as askai does. */
    public static String join(String... parts) {
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.trim().isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(" | ");
            }
            builder.append(part.trim());
        }
        return builder.toString();
    }

    public static String shortDate(String value) {
        return value == null || value.isEmpty() ? ""
                : value.substring(0, Math.min(19, value.length())).replace('T', ' ');
    }

    public static String shortDigest(String digest) {
        return digest == null || digest.isEmpty() ? ""
                : "digest " + (digest.length() <= 18 ? digest : digest.substring(0, 18) + "...");
    }

    public static String formatBytes(long value) {
        if (value <= 0L) {
            return "size unknown";
        }
        double size = value;
        String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int unit = 0;
        while (size >= 1024.0d && unit < units.length - 1) {
            size = size / 1024.0d;
            unit++;
        }
        return String.format("%.1f %s", size, units[unit]);
    }
}
