package com.aresstack.ollamamanager.swing.section;

import com.aresstack.ollamamanager.application.model.ModelEntry;
import com.aresstack.ollamamanager.application.model.ModelPlacement;
import com.aresstack.ollamamanager.domain.model.LoadedModel;
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
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.FlowLayout;
import java.awt.event.HierarchyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Model library: installed and loaded models with pull, load, unload and confirmed delete.
 */
public final class ModelsPanel extends SectionPanel implements ModelsView {

    private static final long serialVersionUID = 1L;

    private final ModelsPresenter presenter;
    private final ModelTableModel model = new ModelTableModel();
    private final JTable table = new JTable(model);
    private final JLabel notice = new JLabel(" ");
    private final MessageBanner banner = new MessageBanner();
    private final EmptyState empty = new EmptyState("No models on this server",
        "Pull a model from the Ollama library to get started.");
    private final CardLayout listCards = new CardLayout();
    private final JPanel list = new JPanel(listCards);
    private final JTextField pullName = new JTextField(24);
    private final JButton pull = new JButton("Pull");
    private final JButton load = new JButton("Load");
    private final JButton unload = new JButton("Unload");
    private final JButton delete = new JButton("Delete…");
    private final JobProgressPanel progress = new JobProgressPanel();
    private boolean actionsEnabled = true;
    private boolean mutationsAllowed;

