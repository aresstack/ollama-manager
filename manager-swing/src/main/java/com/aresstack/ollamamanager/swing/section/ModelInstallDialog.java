package com.aresstack.ollamamanager.swing.section;

import com.aresstack.ollamamanager.application.catalog.CatalogModel;
import com.aresstack.ollamamanager.application.catalog.CatalogSource;
import com.aresstack.ollamamanager.application.catalog.CatalogVariant;
import com.aresstack.ollamamanager.application.catalog.ModelCatalogService;
import com.aresstack.ollamamanager.swing.component.Dialogs;
import com.aresstack.ollamamanager.swing.component.Spacing;
import com.aresstack.ollamamanager.swing.component.Tables;
import com.aresstack.ollamamanager.swing.component.Theme;
import com.aresstack.ollamamanager.swing.job.JobManager;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Search the Ollama library or Hugging Face (GGUF), choose a variant and install it. Installing hands the exact
 * pull name to the Models section, which pulls it on the running server with the usual progress and cancel.
 */
final class ModelInstallDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    private final transient ModelCatalogService catalog;
    private final transient JobManager jobs;
    private final transient Consumer<String> install;
    private final JComboBox<CatalogSource> source;
    private final JTextField query = new JTextField(28);
    private final JButton search = new JButton("Search");
    private final JButton installButton = new JButton("Install");
    private final JLabel status = new JLabel(" ");
    private final ResultModel results = new ResultModel();
    private final VariantModel variants = new VariantModel();
    private final JTable resultTable = new JTable(results);
    private final JTable variantTable = new JTable(variants);
    /** Incremented per request so that a slow, older answer never overwrites a newer one. */
    private long generation;

    ModelInstallDialog(Component owner, ModelCatalogService catalog, JobManager jobs, Consumer<String> install) {
        super(owner instanceof Window ? (Window) owner : javax.swing.SwingUtilities.getWindowAncestor(owner), "Install model", ModalityType.MODELESS);
        this.catalog = catalog;
        this.jobs = jobs;
        this.install = install;
        source = new JComboBox<CatalogSource>(catalog.sources().toArray(new CatalogSource[0]));
        source.addActionListener(event -> search());
        query.addActionListener(event -> search());
        search.addActionListener(event -> search());
        search.setMnemonic('S');
        installButton.setMnemonic('I');
        installButton.setToolTipText("Download the selected variant into the running server's model directory.");
        installButton.addActionListener(event -> installSelected());
        query.setToolTipText("For example qwen3, llama3.2, gemma or a Hugging Face repository name");

        Tables.setup(resultTable, "Models found");
        Tables.setup(variantTable, "Variants");
        Tables.setWidthInChars(resultTable, 0, 30);
        Tables.setWidthInChars(variantTable, 0, 36);
        resultTable.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                loadVariants();
            }
        });
        variantTable.getSelectionModel().addListSelectionListener(event -> updateButtons());
        variantTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2) {
                    installSelected();
                }
            }
        });

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEADING, Spacing.GAP, 0));
        top.add(new JLabel("Source:"));
        top.add(source);
        JLabel queryLabel = new JLabel("Search:");
        queryLabel.setDisplayedMnemonic('E');
        queryLabel.setLabelFor(query);
        top.add(queryLabel);
        top.add(query);
        top.add(search);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, titled("Models", resultTable),
            titled("Variants (double-click to install)", variantTable));
        split.setResizeWeight(0.55);

        JPanel bottom = new JPanel(new BorderLayout(Spacing.GAP, 0));
        status.setForeground(Theme.mutedForeground());
        bottom.add(status, BorderLayout.CENTER);
        JButton close = new JButton("Close");
        close.addActionListener(event -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.TRAILING, Spacing.GAP, 0));
        buttons.add(installButton);
        buttons.add(close);
        bottom.add(buttons, BorderLayout.EAST);

        JPanel content = new JPanel(new BorderLayout(0, Spacing.GAP));
        content.setBorder(BorderFactory.createEmptyBorder(Spacing.GAP, Spacing.GAP, Spacing.GAP, Spacing.GAP));
        content.add(top, BorderLayout.NORTH);
        content.add(split, BorderLayout.CENTER);
        content.add(bottom, BorderLayout.SOUTH);
        setContentPane(content);
        getRootPane().setDefaultButton(search);
        getRootPane().registerKeyboardAction(event -> dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_IN_FOCUSED_WINDOW);
        setPreferredSize(new Dimension(820, 600));
        pack();
        setLocationRelativeTo(owner);
        updateButtons();
    }

    private static JComponent titled(String title, JTable table) {
        JPanel panel = new JPanel(new BorderLayout(0, Spacing.GAP / 2));
        panel.add(new JLabel(title), BorderLayout.NORTH);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        return panel;
    }

    private void search() {
        final String text = query.getText().trim();
        if (text.isEmpty()) {
            status.setText("Enter a search term.");
            return;
        }
        final CatalogSource selected = (CatalogSource) source.getSelectedItem();
        final long current = ++generation;
        results.setRows(new ArrayList<CatalogModel>());
        variants.setRows(new ArrayList<CatalogVariant>());
        status.setText("Searching " + selected.title() + "…");
        jobs.submit("Search " + selected.title(), true,
            context -> catalog.search(selected, text, context.cancellation()),
            found -> {
                if (current == generation) {
                    results.setRows(found);
                    status.setText(found.isEmpty() ? "No models found." : found.size() + " models found.");
                    if (!found.isEmpty()) {
                        resultTable.setRowSelectionInterval(0, 0);
                    }
                }
            }, error -> {
                if (current == generation) {
                    status.setText("Search failed.");
                    Dialogs.showError(this, "Search " + selected.title(), error);
                }
            });
    }

    private void loadVariants() {
        int row = resultTable.getSelectedRow();
        variants.setRows(new ArrayList<CatalogVariant>());
        updateButtons();
        if (row < 0) {
            return;
        }
        final CatalogModel model = results.row(resultTable.convertRowIndexToModel(row));
        final long current = ++generation;
        status.setText("Loading variants of " + model.id() + "…");
        jobs.submit("Load variants of " + model.id(), true,
            context -> catalog.variants(model, context.cancellation()),
            found -> {
                if (current == generation) {
                    variants.setRows(found);
                    status.setText(found.isEmpty() ? "No installable variants for " + model.id() + "."
                        : found.size() + " variants of " + model.id() + ".");
                    int recommended = 0;
                    for (int i = 0; i < found.size(); i++) {
                        if (found.get(i).recommended()) {
                            recommended = i;
                            break;
                        }
                    }
                    if (!found.isEmpty()) {
                        variantTable.setRowSelectionInterval(recommended, recommended);
                    }
                }
            }, error -> {
                if (current == generation) {
                    status.setText("Could not load variants.");
                    Dialogs.showError(this, "Load variants of " + model.id(), error);
                }
            });
    }

    private void installSelected() {
        int row = variantTable.getSelectedRow();
        if (row < 0) {
            return;
        }
        install.accept(variants.row(variantTable.convertRowIndexToModel(row)).pullName());
        dispose();
    }

    private void updateButtons() {
        installButton.setEnabled(variantTable.getSelectedRow() >= 0);
    }

    private static final class ResultModel extends AbstractTableModel {
        private static final long serialVersionUID = 1L;
        private static final String[] COLUMNS = {"Model", "Capabilities", "Sizes", "Popularity", "Updated"};
        private final List<CatalogModel> rows = new ArrayList<CatalogModel>();

        CatalogModel row(int index) {
            return rows.get(index);
        }

        void setRows(List<CatalogModel> values) {
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
        public Object getValueAt(int row, int column) {
            CatalogModel model = rows.get(row);
            switch (column) {
                case 0:
                    return model.id();
                case 1:
                    return String.join(", ", model.capabilities());
                case 2:
                    return String.join(", ", model.sizes());
                case 3:
                    return model.popularity();
                default:
                    return model.updated();
            }
        }
    }

    private static final class VariantModel extends AbstractTableModel {
        private static final long serialVersionUID = 1L;
        private static final String[] COLUMNS = {"Pull name", "Size", "Context", "Inputs", "Recommended"};
        private final List<CatalogVariant> rows = new ArrayList<CatalogVariant>();

        CatalogVariant row(int index) {
            return rows.get(index);
        }

        void setRows(List<CatalogVariant> values) {
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
        public Object getValueAt(int row, int column) {
            CatalogVariant variant = rows.get(row);
            switch (column) {
                case 0:
                    return variant.pullName();
                case 1:
                    return variant.size();
                case 2:
                    return variant.context();
                case 3:
                    return String.join(", ", variant.inputs());
                default:
                    return variant.recommended() ? "yes" : "";
            }
        }
    }
}
