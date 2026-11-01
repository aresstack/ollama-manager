package com.aresstack.ollamamanager.swing.section;

import com.aresstack.ollamamanager.application.hardware.Confidence;
import com.aresstack.ollamamanager.application.hardware.PackageRecommendation;
import com.aresstack.ollamamanager.domain.distribution.DistributionPackage;
import com.aresstack.ollamamanager.domain.server.InstallationKind;
import com.aresstack.ollamamanager.domain.server.OllamaInstallation;
import com.aresstack.ollamamanager.swing.component.ActionBar;
import com.aresstack.ollamamanager.swing.component.CardPanel;
import com.aresstack.ollamamanager.swing.component.Dialogs;
import com.aresstack.ollamamanager.swing.component.EmptyState;
import com.aresstack.ollamamanager.swing.component.JobProgressPanel;
import com.aresstack.ollamamanager.swing.component.MessageBanner;
import com.aresstack.ollamamanager.swing.component.ScrollColumn;
import com.aresstack.ollamamanager.swing.component.SectionPanel;
import com.aresstack.ollamamanager.swing.component.Spacing;
import com.aresstack.ollamamanager.swing.component.StatusBadge;
import com.aresstack.ollamamanager.swing.component.Tables;
import com.aresstack.ollamamanager.swing.component.Theme;
import com.aresstack.ollamamanager.swing.component.WrapLabel;
import com.aresstack.ollamamanager.swing.job.Job;
import com.aresstack.ollamamanager.swing.shell.Section;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.event.HierarchyEvent;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Setup assistant in three steps: a server found on the configured endpoint, installations on this computer,
 * and a portable installation with package recommendation, progress and Cancel. Pure view: every action goes
 * through {@link SetupPresenter}, which asks for confirmation through this view before starting or removing.
 */
public final class SetupPanel extends SectionPanel implements SetupView {

    private static final long serialVersionUID = 1L;

    private final StatusBadge runningState = new StatusBadge("Not checked yet", Theme.Tone.NEUTRAL);
    private final WrapLabel runningText = new WrapLabel("Detection runs when this section opens.");
    private final JButton stopServer = new JButton("Stop server…");
    private final JButton restartServer = new JButton("Restart…");

    private final InstallationTableModel installationModel = new InstallationTableModel();
    private final JTable installations = new JTable(installationModel);
    private final EmptyState noInstallations = new EmptyState("No Ollama installation found",
        "Install a portable version below, or install Ollama normally and detect again.");
    private final CardLayout installationCards = new CardLayout();
    private final JPanel installationList = new JPanel(installationCards);
    private final JButton select = new JButton("Select");
    private final JButton start = new JButton("Start selected…");
    private final JButton uninstall = new JButton("Remove…");

    private final WrapLabel recommendationText = new WrapLabel(
        "Recommend packages to check the GPU and choose the portable packages that fit this computer.");
    private final StatusBadge confidence = new StatusBadge();
    private final Map<DistributionPackage, JCheckBox> packages =
        new EnumMap<DistributionPackage, JCheckBox>(DistributionPackage.class);
    private final JButton recommend = new JButton("Recommend packages");
    private final JButton install = new JButton("Install latest");

    private final JButton detect = new JButton("Detect again");
    private final MessageBanner notice = new MessageBanner();
    private final JobProgressPanel progress = new JobProgressPanel();
    private SetupDetection detection;
    private final SetupPresenter presenter;
    private boolean busy;

