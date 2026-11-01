package com.aresstack.ollamamanager.swing.section;

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
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.border.Border;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
            + " from the Profiles section, applies them. An empty field means the variable is not set at all and"
            + " Ollama uses its built-in value; a value you enter is set even if it equals the default.");
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
        return new CardPanel(title, content);
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
                return new IntegerEditor(setting.minimum(), setting.maximum(), unit(setting),
                    DEFAULTS.get(setting.id()));
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
                return new DurationEditor(DEFAULTS.get(setting.id()));
            case DIRECTORY:
                return new DirectoryEditor(DEFAULTS.get(setting.id()));
            case ENDPOINT:
            default:
                return new TextEditor(DEFAULTS.get(setting.id()));
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

        String text();

        void setText(String text);

        void setEnabled(boolean enabled);
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

    /** Number field that stays empty for the Ollama default; the unit and allowed range sit next to it. */
    private static final class IntegerEditor extends TextEditor {
        private final JPanel panel = new JPanel(new BorderLayout(Spacing.GAP, 0));
        private final JLabel unit;

        IntegerEditor(Integer minimum, Integer maximum, String unitText, String placeholder) {
            super(placeholder);
            field.setColumns(12);
            unit = new JLabel(unitText + (minimum == null || maximum == null ? ""
                : "  (" + minimum + " to " + maximum + ")"));
            unit.setForeground(Theme.mutedForeground());
            panel.setOpaque(false);
            panel.add(field, BorderLayout.WEST);
            panel.add(unit, BorderLayout.CENTER);
        }

        @Override
        public JComponent component() {
            return panel;
        }

        @Override
        public void setEnabled(boolean enabled) {
            super.setEnabled(enabled);
            unit.setEnabled(enabled);
        }
    }

    /** Combo box whose first entry means "not set". */
    private static final class ChoiceEditor implements Editor {
        private final String[] values;
        private final JComboBox<String> box;

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
        }

        @Override
        public JComponent component() {
            return box;
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

    /** Duration text such as 30s, 5m or 1h; empty keeps Ollama's default. */
    private static final class DurationEditor extends TextEditor {
        private final JPanel panel = new JPanel(new BorderLayout(Spacing.GAP, 0));
        private final JLabel hint = new JLabel("examples: 30s, 5m, 1h · 0 = unload at once · -1 = keep forever");

        DurationEditor(String placeholder) {
            super(placeholder);
            field.setColumns(10);
            hint.setForeground(Theme.mutedForeground());
            panel.setOpaque(false);
            panel.add(field, BorderLayout.WEST);
            panel.add(hint, BorderLayout.CENTER);
        }

        @Override
        public JComponent component() {
            return panel;
        }

        @Override
        public void setEnabled(boolean enabled) {
            super.setEnabled(enabled);
            hint.setEnabled(enabled);
        }
    }

}