    public ModelsPanel(ModelsPresenter presenter) {
        super(Section.MODELS.title(), Section.MODELS.description());
        this.presenter = presenter;

        JButton refresh = new JButton("Refresh");
        refresh.setMnemonic('R');
        refresh.addActionListener(event -> presenter.refresh());
        addToolbarItem(refresh);

        Tables.setup(table, "Models");
        Tables.alignRight(table, 1, 6, 7);
        table.getColumnModel().getColumn(5).setCellRenderer(Tables.toneRenderer(ModelsPanel::placementTone));
        table.getColumnModel().getColumn(4).setCellRenderer(Tables.toneRenderer(
            value -> "yes".equals(value) ? Theme.Tone.SUCCESS : Theme.Tone.NEUTRAL));
        Tables.setWidthInChars(table, 0, 28);
        table.getSelectionModel().addListSelectionListener(event -> updateButtons());

        pullName.setToolTipText("Model name as in the Ollama library, for example llama3.2:3b");
        pull.setMnemonic('P');
        pull.setToolTipText("Download the model to the running server's model directory.");
        load.setMnemonic('O');
        load.setToolTipText("Load the model into memory now and show whether it runs on GPU, CPU or split.");
        unload.setMnemonic('U');
        unload.setToolTipText("Remove the model from memory. The files stay on disk.");
        delete.setMnemonic('D');
        delete.setToolTipText("Delete the model files from disk. You must type the model name to confirm.");
        pull.addActionListener(event -> presenter.pull(pullName.getText()));
        pullName.addActionListener(event -> presenter.pull(pullName.getText()));
        load.addActionListener(event -> presenter.load(selectedName()));
        unload.addActionListener(event -> presenter.unload(selectedName()));
        delete.addActionListener(event -> presenter.delete(selectedName()));

        JLabel pullLabel = new JLabel("Pull model:");
        pullLabel.setDisplayedMnemonic('M');
        pullLabel.setLabelFor(pullName);
        JPanel pullRow = new JPanel(new FlowLayout(FlowLayout.LEADING, Spacing.GAP, 0));
        pullRow.add(pullLabel);
        pullRow.add(pullName);
        pullRow.add(pull);
        pullRow.setOpaque(false);
        ActionBar selectionRow = new ActionBar();
        selectionRow.addAction(new JLabel("Selected model:")).addAction(load).addAction(unload)
            .addTrailing(delete);

        JPanel south = new JPanel(new BorderLayout(0, Spacing.GAP));
        south.setOpaque(false);
        south.add(selectionRow, BorderLayout.NORTH);
        south.add(pullRow, BorderLayout.CENTER);
        south.add(progress, BorderLayout.SOUTH);

        empty.setAction(pullFromEmpty());
        list.setOpaque(false);
        list.add(new JScrollPane(table), "table");
        list.add(empty, "empty");

        JPanel north = new JPanel(new BorderLayout(0, Spacing.GAP));
        north.setOpaque(false);
        north.add(banner, BorderLayout.NORTH);
        notice.setForeground(Theme.mutedForeground());
        north.add(notice, BorderLayout.CENTER);
        JPanel body = new JPanel(new BorderLayout(0, Spacing.GAP));
        body.setOpaque(false);
        body.add(north, BorderLayout.NORTH);
        body.add(list, BorderLayout.CENTER);
        body.add(south, BorderLayout.SOUTH);
        setBody(body);
        showList(false);
        updateButtons();
        presenter.bind(this);
        // Load when the section becomes visible, so a server started meanwhile is picked up.
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
            notice.setForeground(Theme.mutedForeground());
        }
    }

    @Override
    public void showModels(List<ModelEntry> entries) {
        int loaded = 0;
        for (ModelEntry entry : entries) {
            if (entry.isLoaded()) {
                loaded++;
            }
        }
        notice.setText(entries.isEmpty() ? " " : entries.size() + (entries.size() == 1 ? " model, " : " models, ")
            + loaded + " loaded");
        empty.setText("No models on this server", "Pull a model from the Ollama library to get started.");
        model.setRows(entries);
        showList(!entries.isEmpty());
        updateButtons();
    }

    @Override
    public void setMutationsAllowed(boolean allowed, String reason) {
        mutationsAllowed = allowed;
        if (allowed || reason == null) {
            banner.clear();
        } else {
            banner.warning(reason);
        }
        updateButtons();
    }

    @Override
    public void showUnavailable(String reason) {
        mutationsAllowed = false;
        banner.clear();
        notice.setText(" ");
        empty.setText("Model library unavailable", reason);
        model.setRows(new ArrayList<ModelEntry>());
        showList(false);
        updateButtons();
    }

    /** Show the table, or the empty state while there is nothing to list. */
    private void showList(boolean hasRows) {
        listCards.show(list, hasRows ? "table" : "empty");
    }

    /** Button in the empty state that moves the focus to the pull field. */
    private JButton pullFromEmpty() {
        JButton button = new JButton("Pull a model…");
        button.addActionListener(event -> pullName.requestFocusInWindow());
        return button;
    }

    static Theme.Tone placementTone(Object value) {
        if ("gpu".equals(value)) {
            return Theme.Tone.SUCCESS;
        }
        if ("split".equals(value)) {
            return Theme.Tone.WARNING;
        }
        return "cpu".equals(value) ? Theme.Tone.ERROR : Theme.Tone.NEUTRAL;
    }

    @Override
    public void setActionsEnabled(boolean enabled) {
        actionsEnabled = enabled;
        updateButtons();
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
    public String confirmDelete(String modelName) {
        Object typed = JOptionPane.showInputDialog(this,
            "Deleting removes the model files from disk. Clients using " + modelName + " will fail.\n"
                + "Type the model name to confirm:", "Delete model", JOptionPane.WARNING_MESSAGE);
        return typed == null ? null : typed.toString();
    }

    private String selectedName() {
        int row = table.getSelectedRow();
        return row < 0 ? null : model.entry(table.convertRowIndexToModel(row)).identity().name();
    }

    private void updateButtons() {
        int row = table.getSelectedRow();
        ModelEntry entry = row < 0 ? null : model.entry(table.convertRowIndexToModel(row));
        boolean enabled = actionsEnabled && mutationsAllowed;
        pull.setEnabled(enabled);
        pullName.setEnabled(enabled);
        for (java.awt.Component component : empty.getComponents()) {
            component.setEnabled(true);
        }
        load.setEnabled(enabled && entry != null && entry.isInstalled());
        unload.setEnabled(enabled && entry != null && entry.isLoaded());
        delete.setEnabled(enabled && entry != null && entry.isInstalled());
    }

    private static final class ModelTableModel extends AbstractTableModel {
        private static final long serialVersionUID = 1L;
        private static final String[] COLUMNS = {"Model", "Size", "Parameters", "Quantization", "Loaded",
            "Placement", "VRAM", "Context"};
        private final List<ModelEntry> rows = new ArrayList<ModelEntry>();

        ModelEntry entry(int row) {
            return rows.get(row);
        }

        void setRows(List<ModelEntry> entries) {
            rows.clear();
            rows.addAll(entries);
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
        public Object getValueAt(int row, int column) {
            ModelEntry entry = rows.get(row);
            LoadedModel loaded = entry.loaded();
            switch (column) {
                case 0:
                    return entry.identity().name();
                case 1:
                    return entry.isInstalled() ? Format.bytes(entry.installed().sizeBytes()) : "";
                case 2:
                    return entry.isInstalled() ? text(entry.installed().parameterSize()) : "";
                case 3:
                    return text(entry.identity().quantization());
                case 4:
                    return entry.isLoaded() ? "yes" : "no";
                case 5:
                    ModelPlacement placement = entry.placement();
                    return placement == null ? "" : placement.name().toLowerCase(Locale.ROOT);
                case 6:
                    return loaded == null ? "" : Format.bytes(loaded.vramBytes());
                default:
                    return loaded == null || loaded.contextLength() == null ? "" : loaded.contextLength();
            }
        }

        private static String text(String value) {
            return value == null ? "" : value;
        }
    }
}
