package com.aresstack.ollamamanager.swing.section;

import com.aresstack.ollamamanager.application.hardware.PackageRecommendation;
import com.aresstack.ollamamanager.domain.distribution.DistributionPackage;
import com.aresstack.ollamamanager.domain.server.InstallationKind;
import com.aresstack.ollamamanager.domain.server.OllamaInstallation;
import com.aresstack.ollamamanager.swing.component.Dialogs;
import com.aresstack.ollamamanager.swing.component.JobProgressPanel;
import com.aresstack.ollamamanager.swing.component.SectionPanel;
import com.aresstack.ollamamanager.swing.component.Spacing;
import com.aresstack.ollamamanager.swing.job.Job;
import com.aresstack.ollamamanager.swing.shell.Section;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.HierarchyEvent;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Setup assistant in three blocks: a server found on the configured endpoint, installations on disk, and a
 * portable installation with package recommendation, progress and Cancel.
 */
public final class SetupPanel extends SectionPanel implements SetupView {

    private static final long serialVersionUID = 1L;

    private final JLabel running = new JLabel("Not checked yet.");
    private final JButton useRunning = new JButton("Use running server (read-only)");
    private final JButton stopUsing = new JButton("Stop using it");
    private final DefaultListModel<OllamaInstallation> installationModel = new DefaultListModel<OllamaInstallation>();
    private final JList<OllamaInstallation> installations = new JList<OllamaInstallation>(installationModel);
    private final JButton select = new JButton("Select");
    private final JButton start = new JButton("Start selected…");
    private final JButton uninstall = new JButton("Remove…");
    private final JTextArea recommendation = new JTextArea(4, 40);
    private final Map<DistributionPackage, JCheckBox> packages =
        new EnumMap<DistributionPackage, JCheckBox>(DistributionPackage.class);
    private final JButton recommend = new JButton("Recommend packages");
    private final JButton install = new JButton("Install latest");
    private final JButton detect = new JButton("Detect again");
    private final JLabel notice = new JLabel(" ");
    private final JobProgressPanel progress = new JobProgressPanel();
    private SetupDetection detection;
    private boolean busy;

