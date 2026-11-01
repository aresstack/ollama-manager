package com.aresstack.ollamamanager.swing.section;

import com.aresstack.ollamamanager.domain.profile.ProfileActivationPlan;
import com.aresstack.ollamamanager.domain.server.ServerSetting;
import com.aresstack.ollamamanager.swing.component.ActionBar;
import com.aresstack.ollamamanager.swing.component.Dialogs;
import com.aresstack.ollamamanager.swing.component.EmptyState;
import com.aresstack.ollamamanager.swing.component.JobProgressPanel;
import com.aresstack.ollamamanager.swing.component.MessageBanner;
import com.aresstack.ollamamanager.swing.component.SectionPanel;
import com.aresstack.ollamamanager.swing.component.Spacing;
import com.aresstack.ollamamanager.swing.component.Tables;
import com.aresstack.ollamamanager.swing.component.Theme;
import com.aresstack.ollamamanager.swing.job.Job;
import com.aresstack.ollamamanager.swing.shell.Section;

import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.event.HierarchyEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Model profiles with their validity against the current model, GPU and Ollama version, and activation.
 */
public final class ProfilesPanel extends SectionPanel implements ProfilesView {

    private static final long serialVersionUID = 1L;

    private final ProfileTableModel model = new ProfileTableModel();
    private final JTable table = new JTable(model);
    private final JTextArea details = new JTextArea(4, 40);
    private final MessageBanner notice = new MessageBanner();
    private final EmptyState empty = new EmptyState("No profiles stored yet",
        "Profiles are created from benchmark results or scenario presets.");
    private final CardLayout listCards = new CardLayout();
    private final JPanel list = new JPanel(listCards);
    private final JButton activate = new JButton("Activate…");
    private final JButton refresh = new JButton("Refresh");
    private final JobProgressPanel progress = new JobProgressPanel();
    private boolean busy;

