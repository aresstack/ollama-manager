package com.aresstack.ollamamanager.swing.section;

import com.aresstack.ollamamanager.domain.server.OptionType;
import com.aresstack.ollamamanager.domain.server.ServerSetting;
import com.aresstack.ollamamanager.domain.server.ServerSettingCatalog;
import com.aresstack.ollamamanager.swing.component.Dialogs;
import com.aresstack.ollamamanager.swing.component.FormPanel;
import com.aresstack.ollamamanager.swing.component.Html;
import com.aresstack.ollamamanager.swing.component.SectionPanel;
import com.aresstack.ollamamanager.swing.component.Spacing;
import com.aresstack.ollamamanager.swing.shell.Section;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Editor for the desired server-wide settings. Every field explains itself in a tooltip; an empty field means
 * Ollama's own default. Saving only stores the settings; a running server picks them up after a restart.
 */
public final class ServerSettingsPanel extends SectionPanel implements ServerSettingsView {

    private static final long serialVersionUID = 1L;
    static final String DEFAULT_CHOICE = "(Ollama default)";

    private final Map<ServerSetting<?>, Editor> editors = new LinkedHashMap<ServerSetting<?>, Editor>();
    private final Map<ServerSetting<?>, JLabel> labels = new LinkedHashMap<ServerSetting<?>, JLabel>();
    private final JLabel notice = new JLabel(" ");
    private final JLabel appliedSource = new JLabel(" ");
    private final Map<ServerSetting<?>, JLabel> appliedLabels = new LinkedHashMap<ServerSetting<?>, JLabel>();
    private final JButton save = new JButton("Save");
    private final JButton revert = new JButton("Revert");
    private final JButton reload = new JButton("Reload");

    public ServerSettingsPanel(ServerSettingsPresenter presenter) {
        super(Section.SERVER_SETTINGS.title(), Section.SERVER_SETTINGS.description());
        FormPanel form = new FormPanel();
        for (ServerSetting<?> setting : ServerSettingCatalog.all()) {
            Editor editor = editorFor(setting);
            editors.put(setting, editor);
            JLabel applied = new JLabel(" ");
            appliedLabels.put(setting, applied);
            JPanel cell = new JPanel(new BorderLayout(Spacing.GAP, 0));
            cell.setOpaque(false);
            cell.add(editor.component(), BorderLayout.CENTER);
            cell.add(applied, BorderLayout.EAST);
            JLabel label = form.addRow(setting.label(), cell, tooltip(setting));
            label.setLabelFor(editor.component());
            editor.component().setToolTipText(tooltip(setting));
            labels.put(setting, label);
        }
        form.addFiller();
        JScrollPane scroll = new JScrollPane(form);
        scroll.setBorder(null);

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

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEADING, Spacing.GAP, 0));
        buttons.add(save);
        buttons.add(revert);
        JPanel south = new JPanel(new BorderLayout(0, Spacing.GAP));
        south.setBorder(BorderFactory.createEmptyBorder(Spacing.GAP, 0, 0, 0));
        south.add(notice, BorderLayout.NORTH);
        south.add(buttons, BorderLayout.CENTER);

        JPanel body = new JPanel(new BorderLayout(0, Spacing.GAP));
        body.add(appliedSource, BorderLayout.NORTH);
        body.add(scroll, BorderLayout.CENTER);
        body.add(south, BorderLayout.SOUTH);
        setBody(body);
        presenter.bind(this);
        presenter.load();
    }

    static String tooltip(ServerSetting<?> setting) {
        StringBuilder text = new StringBuilder("<html>").append(Html.escape(setting.tooltip()));
        if (setting.minimum() != null && setting.maximum() != null) {
            text.append("<br>Allowed: ").append(setting.minimum()).append(" to ").append(setting.maximum());
        }
        if (setting.environmentVariable() != null) {
            text.append("<br>Environment variable: ").append(Html.escape(setting.environmentVariable()));
        }
        text.append("<br>").append(setting.isRestartRequired() ? "Requires a server restart." : "Applies per request.");
        text.append("<br>Leave empty for Ollama's default.");
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
        appliedSource.setText("Stored values are edited below. " + source);
        for (Map.Entry<ServerSetting<?>, JLabel> entry : appliedLabels.entrySet()) {
            JLabel label = entry.getValue();
            if (applied == null) {
                label.setText(" ");
                continue;
            }
            String value = applied.get(entry.getKey());
            String running = "running: " + (value == null || value.isEmpty() ? "default" : value);
            label.setText(pendingRestart.contains(entry.getKey()) ? running + " — restart pending" : running);
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
        Color normal = UIManager.getColor("Label.foreground");
        Color error = UIManager.getColor("Component.errorFocusColor") != null
            ? UIManager.getColor("Component.errorFocusColor") : Color.RED.darker();
        for (Map.Entry<ServerSetting<?>, JLabel> entry : labels.entrySet()) {
            String message = errors.get(entry.getKey());
            JLabel label = entry.getValue();
            label.setForeground(message == null ? normal : error);
            label.setText(entry.getKey().label() + (message == null ? ":" : ": *"));
            String tip = message == null ? tooltip(entry.getKey())
                : "<html><b>" + Html.escape(message) + "</b><br>" + tooltip(entry.getKey()).substring(6);
            label.setToolTipText(tip);
            editors.get(entry.getKey()).component().setToolTipText(tip);
            label.getAccessibleContext().setAccessibleDescription(message);
        }
        if (!errors.isEmpty()) {
            editors.get(errors.keySet().iterator().next()).component().requestFocusInWindow();
        }
    }

    @Override
    public void setBusy(boolean busy) {
        // Lock the editors too, so edits made during a save cannot be overwritten by its result.
        for (Editor editor : editors.values()) {
            editor.component().setEnabled(!busy);
        }
        save.setEnabled(!busy);
        revert.setEnabled(!busy);
        reload.setEnabled(!busy);
    }

    @Override
    public void showNotice(String text) {
        notice.setText(text == null || text.isEmpty() ? " " : text);
    }

    @Override
    public void showFailure(String action, Exception error) {
        Dialogs.showError(this, action, error);
    }

    private static Editor editorFor(ServerSetting<?> setting) {
        if (setting.optionType() == OptionType.BOOLEAN) {
            return new ChoiceEditor(new String[]{"1", "0"}, new String[]{"On", "Off"});
        }
        if (setting.optionType() == OptionType.CHOICE && !setting.choices().isEmpty()) {
            List<String> values = new ArrayList<String>();
            for (Object choice : setting.choices()) {
                values.add(encode(setting, choice));
            }
            String[] array = values.toArray(new String[0]);
            return new ChoiceEditor(array, array);
        }
        return new TextEditor();
    }

    @SuppressWarnings("unchecked")
    private static <T> String encode(ServerSetting<T> setting, Object value) {
        return setting.encode((T) value);
    }

    /** Text access to one field, independent of the Swing component type. */
    private interface Editor {
        JComponent component();

        String text();

        void setText(String text);
    }

    private static final class TextEditor implements Editor {
        private final JTextField field = new JTextField(24);

        @Override
        public JComponent component() {
            return field;
        }

        @Override
        public String text() {
            return field.getText();
        }

        @Override
        public void setText(String text) {
            field.setText(text);
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
        }

        @Override
        public JComponent component() {
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
    }
}