    public SetupPanel(SetupPresenter presenter) {
        super(Section.SETUP.title(), Section.SETUP.description());
        detect.setMnemonic('D');
        detect.addActionListener(event -> presenter.detect());
        addToolbarItem(detect);

        useRunning.setMnemonic('U');
        useRunning.setToolTipText("Show this server in the manager. It is never stopped, restarted or changed.");
        useRunning.addActionListener(event -> presenter.useRunningServer());
        stopUsing.setToolTipText("Forget the external server in the manager. The server keeps running unchanged.");
        stopUsing.addActionListener(event -> presenter.stopUsingExternal());
        JPanel runningBlock = new JPanel(new BorderLayout(0, Spacing.GAP));
        runningBlock.setBorder(BorderFactory.createTitledBorder("Running server"));
        runningBlock.add(running, BorderLayout.CENTER);
        runningBlock.add(row(useRunning, stopUsing), BorderLayout.SOUTH);

        installations.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        installations.setVisibleRowCount(4);
        installations.getAccessibleContext().setAccessibleName("Installations");
        installations.setCellRenderer((list, value, index, selected, focused) -> {
            JLabel label = new JLabel(describe(value));
            label.setOpaque(true);
            label.setBackground(selected ? list.getSelectionBackground() : list.getBackground());
            label.setForeground(selected ? list.getSelectionForeground() : list.getForeground());
            return label;
        });
        installations.addListSelectionListener(event -> updateButtons());
        select.setMnemonic('S');
        select.addActionListener(event -> presenter.select(installations.getSelectedValue()));
        start.setMnemonic('T');
        start.setToolTipText("Start the selected installation as the manager's own server with the stored"
            + " server settings.");
        start.addActionListener(event -> presenter.startSelected());
        uninstall.setToolTipText("Remove a portable version from disk. Refused while it runs.");
        uninstall.addActionListener(event -> presenter.uninstall(installations.getSelectedValue()));
        JPanel installationBlock = new JPanel(new BorderLayout(0, Spacing.GAP));
        installationBlock.setBorder(BorderFactory.createTitledBorder("Installations"));
        installationBlock.add(new JScrollPane(installations), BorderLayout.CENTER);
        installationBlock.add(row(select, start, uninstall), BorderLayout.SOUTH);

        recommendation.setEditable(false);
        recommendation.setLineWrap(true);
        recommendation.setWrapStyleWord(true);
        recommendation.getAccessibleContext().setAccessibleName("Package recommendation");
        recommendation.setText("Recommend packages to check the GPU and choose the portable packages.");
        JPanel packageRow = new JPanel(new FlowLayout(FlowLayout.LEADING, Spacing.GAP, 0));
        for (DistributionPackage value : DistributionPackage.values()) {
            JCheckBox box = new JCheckBox(value.name(), value == DistributionPackage.BASE);
            box.setToolTipText(value.assetName());
            packages.put(value, box);
            packageRow.add(box);
        }
        recommend.setMnemonic('R');
        recommend.addActionListener(event -> presenter.recommend());
        install.setMnemonic('I');
        install.setToolTipText("Download the latest stable release into the manager's portable directory.");
        install.addActionListener(event -> presenter.install(selectedPackages()));
        JPanel portableBlock = new JPanel(new BorderLayout(0, Spacing.GAP));
        portableBlock.setBorder(BorderFactory.createTitledBorder("Portable installation"));
        portableBlock.add(new JScrollPane(recommendation), BorderLayout.CENTER);
        JPanel portableSouth = new JPanel(new BorderLayout(0, Spacing.GAP));
        portableSouth.add(packageRow, BorderLayout.NORTH);
        portableSouth.add(row(recommend, install), BorderLayout.SOUTH);
        portableBlock.add(portableSouth, BorderLayout.SOUTH);

        JPanel blocks = new JPanel();
        blocks.setLayout(new BoxLayout(blocks, BoxLayout.PAGE_AXIS));
        blocks.add(runningBlock);
        blocks.add(Box.createVerticalStrut(Spacing.GAP));
        blocks.add(installationBlock);
        blocks.add(Box.createVerticalStrut(Spacing.GAP));
        blocks.add(portableBlock);

        JPanel south = new JPanel(new BorderLayout(0, Spacing.GAP));
        south.add(notice, BorderLayout.NORTH);
        south.add(progress, BorderLayout.SOUTH);
        JPanel body = new JPanel(new BorderLayout(0, Spacing.GAP));
        body.add(new JScrollPane(blocks), BorderLayout.CENTER);
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

    @Override
    public void showDetection(SetupDetection value) {
        detection = value;
        running.setText(value.isServerRunning()
            ? "Ollama " + value.runningVersion() + " answers on " + value.endpoint().toHostValue() + "."
            : value.probeFailure() != null
                ? value.endpoint().toHostValue() + " is occupied but not usable: " + value.probeFailure()
                : "No server answers on " + value.endpoint().toHostValue() + ".");
        if (value.isExternalAttached()) {
            running.setText(running.getText() + " The manager uses an external server read-only.");
        }
        OllamaInstallation previous = installations.getSelectedValue();
        installationModel.clear();
        for (OllamaInstallation installation : value.installations()) {
            installationModel.addElement(installation);
        }
        OllamaInstallation keep = previous != null && installationModel.contains(previous) ? previous : value.selected();
        if (keep != null) {
            installations.setSelectedValue(keep, true);
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
            text.append("This platform is not supported by the portable packages.\n");
        }
        text.append("Recommended: ").append(value.packages()).append(" (confidence ").append(value.confidence())
            .append(")");
        for (String reason : value.reasons()) {
            text.append("\n• ").append(reason);
        }
        recommendation.setText(text.toString());
        recommendation.setCaretPosition(0);
        for (Map.Entry<DistributionPackage, JCheckBox> entry : packages.entrySet()) {
            entry.getValue().setSelected(value.packages().contains(entry.getKey()));
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
        notice.setText(text == null || text.isEmpty() ? " " : text);
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
    public boolean confirmStart(OllamaInstallation installation, String endpoint) {
        return Dialogs.confirm(this, "Start server", "Start Ollama " + installation.version() + " from\n"
            + installation.binaryDirectory() + "\nas the manager's own server on " + endpoint
            + " with the stored server settings?");
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

    private String describe(OllamaInstallation installation) {
        if (installation == null) {
            return "";
        }
        String kind = installation.kind() == InstallationKind.MANAGED_PORTABLE ? "portable" : "installed";
        String marker = detection != null && detection.isSelected(installation) ? "  (selected)" : "";
        return "Ollama " + installation.version() + " · " + kind + " · " + installation.binaryDirectory() + marker;
    }

    private void updateButtons() {
        OllamaInstallation chosen = installations.getSelectedValue();
        useRunning.setEnabled(!busy && detection != null && detection.isServerRunning()
            && !detection.isExternalAttached());
        stopUsing.setEnabled(!busy && detection != null && detection.isExternalAttached());
        select.setEnabled(!busy && chosen != null);
        start.setEnabled(!busy && detection != null && detection.selected() != null);
        uninstall.setEnabled(!busy && chosen != null && chosen.kind() == InstallationKind.MANAGED_PORTABLE);
        recommend.setEnabled(!busy);
        install.setEnabled(!busy);
        for (JCheckBox box : packages.values()) {
            box.setEnabled(!busy);
        }
    }

    private static JPanel row(JButton... buttons) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEADING, Spacing.GAP, 0));
        for (JButton button : buttons) {
            panel.add(button);
        }
        return panel;
    }
}