    public ProfilesPanel(ProfilesPresenter presenter) {
        super(Section.PROFILES.title(), Section.PROFILES.description());
        refresh.setMnemonic('R');
        refresh.addActionListener(event -> presenter.refresh());
        addToolbarItem(refresh);

        Tables.setup(table, "Profiles");
        table.getColumnModel().getColumn(4).setCellRenderer(Tables.toneRenderer(ProfilesPanel::checkTone));
        table.getColumnModel().getColumn(5).setCellRenderer(Tables.toneRenderer(
            value -> "yes".equals(value) ? Theme.Tone.SUCCESS : Theme.Tone.NEUTRAL));
        Tables.setWidthInChars(table, 0, 24);
        table.getSelectionModel().addListSelectionListener(event -> showSelection());
        details.setEditable(false);
        details.setLineWrap(true);
        details.setWrapStyleWord(true);
        details.getAccessibleContext().setAccessibleName("Profile check details");

        activate.setMnemonic('A');
        activate.setToolTipText("Make this profile active. A server restart is only done after you confirm it.");
        activate.addActionListener(event -> {
            ProfileRow row = selected();
            if (row != null) {
                presenter.activate(row.profile().id());
            }
        });

        ActionBar buttons = new ActionBar().addAction(activate);
        JPanel south = new JPanel(new BorderLayout(0, Spacing.GAP));
        south.setOpaque(false);
        south.add(buttons, BorderLayout.CENTER);
        south.add(progress, BorderLayout.SOUTH);

        list.setOpaque(false);
        list.add(new JScrollPane(table), "table");
        list.add(empty, "empty");
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, list, new JScrollPane(details));
        split.setResizeWeight(0.7);
        split.setBorder(null);
        JPanel body = new JPanel(new BorderLayout(0, Spacing.GAP));
        body.setOpaque(false);
        body.add(notice, BorderLayout.NORTH);
        body.add(split, BorderLayout.CENTER);
        body.add(south, BorderLayout.SOUTH);
        setBody(body);
        listCards.show(list, "empty");
        updateButtons();
        presenter.bind(this);
        addHierarchyListener(event -> {
            if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && isShowing()) {
                presenter.refresh();
            }
        });
    }

    @Override
    public void updateUI() {
        super.updateUI();
        if (table != null) {
            Tables.fitRowHeight(table);
        }
    }

    @Override
    public void showProfiles(List<ProfileRow> rows) {
        model.setRows(rows);
        listCards.show(list, rows.isEmpty() ? "empty" : "table");
        if (rows.isEmpty()) {
            details.setText("No profiles stored yet. Profiles are created from benchmark results or scenario"
                + " presets.");
        } else {
            showSelection();
        }
        updateButtons();
    }

    static Theme.Tone checkTone(Object value) {
        if ("valid".equals(value)) {
            return Theme.Tone.SUCCESS;
        }
        if ("outdated".equals(value)) {
            return Theme.Tone.WARNING;
        }
        return "not verifiable".equals(value) ? Theme.Tone.ERROR : Theme.Tone.NEUTRAL;
    }

    @Override
    public void setBusy(boolean value) {
        busy = value;
        refresh.setEnabled(!value);
        updateButtons();
    }

    @Override
    public void showNotice(String text) {
        if (text == null || text.isEmpty()) {
            notice.clear();
        } else {
            notice.show(text.startsWith("Not activated") ? Theme.Tone.WARNING : Theme.Tone.INFO, text);
        }
    }

    @Override
    public void showFailure(String action, Exception error) {
        Dialogs.showError(this, action, error);
    }

    @Override
    public void trackJob(Job job) {
        progress.attach(job);
    }

    @Override
    public boolean confirmUnverifiedActivation(ProfileRow row) {
        StringBuilder text = new StringBuilder("The profile \"").append(row.profile().name()).append("\" is ")
            .append(label(row)).append(":\n");
        for (String finding : findings(row)) {
            text.append("  • ").append(finding).append('\n');
        }
        text.append("\nIts settings were tuned for a different situation and may not fit.\nActivate it anyway?");
        return JOptionPane.showConfirmDialog(this, text.toString(), "Activate profile", JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION;
    }

    @Override
    public boolean confirmRestart(ProfileRow row, ProfileActivationPlan plan) {
        StringBuilder text = new StringBuilder("Profile \"").append(row.profile().name())
            .append("\" changes settings that need a server restart:\n");
        for (ServerSetting<?> setting : plan.restartRequiredChanges()) {
            text.append("  • ").append(setting.label()).append('\n');
        }
        text.append('\n').append(Dialogs.STOP_WARNING.replace("Stop the server now?", "Restart the server now?"));
        return Dialogs.confirm(this, "Restart server", text.toString());
    }

    private static String label(ProfileRow row) {
        return row.validity() == null ? "not checked" : row.validity().result().label();
    }

    private static List<String> findings(ProfileRow row) {
        return row.validity() == null ? new ArrayList<String>() : row.validity().findings();
    }

    private ProfileRow selected() {
        int row = table.getSelectedRow();
        return row < 0 ? null : model.row(table.convertRowIndexToModel(row));
    }

    private void showSelection() {
        ProfileRow row = selected();
        if (row == null) {
            details.setText("Select a profile to see its check.");
            updateButtons();
            return;
        }
        StringBuilder text = new StringBuilder(row.profile().name()).append(": ")
            .append(label(row));
        for (String finding : findings(row)) {
            text.append("\n• ").append(finding);
        }
        text.append("\nScope: ").append(row.profile().scope());
        details.setText(text.toString());
        details.setCaretPosition(0);
        updateButtons();
    }

    private void updateButtons() {
        activate.setEnabled(!busy && selected() != null);
    }

    private static final class ProfileTableModel extends AbstractTableModel {
        private static final long serialVersionUID = 1L;
        private static final String[] COLUMNS = {"Profile", "Model", "Workload", "Ollama", "Check", "Active"};
        private final List<ProfileRow> rows = new ArrayList<ProfileRow>();

        ProfileRow row(int index) {
            return rows.get(index);
        }

        void setRows(List<ProfileRow> values) {
            rows.clear();
            rows.addAll(values);
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMNS[column];
        }

        @Override
        public Object getValueAt(int index, int column) {
            ProfileRow row = rows.get(index);
            switch (column) {
                case 0:
                    return row.profile().name();
                case 1:
                    return row.profile().scope().model().name();
                case 2:
                    return row.profile().scope().workload();
                case 3:
                    return row.profile().scope().ollamaVersion();
                case 4:
                    return label(row);
                default:
                    return row.isActive() ? "yes" : "";
            }
        }
    }
}
