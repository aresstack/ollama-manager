package com.aresstack.ollamamanager.swing.section;

import com.aresstack.ollamamanager.application.model.ModelEntry;
import com.aresstack.ollamamanager.application.model.ModelPlacement;
import com.aresstack.ollamamanager.domain.model.LoadedModel;
import com.aresstack.ollamamanager.swing.component.Dialogs;
import com.aresstack.ollamamanager.swing.component.JobProgressPanel;
import com.aresstack.ollamamanager.swing.component.SectionPanel;
import com.aresstack.ollamamanager.swing.component.Spacing;
import com.aresstack.ollamamanager.swing.job.Job;
import com.aresstack.ollamamanager.swing.shell.Section;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.UIManager;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
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
    private final JTextField pullName = new JTextField(24);
    private final JButton pull = new JButton("Pull");
    private final JButton load = new JButton("Load");
    private final JButton unload = new JButton("Unload");
    private final JButton delete = new JButton("Delete…");
    private final JobProgressPanel progress = new JobProgressPanel();
    private boolean actionsEnabled = true;
    private boolean mutationsAllowed;
    private final JLabel readOnly = new JLabel(" ");

    public ModelsPanel(ModelsPresenter presenter) {
        super(Section.MODELS.title(), Section.MODELS.description());
        this.presenter = presenter;

        JButton refresh = new JButton("Refresh");
        refresh.setMnemonic('R');
        refresh.addActionListener(event -> presenter.refresh());
        addToolbarItem(refresh);

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFillsViewportHeight(true);
        table.setAutoCreateRowSorter(true);
        table.getAccessibleContext().setAccessibleName("Models");
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
        JPanel selectionRow = new JPanel(new FlowLayout(FlowLayout.LEADING, Spacing.GAP, 0));
        selectionRow.add(new JLabel("Selected model:"));
        selectionRow.add(load);
        selectionRow.add(unload);
        selectionRow.add(delete);

        JPanel south = new JPanel(new BorderLayout(0, Spacing.GAP));
        JPanel actions = new JPanel(new BorderLayout());
        actions.add(readOnly, BorderLayout.NORTH);
        actions.add(selectionRow, BorderLayout.CENTER);
        south.add(actions, BorderLayout.NORTH);
        south.add(pullRow, BorderLayout.CENTER);
        south.add(progress, BorderLayout.SOUTH);

        JPanel body = new JPanel(new BorderLayout(0, Spacing.GAP));
        notice.setBorder(BorderFactory.createEmptyBorder(0, 0, 2, 0));
        body.add(notice, BorderLayout.NORTH);
        body.add(new JScrollPane(table), BorderLayout.CENTER);
        body.add(south, BorderLayout.SOUTH);
        setBody(body);
        fitRowHeight();
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
            fitRowHeight();
        }
    }

    @Override
    public void showModels(List<ModelEntry> entries) {
        notice.setText(entries.isEmpty() ? "No models installed on this server." : entries.size() + " models");
        model.setRows(entries);
        updateButtons();
    }

    @Override
    public void setMutationsAllowed(boolean allowed, String reason) {
        mutationsAllowed = allowed;
        readOnly.setText(allowed || reason == null ? " " : reason);
        updateButtons();
    }

    @Override
    public void showUnavailable(String reason) {
        mutationsAllowed = false;
        readOnly.setText(" ");
        notice.setText(reason);
        model.setRows(new ArrayList<ModelEntry>());
        updateButtons();
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
        load.setEnabled(enabled && entry != null && entry.isInstalled());
        unload.setEnabled(enabled && entry != null && entry.isLoaded());
        delete.setEnabled(enabled && entry != null && entry.isInstalled());
    }

    private void fitRowHeight() {
        Font font = UIManager.getFont("Table.font");
        table.setRowHeight(table.getFontMetrics(font == null ? table.getFont() : font).getHeight() + 4);
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
