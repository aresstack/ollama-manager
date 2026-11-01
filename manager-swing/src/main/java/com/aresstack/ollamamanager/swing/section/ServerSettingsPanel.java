package com.aresstack.ollamamanager.swing.section;

import com.aresstack.ollamamanager.domain.server.Endpoint;
import com.aresstack.ollamamanager.domain.server.KvCacheType;
import com.aresstack.ollamamanager.domain.server.ServerSetting;
import com.aresstack.ollamamanager.domain.server.ServerSettingCatalog;
import com.aresstack.ollamamanager.swing.component.ActionBar;
import com.aresstack.ollamamanager.swing.component.CardPanel;
import com.aresstack.ollamamanager.swing.component.Dialogs;
import com.aresstack.ollamamanager.swing.component.Html;
import com.aresstack.ollamamanager.swing.component.MessageBanner;
import com.aresstack.ollamamanager.swing.component.PlaceholderTextField;
import com.aresstack.ollamamanager.swing.component.ScrollColumn;
import com.aresstack.ollamamanager.swing.component.SectionPanel;
import com.aresstack.ollamamanager.swing.component.Spacing;
import com.aresstack.ollamamanager.swing.component.StatusBadge;
import com.aresstack.ollamamanager.swing.component.Theme;
import com.aresstack.ollamamanager.swing.component.WrapLabel;
import com.aresstack.ollamamanager.swing.shell.Section;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.border.Border;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Editor for the desired server-wide settings, grouped by topic. Every field shows what it does, what Ollama
 * uses when it is left at the default, which unit it takes and, next to it, what the running server uses.
 * Saving only stores the settings; a running owned server picks them up after a restart.
 */
public final class ServerSettingsPanel extends SectionPanel implements ServerSettingsView {

    private static final long serialVersionUID = 1L;
    static final String DEFAULT_CHOICE = "(Ollama default)";

    /** What Ollama uses when a setting is not set. Shown as placeholder or hint so an empty field is explained. */
    static final Map<String, String> DEFAULTS = defaults();

    private final Map<ServerSetting<?>, Editor> editors = new LinkedHashMap<ServerSetting<?>, Editor>();
    private final Map<ServerSetting<?>, Row> rows = new LinkedHashMap<ServerSetting<?>, Row>();
    private final MessageBanner notice = new MessageBanner();
    private final MessageBanner appliedSource = new MessageBanner();
    private final JButton save = new JButton("Save");
    private final JButton revert = new JButton("Revert");
    private final JButton reload = new JButton("Reload");
    private final WrapLabel legend = new WrapLabel("");

    public ServerSettingsPanel(ServerSettingsPresenter presenter) {
        super(Section.SERVER_SETTINGS.title(), Section.SERVER_SETTINGS.description());
        for (ServerSetting<?> setting : ServerSettingCatalog.all()) {
            Editor editor = editorFor(setting);
            editors.put(setting, editor);
            rows.put(setting, new Row(setting, editor));
        }

        JPanel column = new JPanel();
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
        column.setOpaque(false);
        appliedSource.setAlignmentX(LEFT_ALIGNMENT);
        column.add(appliedSource);
        column.add(Box.createVerticalStrut(Spacing.GAP));
        column.add(card("Network", "Where the server listens. Clients must use the same address.",
            ServerSettingCatalog.BIND));
        column.add(Box.createVerticalStrut(Spacing.GAP));
        column.add(card("Requests and memory",
            "Each parallel request and each loaded model reserves memory on the GPU. Context memory grows with"
                + " parallel requests × context length; lower precision halves or quarters it.",
            ServerSettingCatalog.PARALLELISM, ServerSettingCatalog.LOADED_MODELS, ServerSettingCatalog.QUEUE,
            ServerSettingCatalog.KV_CACHE, ServerSettingCatalog.FLASH_ATTENTION));
        column.add(Box.createVerticalStrut(Spacing.GAP));
        column.add(card("Context", "How much conversation a model sees when the client does not say.",
            ServerSettingCatalog.CONTEXT_LENGTH));
        column.add(Box.createVerticalStrut(Spacing.GAP));
        column.add(card("Models", "Where models live and how long they stay in memory.",
            ServerSettingCatalog.KEEP_ALIVE, ServerSettingCatalog.MODEL_ROOT));
        column.add(Box.createVerticalStrut(Spacing.GAP));
        legend.setText("All settings here need a server restart. Save stores them; the next start, or a restart"
            + " from the Profiles section, applies them. A setting is only passed to the server when its"
            + " \"Set\" box is ticked (or a field is filled, or a choice is made); otherwise the variable stays unset"
            + " and Ollama uses its built-in value. A value you set is passed even if it equals the default.");
        legend.setAlignmentX(LEFT_ALIGNMENT);
        column.add(legend);

        alignLabels();
        JScrollPane scroll = ScrollColumn.wrap(column);

        save.setMnemonic('S');
        save.setToolTipText("Store the settings. A running server applies them after a restart.");
        revert.setMnemonic('V');
        revert.setToolTipText("Discard unsaved edits.");
        reload.setMnemonic('L');
        reload.setToolTipText("Load the stored settings again.");
        save.addActionListener(event -> presenter.save());
        revert.addActionListener(event -> presenter.revert());
        reload.addActionListener(event -> presenter.load());
        addToolbarItem(reload);

        ActionBar buttons = new ActionBar().addAction(save).addAction(revert);
        JPanel south = new JPanel(new BorderLayout(0, Spacing.GAP));
        south.setOpaque(false);
        south.setBorder(BorderFactory.createEmptyBorder(Spacing.GAP, 0, 0, 0));
        south.add(notice, BorderLayout.NORTH);
        south.add(buttons, BorderLayout.CENTER);

        JPanel body = new JPanel(new BorderLayout(0, Spacing.GAP));
        body.setOpaque(false);
        body.add(scroll, BorderLayout.CENTER);
        body.add(south, BorderLayout.SOUTH);
        setBody(body);
        presenter.bind(this);
        presenter.load();
    }