    public SetupPanel(SetupPresenter presenter) {
        super(Section.SETUP.title(), Section.SETUP.description());
        this.presenter = presenter;
        detect.setMnemonic('D');
        detect.addActionListener(event -> presenter.detect());
        addToolbarItem(detect);

        JPanel column = new JPanel();
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
        column.setOpaque(false);
        column.add(runningCard(presenter));
        column.add(Box.createVerticalStrut(Spacing.GAP));
        column.add(installationCard(presenter));
        column.add(Box.createVerticalStrut(Spacing.GAP));
        column.add(portableCard(presenter));

        JPanel south = new JPanel(new BorderLayout(0, Spacing.GAP));
        south.setOpaque(false);
        south.add(notice, BorderLayout.NORTH);
        south.add(progress, BorderLayout.SOUTH);
        JPanel body = new JPanel(new BorderLayout(0, Spacing.GAP));
        body.setOpaque(false);
        body.add(ScrollColumn.wrap(column), BorderLayout.CENTER);
        body.add(south, BorderLayout.SOUTH);
        setBody(body);
        updateButtons();
        presenter.bind(this);
        addHierarchyListener(event -> {
            if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && isShowing()) {
                presenter.detect();
            }
        });
    }

    private CardPanel runningCard(SetupPresenter presenter) {
        stopServer.setToolTipText("Stop this server after a confirmation. Running requests are aborted; on Windows"
            + " the Ollama app is ended too.");
        stopServer.addActionListener(event -> presenter.stopRunningServer());
        restartServer.setToolTipText("Restart this server with the stored server settings after a confirmation. A"
            + " server not started by the manager is started again from the same installation.");
        restartServer.addActionListener(event -> presenter.restartRunningServer());
        JPanel content = new JPanel(new BorderLayout(0, Spacing.GAP));
        content.setOpaque(false);
        content.add(runningText, BorderLayout.CENTER);
        content.add(new ActionBar().addAction(restartServer)
            .addAction(stopServer), BorderLayout.SOUTH);
        CardPanel card = new CardPanel("1 · Server on the configured address", content);
        card.setTrailing(runningState);
        return card;
    }

    private CardPanel installationCard(SetupPresenter presenter) {
        Tables.setup(installations, "Installations");
        installations.setAutoCreateRowSorter(false);
        installations.getColumnModel().getColumn(3).setCellRenderer(Tables.toneRenderer(
            value -> "selected".equals(value) ? Theme.Tone.SUCCESS : Theme.Tone.NEUTRAL));
        Tables.setWidthInChars(installations, 0, 12);
        Tables.setWidthInChars(installations, 1, 10);
        Tables.setWidthInChars(installations, 2, 40);
        Tables.setWidthInChars(installations, 3, 10);
        installations.getSelectionModel().addListSelectionListener(event -> updateButtons());
        JScrollPane scroll = new JScrollPane(installations);
        scroll.setPreferredSize(new Dimension(100, installations.getRowHeight() * 5
            + installations.getTableHeader().getPreferredSize().height + 4));
        installationList.setOpaque(false);
        installationList.add(scroll, "table");
        installationList.add(noInstallations, "empty");
        installationCards.show(installationList, "empty");

        select.setMnemonic('S');
        select.setToolTipText("Remember this installation as the one the manager starts.");
        select.addActionListener(event -> presenter.select(selectedInstallation()));
        start.setMnemonic('T');
        start.setToolTipText("Start the selected installation as the manager's own server with the stored"
            + " server settings.");
        start.addActionListener(event -> presenter.startSelected());
        uninstall.setToolTipText("Remove a portable version from disk. Refused while it runs.");
        uninstall.addActionListener(event -> presenter.uninstall(selectedInstallation()));

        JPanel content = new JPanel(new BorderLayout(0, Spacing.GAP));
        content.setOpaque(false);
        content.add(new WrapLabel("Portable versions managed here and regular installations found on this"
            + " computer. The selected one is what Start uses."), BorderLayout.NORTH);
        content.add(installationList, BorderLayout.CENTER);
        content.add(new ActionBar().addAction(select).addAction(start).addTrailing(uninstall), BorderLayout.SOUTH);
        return new CardPanel("2 · Installations on this computer", content);
    }

    private CardPanel portableCard(SetupPresenter presenter) {
        JPanel packageRow = new JPanel();
        packageRow.setLayout(new BoxLayout(packageRow, BoxLayout.X_AXIS));
        packageRow.setOpaque(false);
        for (DistributionPackage value : DistributionPackage.values()) {
            JCheckBox box = new JCheckBox(packageLabel(value), value == DistributionPackage.BASE);
            box.setToolTipText(value.assetName());
            packages.put(value, box);
            if (packageRow.getComponentCount() > 0) {
                packageRow.add(Box.createHorizontalStrut(Spacing.MARGIN));
            }
            packageRow.add(box);
        }
        recommend.setMnemonic('R');
        recommend.setToolTipText("Check the GPU and tick the packages that fit it.");
        recommend.addActionListener(event -> presenter.recommend());
        install.setMnemonic('I');
        install.setToolTipText("Download the latest stable release into the manager's portable directory."
            + " The download can be cancelled.");
        install.addActionListener(event -> presenter.install(selectedPackages()));
        confidence.setVisible(false);
        confidence.setFocusable(false);

        JPanel content = new JPanel(new BorderLayout(0, Spacing.GAP));
        content.setOpaque(false);
        content.add(recommendationText, BorderLayout.NORTH);
        content.add(packageRow, BorderLayout.CENTER);
        content.add(new ActionBar().addAction(recommend).addAction(install), BorderLayout.SOUTH);
        CardPanel card = new CardPanel("3 · Portable installation", content);
        card.setTrailing(confidence);
        return card;
    }

    static String packageLabel(DistributionPackage value) {
        switch (value) {
            case BASE:
                return "Base (CPU, NVIDIA CUDA)";
            case ROCM:
                return "AMD ROCm add-on";
            case MLX:
                return "MLX add-on";
            default:
                return value.name();
        }
    }

    @Override
    public void showDetection(SetupDetection value) {
        detection = value;
        String host = value.endpoint().toHostValue();
        if (value.isServerRunning()) {
            runningState.show("in use", Theme.Tone.SUCCESS);
            runningText.setText("Ollama " + value.runningVersion() + " answers on " + host + "."
                + (value.isExternalAttached()
                    ? " It was not started by the manager and is used automatically: models can be managed on it,"
                        + " and Restart or Stop control it after a confirmation."
                    : " The manager uses it: models can be managed on it, and Restart or Stop control it after a"
                        + " confirmation."));
        } else if (value.probeFailure() != null) {
            runningState.show("occupied, not usable", Theme.Tone.WARNING);
            runningText.setText(host + " is occupied but does not answer like an Ollama server: "
                + value.probeFailure() + " Change the network address in the server settings, or free the port.");
        } else {
            runningState.show("no server", Theme.Tone.NEUTRAL);
            runningText.setText("No server answers on " + host + ". Select an installation below and start it, or"
                + " install a portable version.");
        }
        OllamaInstallation previous = selectedInstallation();
        installationModel.setRows(value.installations(), value);
        installationCards.show(installationList, value.installations().isEmpty() ? "empty" : "table");
        OllamaInstallation keep = previous != null && value.installations().contains(previous)
            ? previous : value.selected();
        if (keep != null) {
            int row = value.installations().indexOf(keep);
            installations.setRowSelectionInterval(row, row);
        }
        if (value.isSelectionMissing()) {
            showNotice("The stored installation no longer exists on disk. Select another one or install a portable"
                + " version.");
        }
        updateButtons();
    }

    @Override
    public void showRecommendation(PackageRecommendation value) {
        StringBuilder text = new StringBuilder();
        if (!value.isPlatformSupported()) {
            text.append("This platform is not supported by the portable packages. ");
        }
        text.append("Recommended: ");
        List<String> names = new ArrayList<String>();
        for (DistributionPackage pkg : value.packages()) {
            names.add(packageLabel(pkg));
        }
        text.append(names.isEmpty() ? "none" : String.join(", ", names)).append('.');
        for (String reason : value.reasons()) {
            text.append("\n• ").append(reason);
        }
        recommendationText.setText(text.toString());
        confidence.show("confidence: " + value.confidence().name().toLowerCase(java.util.Locale.ROOT),
            confidenceTone(value.confidence()));
        confidence.setVisible(true);
        for (Map.Entry<DistributionPackage, JCheckBox> entry : packages.entrySet()) {
            entry.getValue().setSelected(value.packages().contains(entry.getKey()));
        }
    }

    static Theme.Tone confidenceTone(Confidence value) {
        switch (value) {
            case HIGH:
                return Theme.Tone.SUCCESS;
            case MEDIUM:
                return Theme.Tone.WARNING;
            case LOW:
            default:
                return Theme.Tone.ERROR;
        }
    }

    @Override
    public void setBusy(boolean value) {
        busy = value;
        detect.setEnabled(!value);
        updateButtons();
    }

    @Override
    public void showNotice(String text) {
        if (text == null || text.isEmpty()) {
            notice.clear();
        } else {
            notice.show(noticeTone(text), text);
        }
    }

    static Theme.Tone noticeTone(String text) {
        if (text.startsWith("Not removed") || text.contains("cannot be used") || text.contains("no longer exists")
            || text.startsWith("Select ") || text.startsWith("Another server") || text.contains("already")
            || text.startsWith("The manager still")) {
            return Theme.Tone.WARNING;
        }
        if (text.contains("cancelled")) {
            return Theme.Tone.NEUTRAL;
        }
        return Theme.Tone.SUCCESS;
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
    public boolean confirmUninstall(OllamaInstallation installation) {
        return Dialogs.confirm(this, "Remove portable version", "Remove Ollama " + installation.version()
            + " from\n" + installation.binaryDirectory() + "?\n\nDownloaded models are not affected.");
    }

    @Override
    public boolean confirmStop(boolean external) {
        return Dialogs.confirmStop(this, external);
    }

    @Override
    public boolean confirmRestart(boolean external) {
        return Dialogs.confirmRestart(this, external);
    }

    @Override
    public boolean confirmStart(OllamaInstallation installation, String endpoint) {
        return Dialogs.confirm(this, "Start server", "Start Ollama " + installation.version() + " from\n"
            + installation.binaryDirectory() + "\nas the manager's own server on " + endpoint
            + " with the stored server settings?");
    }

    @Override
    public void updateUI() {
        super.updateUI();
        if (installations != null) {
            Tables.fitRowHeight(installations);
        }
    }

    private Set<DistributionPackage> selectedPackages() {
        Set<DistributionPackage> result = EnumSet.noneOf(DistributionPackage.class);
        for (Map.Entry<DistributionPackage, JCheckBox> entry : packages.entrySet()) {
            if (entry.getValue().isSelected()) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    private OllamaInstallation selectedInstallation() {
        int row = installations.getSelectedRow();
        return row < 0 ? null : installationModel.row(installations.convertRowIndexToModel(row));
    }

    private void updateButtons() {
        OllamaInstallation chosen = selectedInstallation();
        boolean control = !busy && presenter.canControlRunning(detection);
        stopServer.setEnabled(control);
        restartServer.setEnabled(control);
        select.setEnabled(!busy && chosen != null);
        start.setEnabled(!busy && detection != null && detection.selected() != null);
        uninstall.setEnabled(!busy && chosen != null && chosen.kind() == InstallationKind.MANAGED_PORTABLE);
        recommend.setEnabled(!busy);
        install.setEnabled(!busy);
        for (JCheckBox box : packages.values()) {
            box.setEnabled(!busy);
        }
    }

    private static final class InstallationTableModel extends AbstractTableModel {
        private static final long serialVersionUID = 1L;
        private static final String[] COLUMNS = {"Version", "Kind", "Folder", "Use"};
        private final List<OllamaInstallation> rows = new ArrayList<OllamaInstallation>();
        private SetupDetection detection;

        OllamaInstallation row(int index) {
            return rows.get(index);
        }

        void setRows(List<OllamaInstallation> values, SetupDetection current) {
            rows.clear();
            rows.addAll(values);
            detection = current;
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
            OllamaInstallation installation = rows.get(index);
            switch (column) {
                case 0:
                    return "Ollama " + installation.version();
                case 1:
                    return installation.kind() == InstallationKind.MANAGED_PORTABLE ? "portable" : "installed";
                case 2:
                    return installation.binaryDirectory() == null ? "" : installation.binaryDirectory().getPath();
                default:
                    return detection != null && detection.isSelected(installation) ? "selected" : "";
            }
        }
    }
}