    private CardPanel card(String title, String intro, ServerSetting<?>... settings) {
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);
        WrapLabel introLabel = new WrapLabel(intro);
        introLabel.setAlignmentX(LEFT_ALIGNMENT);
        content.add(introLabel);
        content.add(Box.createVerticalStrut(Spacing.GAP));
        for (ServerSetting<?> setting : settings) {
            Row row = rows.get(setting);
            row.setAlignmentX(LEFT_ALIGNMENT);
            content.add(row);
        }
        CardPanel card = new CardPanel(title, content);
        card.setAlignmentX(LEFT_ALIGNMENT);
        return card;
    }

    static String tooltip(ServerSetting<?> setting) {
        StringBuilder text = new StringBuilder("<html>").append(Html.escape(setting.tooltip()));
        if (setting.minimum() != null && setting.maximum() != null) {
            text.append("<br>Allowed: ").append(setting.minimum()).append(" to ").append(setting.maximum());
        }
        if (setting.environmentVariable() != null) {
            text.append("<br>Environment variable: ").append(Html.escape(setting.environmentVariable()));
        }
        String fallback = DEFAULTS.get(setting.id());
        if (fallback != null) {
            text.append("<br>Ollama default: ").append(Html.escape(fallback));
        }
        text.append("<br>").append(setting.isRestartRequired() ? "Requires a server restart." : "Applies per request.");
        return text.append("</html>").toString();
    }

    @Override
    public void showValues(Map<ServerSetting<?>, String> texts) {
        for (Map.Entry<ServerSetting<?>, Editor> entry : editors.entrySet()) {
            String text = texts.get(entry.getKey());
            entry.getValue().setText(text == null ? "" : text);
        }
    }

    @Override
    public void showApplied(Map<ServerSetting<?>, String> applied, Set<ServerSetting<?>> pendingRestart,
                            String source) {
        boolean running = applied != null;
        appliedSource.show(running ? Theme.Tone.INFO : Theme.Tone.NEUTRAL, source);
        for (Map.Entry<ServerSetting<?>, Row> entry : rows.entrySet()) {
            Row row = entry.getValue();
            if (!running) {
                row.showApplied(null, false);
                continue;
            }
            String value = applied.get(entry.getKey());
            row.showApplied(value == null || value.isEmpty() ? "default" : value,
                pendingRestart.contains(entry.getKey()));
        }
    }

    @Override
    public Map<ServerSetting<?>, String> editedValues() {
        Map<ServerSetting<?>, String> texts = new LinkedHashMap<ServerSetting<?>, String>();
        for (Map.Entry<ServerSetting<?>, Editor> entry : editors.entrySet()) {
            texts.put(entry.getKey(), entry.getValue().text());
        }
        return texts;
    }

    @Override
    public void showErrors(Map<ServerSetting<?>, String> errors) {
        for (Map.Entry<ServerSetting<?>, Row> entry : rows.entrySet()) {
            entry.getValue().showError(errors.get(entry.getKey()));
        }
        if (!errors.isEmpty()) {
            rows.get(errors.keySet().iterator().next()).focus();
        }
    }

    @Override
    public void setBusy(boolean busy) {
        // Lock the editors too, so edits made during a save cannot be overwritten by its result.
        for (Editor editor : editors.values()) {
            editor.setEnabled(!busy);
        }
        save.setEnabled(!busy);
        revert.setEnabled(!busy);
        reload.setEnabled(!busy);
    }

    @Override
    public void showNotice(String text) {
        if (text == null || text.isEmpty()) {
            notice.clear();
        } else if (text.startsWith("Fix ")) {
            notice.error(text);
        } else {
            notice.show(text.contains("estart") ? Theme.Tone.WARNING : Theme.Tone.SUCCESS, text);
        }
    }

    @Override
    public void showFailure(String action, Exception error) {
        Dialogs.showError(this, action, error);
    }

    @Override
    public void updateUI() {
        super.updateUI();
        if (rows != null) {
            alignLabels();
        }
    }

    /** Give every row label the same width so the editors line up across cards. */
    private void alignLabels() {
        int width = 0;
        for (Row row : rows.values()) {
            width = Math.max(width, row.labelWidth());
        }
        for (Row row : rows.values()) {
            row.setLabelWidth(width);
        }
    }

    private static Map<String, String> defaults() {
        Map<String, String> map = new LinkedHashMap<String, String>();
        map.put("bind", "127.0.0.1:11434");
        map.put("parallelism", "auto (4, or 1 when memory is tight)");
        map.put("loadedModels", "3 per GPU");
        map.put("queue", "512");
        map.put("contextLength", "4096");
        map.put("kvCache", "f16 (full precision)");
        map.put("flashAttention", "off");
        map.put("keepAlive", "5m");
        map.put("modelRoot", "~/.ollama/models");
        return map;
    }

    private static Editor editorFor(ServerSetting<?> setting) {
        switch (setting.optionType()) {
            case INTEGER:
                if (setting == ServerSettingCatalog.CONTEXT_LENGTH) {
                    return new ContextLengthEditor(setting.minimum(), setting.maximum(), 4096);
                }
                return new SpinnerEditor(setting.minimum(), setting.maximum(), unit(setting),
                    setting == ServerSettingCatalog.PARALLELISM ? 4
                        : setting == ServerSettingCatalog.LOADED_MODELS ? 3 : 512);
            case BOOLEAN:
                return new ChoiceEditor(new String[]{"1", "0"}, new String[]{"On", "Off"});
            case CHOICE:
                List<String> values = new ArrayList<String>();
                List<String> labels = new ArrayList<String>();
                for (Object choice : setting.choices()) {
                    values.add(encode(setting, choice));
                    labels.add(choice instanceof KvCacheType ? ((KvCacheType) choice).label() : String.valueOf(choice));
                }
                return new ChoiceEditor(values.toArray(new String[0]), labels.toArray(new String[0]));
            case DURATION:
                return new DurationEditor();
            case DIRECTORY:
                return new DirectoryEditor(DEFAULTS.get(setting.id()));
            case ENDPOINT:
            default:
                return new EndpointEditor();
        }
    }

    private static String unit(ServerSetting<?> setting) {
        if (setting == ServerSettingCatalog.CONTEXT_LENGTH) {
            return "tokens";
        }
        if (setting == ServerSettingCatalog.LOADED_MODELS) {
            return "models";
        }
        return "requests";
    }

    @SuppressWarnings("unchecked")
    private static <T> String encode(ServerSetting<T> setting, Object value) {
        return setting.encode((T) value);
    }

    /** One setting: label, editor, running value, description and inline error. */
    private static final int MAX_BADGE_CHARS = 24;

    /**
     * Keep the running-value badge short so a long value (a model folder path)
     * never squeezes the editor out of its row; the full value stays in the tooltip.
     */
    static String shorten(String value) {
        if (value.length() <= MAX_BADGE_CHARS) {
            return value;
        }
        return "\u2026" + value.substring(value.length() - MAX_BADGE_CHARS + 1);
    }

    private static final class Row extends JPanel {
        private static final long serialVersionUID = 1L;
        private final ServerSetting<?> setting;
        private final Editor editor;
        private final JLabel label = new JLabel();
        private final WrapLabel description = new WrapLabel("");
        private final JLabel error = new JLabel();
        private final StatusBadge applied = new StatusBadge();
        private final Border normalBorder;

        Row(ServerSetting<?> setting, Editor editor) {
            super(new GridBagLayout());
            this.setting = setting;
            this.editor = editor;
            setOpaque(false);
            label.setText(setting.label() + ":");
            label.setLabelFor(editor.focusTarget());
            if (editor instanceof ToggledEditor) {
                ((ToggledEditor) editor).set.getAccessibleContext().setAccessibleName("Set " + setting.label());
            }
            label.setToolTipText(tooltip(setting));
            editor.component().setToolTipText(tooltip(setting));
            String fallback = DEFAULTS.get(setting.id());
            description.setText(setting.tooltip() + (fallback == null ? "" : " Ollama default: " + fallback + "."));
            error.setForeground(Theme.color(Theme.Tone.ERROR));
            error.setVisible(false);
            applied.setVisible(false);
            applied.setFocusable(false);
            normalBorder = editor.focusTarget().getBorder();

            GridBagConstraints c = new GridBagConstraints();
            c.insets = new Insets(2, 0, 2, Spacing.GAP);
            c.anchor = GridBagConstraints.NORTHWEST;
            c.gridx = 0;
            c.gridy = 0;
            c.weightx = 0;
            add(label, c);
            c.gridx = 1;
            c.weightx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            add(editor.component(), c);
            c.gridx = 2;
            c.weightx = 0;
            c.fill = GridBagConstraints.NONE;
            c.insets = new Insets(2, 0, 2, 0);
            add(applied, c);
            c.gridx = 1;
            c.gridy = 1;
            c.gridwidth = 2;
            c.weightx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.insets = new Insets(0, 0, 2, 0);
            add(description, c);
            c.gridy = 2;
            c.insets = new Insets(0, 0, Spacing.GAP, 0);
            add(error, c);
        }

        void showApplied(String value, boolean pending) {
            if (value == null) {
                applied.setVisible(false);
                return;
            }
            applied.show("running: " + shorten(value), pending ? Theme.Tone.WARNING : Theme.Tone.SUCCESS);
            applied.setToolTipText("<html>Running server: " + Html.escape(value) + "<br>" + (pending
                ? "The stored value differs; restart the server to apply it."
                : "The running server uses this value.") + "</html>");
            applied.setVisible(true);
        }

        void showError(String message) {
            boolean failed = message != null;
            error.setText(failed ? message : "");
            error.setVisible(failed);
            label.setForeground(failed ? Theme.color(Theme.Tone.ERROR) : Theme.foreground());
            JComponent target = editor.focusTarget();
            target.setBorder(failed ? BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.color(Theme.Tone.ERROR), 2), normalBorder) : normalBorder);
            label.getAccessibleContext().setAccessibleDescription(message);
            target.getAccessibleContext().setAccessibleDescription(failed ? message : setting.tooltip());
        }

        void focus() {
            editor.focusTarget().requestFocusInWindow();
        }

        int labelWidth() {
            label.setPreferredSize(null);
            label.setMinimumSize(null);
            return label.getPreferredSize().width;
        }

        void setLabelWidth(int width) {
            Dimension size = new Dimension(width, label.getPreferredSize().height);
            label.setPreferredSize(size);
            // GridBag falls back to minimum sizes when space is short; keep the columns aligned then too.
            label.setMinimumSize(size);
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }

    }

    /** Text access to one field, independent of the Swing component type. */
    private interface Editor {
        /** The whole editor as laid out in the row. */
        JComponent component();

        /** The component that takes keyboard focus and the error border. */
        JComponent focusTarget();

        /** Encoded value, or empty when the variable is not set. */
        String text();

        void setText(String text);

        void setEnabled(boolean enabled);
    }

    /**
     * Editor whose control cannot be empty (spinner, slider, combo pair). A "Set" box in front of it decides
     * whether the variable is passed at all; while it is unticked the control is greyed and shows Ollama's default.
     */
    private abstract static class ToggledEditor implements Editor {
        final JCheckBox set = new JCheckBox("Set");
        final JPanel panel = new JPanel(new GridBagLayout());
        private boolean enabled = true;

        ToggledEditor() {
            panel.setOpaque(false);
            set.setOpaque(false);
            set.setToolTipText("Unticked: the variable is not set and Ollama uses its own default."
                + " Ticked: the value shown is passed to the server, even if it equals the default.");
            set.addActionListener(event -> {
                if (set.isSelected()) {
                    showDefault();
                }
                applyEnabled();
            });
        }

        /** Place the control(s) to the right of the Set box. */
        final void layout(JComponent control, boolean fill) {
            GridBagConstraints c = new GridBagConstraints();
            c.gridx = 0;
            c.gridy = 0;
            c.anchor = GridBagConstraints.WEST;
            c.insets = new Insets(0, 0, 0, Spacing.GAP);
            panel.add(set, c);
            c.gridx = 1;
            c.weightx = 1;
            c.fill = fill ? GridBagConstraints.HORIZONTAL : GridBagConstraints.NONE;
            c.insets = new Insets(0, 0, 0, 0);
            panel.add(control, c);
        }

        /** Put Ollama's default into the control (shown greyed while unset, kept as start value when set). */
        abstract void showDefault();

        /** Encoded value of the control. */
        abstract String controlText();

        /** Apply an encoded value to the control. */
        abstract void applyText(String text);

        /** Enable or disable the control(s). */
        abstract void enableControl(boolean enabled);

        @Override
        public final JComponent component() {
            return panel;
        }

        @Override
        public final String text() {
            return set.isSelected() ? controlText() : "";
        }

        @Override
        public final void setText(String text) {
            boolean chosen = text != null && !text.trim().isEmpty();
            set.setSelected(chosen);
            if (chosen) {
                try {
                    applyText(text.trim());
                } catch (RuntimeException e) {
                    // Malformed stored value: keep the box ticked so the presenter's error points at it.
                    showDefault();
                }
            } else {
                showDefault();
            }
            applyEnabled();
        }

        @Override
        public final void setEnabled(boolean enabled) {
            this.enabled = enabled;
            applyEnabled();
        }

        private void applyEnabled() {
            set.setEnabled(enabled);
            enableControl(enabled && set.isSelected());
        }
    }

    /** Whole number with a spinner; the unit and allowed range sit next to it. */
    private static final class SpinnerEditor extends ToggledEditor {
        private final JSpinner spinner;
        private final JLabel unit;
        private final int fallback;

        SpinnerEditor(int minimum, int maximum, String unitText, int fallback) {
            this.fallback = fallback;
            spinner = new JSpinner(new SpinnerNumberModel(fallback, minimum, maximum, 1));
            fitColumns(spinner, 7);
            unit = new JLabel(unitText + "  (" + minimum + " to " + maximum + ")");
            unit.setForeground(Theme.mutedForeground());
            JPanel control = new JPanel(new BorderLayout(Spacing.GAP, 0));
            control.setOpaque(false);
            control.add(spinner, BorderLayout.WEST);
            control.add(unit, BorderLayout.CENTER);
            layout(control, true);
        }

        @Override
        public JComponent focusTarget() {
            return spinner;
        }

        @Override
        void showDefault() {
            spinner.setValue(fallback);
        }

        @Override
        String controlText() {
            return String.valueOf(spinner.getValue());
        }

        @Override
        void applyText(String text) {
            spinner.setValue(Integer.valueOf(text));
        }

        @Override
        void enableControl(boolean enabled) {
            spinner.setEnabled(enabled);
            unit.setEnabled(enabled);
        }
    }

    /**
     * Context length: a slider over powers of two (256 … 1M tokens) coupled with a spinner for exact values.
     */
    static final class ContextLengthEditor extends ToggledEditor {
        private final int minimum;
        private final JSlider slider;
        private final JSpinner spinner;
        private final JLabel unit = new JLabel("tokens");
        private final int fallback;
        private boolean syncing;

        ContextLengthEditor(int minimum, int maximum, int fallback) {
            this.minimum = minimum;
            this.fallback = fallback;
            int steps = 0;
            while (minimum << (steps + 1) <= maximum) {
                steps++;
            }
            slider = new JSlider(0, steps, position(fallback));
            slider.setOpaque(false);
            slider.setMajorTickSpacing(2);
            slider.setMinorTickSpacing(1);
            slider.setPaintTicks(true);
            slider.setPaintLabels(true);
            slider.setSnapToTicks(true);
            Hashtable<Integer, JComponent> labels = new Hashtable<Integer, JComponent>();
            for (int step = 0; step <= steps; step += 2) {
                JLabel label = new JLabel(tokens(minimum << step));
                label.setFont(Theme.derived(0.85f, false));
                label.setForeground(Theme.mutedForeground());
                labels.put(step, label);
            }
            slider.setLabelTable(labels);
            slider.setToolTipText("Drag to a power of two; type an exact number in the box.");
            spinner = new JSpinner(new SpinnerNumberModel(fallback, minimum, maximum, 256));
            fitColumns(spinner, 8);
            unit.setForeground(Theme.mutedForeground());

            slider.addChangeListener(event -> {
                if (!syncing) {
                    sync(() -> spinner.setValue(minimum << slider.getValue()));
                }
            });
            spinner.addChangeListener(event -> {
                if (!syncing) {
                    sync(() -> slider.setValue(position((Integer) spinner.getValue())));
                }
            });

            JPanel box = new JPanel(new BorderLayout(Spacing.GAP, 0));
            box.setOpaque(false);
            box.add(spinner, BorderLayout.WEST);
            box.add(unit, BorderLayout.CENTER);
            JPanel control = new JPanel(new BorderLayout(Spacing.GAP, 0));
            control.setOpaque(false);
            control.add(slider, BorderLayout.CENTER);
            control.add(box, BorderLayout.EAST);
            layout(control, true);
        }

        private void sync(Runnable update) {
            syncing = true;
            try {
                update.run();
            } finally {
                syncing = false;
            }
        }

        private int position(int tokens) {
            return (int) Math.round(Math.log((double) tokens / minimum) / Math.log(2));
        }

        static String tokens(int count) {
            if (count >= 1 << 20) {
                return (count >> 20) + "M";
            }
            return count >= 1024 ? (count >> 10) + "k" : String.valueOf(count);
        }

        @Override
        public JComponent focusTarget() {
            return spinner;
        }

        @Override
        void showDefault() {
            spinner.setValue(fallback);
        }

        @Override
        String controlText() {
            return String.valueOf(spinner.getValue());
        }

        @Override
        void applyText(String text) {
            spinner.setValue(Integer.valueOf(text));
        }

        @Override
        void enableControl(boolean enabled) {
            slider.setEnabled(enabled);
            spinner.setEnabled(enabled);
            unit.setEnabled(enabled);
        }
    }

    /**
     * Keep-alive: "Unload idle models after" a time, or never. Unticking the unload box sends -1 (keep forever);
     * a time of 0 unloads right after each request.
     */
    private static final class DurationEditor extends ToggledEditor {
        private static final String[] UNITS = {"seconds", "minutes", "hours"};
        private static final String[] SUFFIXES = {"s", "m", "h"};

        private final JCheckBox unload = new JCheckBox("Unload idle models after", true);
        private final JSpinner amount = new JSpinner(new SpinnerNumberModel(5, 0, 1000000, 1));
        private final JComboBox<String> unit = new JComboBox<String>(UNITS);
        private final JLabel hint = new JLabel();
        private boolean enabled = true;

        DurationEditor() {
            unload.setOpaque(false);
            unload.setToolTipText("Ticked: a model leaves memory after this idle time (0 = right after each request)."
                + " Unticked: models stay loaded until the server stops (OLLAMA_KEEP_ALIVE=-1).");
            unload.addActionListener(event -> refresh());
            amount.addChangeListener(event -> refresh());
            unit.addActionListener(event -> refresh());
            fitColumns(amount, 7);
            hint.setForeground(Theme.mutedForeground());
            JPanel control = new JPanel(new GridBagLayout());
            control.setOpaque(false);
            GridBagConstraints c = new GridBagConstraints();
            c.gridy = 0;
            c.anchor = GridBagConstraints.WEST;
            c.insets = new Insets(0, 0, 0, Spacing.GAP);
            control.add(unload, c);
            control.add(amount, c);
            control.add(unit, c);
            c.weightx = 1;
            c.insets = new Insets(0, 0, 0, 0);
            control.add(hint, c);
            layout(control, true);
            refresh();
        }

        private void refresh() {
            boolean timed = enabled && unload.isSelected();
            amount.setEnabled(timed);
            unit.setEnabled(timed);
            hint.setEnabled(enabled);
            if (!unload.isSelected()) {
                hint.setText("never unloaded");
            } else if (((Integer) amount.getValue()) == 0) {
                hint.setText("unloaded right after each request");
            } else {
                hint.setText("");
            }
        }

        @Override
        public JComponent focusTarget() {
            return unload;
        }

        @Override
        void showDefault() {
            unload.setSelected(true);
            amount.setValue(5);
            unit.setSelectedIndex(1);
            refresh();
        }

        @Override
        String controlText() {
            if (!unload.isSelected()) {
                return "-1";
            }
            int value = (Integer) amount.getValue();
            return value == 0 ? "0" : value + SUFFIXES[unit.getSelectedIndex()];
        }

        @Override
        void applyText(String text) {
            if (text.equals("-1")) {
                unload.setSelected(false);
                refresh();
                return;
            }
            unload.setSelected(true);
            if (text.equals("0")) {
                amount.setValue(0);
                refresh();
                return;
            }
            Matcher matcher = DURATION.matcher(text);
            if (!matcher.matches()) {
                throw new IllegalArgumentException("not a duration: " + text);
            }
            int value = Integer.parseInt(matcher.group(1));
            String suffix = matcher.group(2);
            if (suffix.equals("ms")) {
                // Milliseconds are allowed by Ollama but pointless here: round to whole seconds.
                value = (value + 500) / 1000;
                suffix = "s";
            }
            amount.setValue(value);
            unit.setSelectedIndex(Arrays.asList(SUFFIXES).indexOf(suffix));
            refresh();
        }

        @Override
        void enableControl(boolean enabled) {
            this.enabled = enabled;
            unload.setEnabled(enabled);
            refresh();
        }
    }

    /** Network address: a host choice (editable for other interfaces) and a port spinner. */
    private static final class EndpointEditor extends ToggledEditor {
        private static final String LOCAL = "127.0.0.1";
        private static final String ALL = "0.0.0.0";

        private final JComboBox<String> host = new JComboBox<String>(new String[]{LOCAL, ALL});
        private final JSpinner port = new JSpinner(new SpinnerNumberModel(Endpoint.DEFAULT_PORT, 1, 65535, 1));
        private final JLabel hostHint = new JLabel();
        private final JLabel portLabel = new JLabel("port");

        EndpointEditor() {
            host.setEditable(true);
            host.setToolTipText("127.0.0.1: only programs on this computer. 0.0.0.0: every network interface,"
                + " reachable from other machines. Or type a specific address.");
            host.addActionListener(event -> updateHint());
            fitColumns(port, 6);
            hostHint.setForeground(Theme.mutedForeground());
            portLabel.setForeground(Theme.mutedForeground());
            JPanel control = new JPanel(new GridBagLayout());
            control.setOpaque(false);
            GridBagConstraints c = new GridBagConstraints();
            c.gridy = 0;
            c.anchor = GridBagConstraints.WEST;
            c.insets = new Insets(0, 0, 0, Spacing.GAP);
            control.add(host, c);
            control.add(portLabel, c);
            control.add(port, c);
            c.weightx = 1;
            c.insets = new Insets(0, 0, 0, 0);
            control.add(hostHint, c);
            layout(control, true);
            updateHint();
        }

        private String hostText() {
            Object item = host.getEditor().getItem();
            return item == null ? "" : item.toString().trim();
        }

        private void updateHint() {
            String text = hostText();
            hostHint.setText(ALL.equals(text) ? "reachable from other machines"
                : LOCAL.equals(text) || text.equalsIgnoreCase("localhost") ? "only this computer" : "");
        }

        @Override
        public JComponent focusTarget() {
            return host;
        }

        @Override
        void showDefault() {
            host.setSelectedItem(LOCAL);
            port.setValue(Endpoint.DEFAULT_PORT);
        }

        @Override
        String controlText() {
            return hostText() + ":" + port.getValue();
        }

        @Override
        void applyText(String text) {
            Endpoint endpoint = Endpoint.parse(text);
            host.setSelectedItem(endpoint.host());
            port.setValue(endpoint.port());
        }

        @Override
        void enableControl(boolean enabled) {
            host.setEnabled(enabled);
            port.setEnabled(enabled);
            portLabel.setEnabled(enabled);
            hostHint.setEnabled(enabled);
        }
    }

    private static final Pattern DURATION = Pattern.compile("(\\d+)(ms|s|m|h)");

    /** Size a spinner's text field by character count instead of its model's widest value. */
    private static void fitColumns(JSpinner spinner, int columns) {
        spinner.setEditor(new JSpinner.NumberEditor(spinner, "#"));
        JComponent editor = spinner.getEditor();
        if (editor instanceof JSpinner.DefaultEditor) {
            ((JSpinner.DefaultEditor) editor).getTextField().setColumns(columns);
        }
    }

    /** Free text with the Ollama default as placeholder. */
    private static class TextEditor implements Editor {
        final PlaceholderTextField field = new PlaceholderTextField(24);

        TextEditor(String placeholder) {
            // Keep the placeholder short; the full default is in the description below the field.
            String shown = placeholder == null ? "" : placeholder.replaceAll(" \\(.*\\)$", "");
            field.setPlaceholder(shown.isEmpty() ? "" : "Default: " + shown);
        }

        @Override
        public JComponent component() {
            return field;
        }

        @Override
        public JComponent focusTarget() {
            return field;
        }

        @Override
        public String text() {
            return field.getText().trim();
        }

        @Override
        public void setText(String text) {
            field.setText(text);
        }

        @Override
        public void setEnabled(boolean enabled) {
            field.setEnabled(enabled);
        }
    }

    /** Folder path with a Browse button. */
    private static final class DirectoryEditor extends TextEditor {
        private final JPanel panel = new JPanel(new BorderLayout(Spacing.GAP, 0));
        private final JButton browse = new JButton("Browse…");

        DirectoryEditor(String placeholder) {
            super(placeholder);
            panel.setOpaque(false);
            panel.add(field, BorderLayout.CENTER);
            panel.add(browse, BorderLayout.EAST);
            browse.setMnemonic('B');
            browse.addActionListener(event -> choose());
        }

        private void choose() {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            chooser.setDialogTitle("Model folder");
            if (!field.getText().trim().isEmpty()) {
                chooser.setCurrentDirectory(new File(field.getText().trim()));
            }
            if (chooser.showOpenDialog(panel) == JFileChooser.APPROVE_OPTION
                && chooser.getSelectedFile() != null) {
                field.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        }

        @Override
        public JComponent component() {
            return panel;
        }

        @Override
        public void setEnabled(boolean enabled) {
            super.setEnabled(enabled);
            browse.setEnabled(enabled);
        }
    }

    /** Combo box whose first entry means "not set". */
    private static final class ChoiceEditor implements Editor {
        private final String[] values;
        private final JComboBox<String> box;
        private final JPanel panel = new JPanel(new BorderLayout());

        ChoiceEditor(String[] values, String[] labels) {
            this.values = values;
            String[] items = new String[labels.length + 1];
            items[0] = DEFAULT_CHOICE;
            System.arraycopy(labels, 0, items, 1, labels.length);
            this.box = new JComboBox<String>(items);
            box.setMaximumRowCount(12);
            final javax.swing.ListCellRenderer<? super String> renderer = box.getRenderer();
            box.setRenderer((list, value, index, selected, focused) -> {
                java.awt.Component cell = renderer.getListCellRendererComponent(list, value, index, selected,
                    focused);
                if (!selected && DEFAULT_CHOICE.equals(value)) {
                    cell.setForeground(Theme.mutedForeground());
                }
                return cell;
            });
            panel.setOpaque(false);
            panel.add(box, BorderLayout.WEST);
        }

        @Override
        public JComponent component() {
            return panel;
        }

        @Override
        public JComponent focusTarget() {
            return box;
        }

        @Override
        public String text() {
            int index = box.getSelectedIndex();
            return index <= 0 ? "" : values[index - 1];
        }

        @Override
        public void setText(String text) {
            for (int i = 0; i < values.length; i++) {
                if (values[i].equalsIgnoreCase(text)) {
                    box.setSelectedIndex(i + 1);
                    return;
                }
            }
            box.setSelectedIndex(0);
        }

        @Override
        public void setEnabled(boolean enabled) {
            box.setEnabled(enabled);
        }
    }

}
