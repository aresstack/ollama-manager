package com.aresstack.ollamamanager.swing.section;

import com.aresstack.ollamamanager.domain.configuration.ManagerConfiguration;
import com.aresstack.ollamamanager.domain.hardware.GpuDescriptor;
import com.aresstack.ollamamanager.domain.hardware.GpuUsageSample;
import com.aresstack.ollamamanager.domain.hardware.HardwareSnapshot;
import com.aresstack.ollamamanager.domain.server.ServerStatus;
import com.aresstack.ollamamanager.swing.component.Dialogs;
import com.aresstack.ollamamanager.swing.component.FormPanel;
import com.aresstack.ollamamanager.swing.component.JobProgressPanel;
import com.aresstack.ollamamanager.swing.component.SectionPanel;
import com.aresstack.ollamamanager.swing.component.Spacing;
import com.aresstack.ollamamanager.swing.component.ValueLabel;
import com.aresstack.ollamamanager.swing.job.Job;
import com.aresstack.ollamamanager.swing.shell.Section;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.util.List;
import java.util.Locale;

/**
 * Server status and hardware overview. Pure view: all data comes from {@link DashboardPresenter}.
 */
public final class DashboardPanel extends SectionPanel implements DashboardView {

    private static final long serialVersionUID = 1L;

    private final DashboardPresenter presenter;
    private final ValueLabel serverState = new ValueLabel();
    private final ValueLabel ownership = new ValueLabel();
    private final ValueLabel version = new ValueLabel();
    private final ValueLabel endpoint = new ValueLabel();
    private final ValueLabel installation = new ValueLabel();
    private final ValueLabel os = new ValueLabel();
    private final ValueLabel cpu = new ValueLabel();
    private final ValueLabel memory = new ValueLabel();
    private final ValueLabel gpus = new ValueLabel();
    private final ValueLabel gpuUsage = new ValueLabel();
    private final JButton refresh = new JButton("Refresh");
    private final JButton stop = new JButton("Stop server…");
    private final JobProgressPanel progress = new JobProgressPanel();

    public DashboardPanel(DashboardPresenter presenter) {
        super(Section.DASHBOARD.title(), Section.DASHBOARD.description());
        this.presenter = presenter;
        refresh.setMnemonic('R');
        stop.setMnemonic('S');
        stop.setToolTipText("Stops only a server started by Ollama Manager. On Windows running requests are aborted.");
        stop.setEnabled(false);
        refresh.addActionListener(event -> presenter.refresh());
        stop.addActionListener(event -> presenter.stopRequested());
        addToolbarItem(refresh);
        addToolbarItem(stop);

        FormPanel server = new FormPanel();
        server.setBorder(BorderFactory.createTitledBorder("Server"));
        server.addRow("State", serverState, "Lifecycle state of the server the manager is looking at.");
        server.addRow("Owner", ownership, "Only servers started by Ollama Manager can be stopped or restarted.");
        server.addRow("Version", version, "Version reported by the running server.");
        server.addRow("Configured address", endpoint, "Address the manager uses for its own server (OLLAMA_HOST).");
        server.addRow("Installation", installation, "Selected installation kind from the configuration.");

        FormPanel hardware = new FormPanel();
        hardware.setBorder(BorderFactory.createTitledBorder("Hardware"));
        hardware.addRow("System", os, "Operating system and architecture.");
        hardware.addRow("CPU", cpu, "Processor name and logical cores.");
        hardware.addRow("Memory", memory, "Installed system memory.");
        hardware.addRow("GPUs", gpus, "Detected GPUs. Detection is not proof that Ollama uses the GPU.");
        hardware.addRow("GPU usage", gpuUsage, "Live VRAM and utilisation from GPU telemetry.");

        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.add(server);
        body.add(javax.swing.Box.createVerticalStrut(Spacing.GAP));
        body.add(hardware);
        body.add(javax.swing.Box.createVerticalStrut(Spacing.GAP));
        body.add(progress);
        JPanel north = new JPanel(new java.awt.BorderLayout());
        north.add(body, java.awt.BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(north);
        scroll.setBorder(null);
        setBody(scroll);
        presenter.bind(this);
    }

    @Override
    public void showConfiguration(ManagerConfiguration configuration) {
        endpoint.show(String.valueOf(configuration.desiredServerSettings().bind()));
        installation.show(configuration.selectedInstallationKind() == null ? "Not selected yet"
            : configuration.selectedInstallationKind().name().toLowerCase(Locale.ROOT));
    }

    @Override
    public void showServerStatus(ServerStatus status) {
        if (status == null) {
            serverState.show("Not connected in this build (server lifecycle not wired)");
            ownership.show("");
            version.show("");
            stop.setEnabled(false);
            return;
        }
        String message = status.message().isEmpty() ? "" : " — " + status.message();
        serverState.show(status.state().name().toLowerCase(Locale.ROOT) + message);
        ownership.show(status.ownership().name().toLowerCase(Locale.ROOT));
        version.show(status.reportedVersion());
        stop.setEnabled(presenter.canStop(status));
    }

    @Override
    public void showHardware(HardwareSnapshot snapshot) {
        if (snapshot == null) {
            os.show("Not connected in this build (hardware probe not wired)");
            return;
        }
        os.show(snapshot.operatingSystem() + " " + snapshot.architecture());
        cpu.show(snapshot.cpuName() + " (" + snapshot.logicalProcessors() + " logical)");
        memory.show(Format.bytes(snapshot.totalMemoryBytes()));
        StringBuilder text = new StringBuilder();
        for (GpuDescriptor gpu : snapshot.gpus()) {
            if (text.length() > 0) {
                text.append("; ");
            }
            text.append(gpu.name()).append(", ").append(Format.bytes(gpu.vramBytes()));
            if (gpu.computeCapability() != null) {
                text.append(", compute ").append(gpu.computeCapability());
            }
        }
        gpus.show(text.length() == 0 ? "No GPU detected" : text.toString());
    }

    @Override
    public void showGpuUsage(List<GpuUsageSample> samples) {
        if (samples == null) {
            gpuUsage.show("Not connected in this build (GPU telemetry not wired)");
        } else if (samples.isEmpty()) {
            gpuUsage.show("No telemetry tool available");
        } else {
            StringBuilder text = new StringBuilder();
            for (GpuUsageSample sample : samples) {
                if (text.length() > 0) {
                    text.append("; ");
                }
                text.append(sample.gpuName()).append(": ").append(Format.bytes(sample.usedVramBytes()))
                    .append(" / ").append(Format.bytes(sample.totalVramBytes()));
                if (sample.utilizationPercent() != null) {
                    text.append(", ").append(sample.utilizationPercent()).append(" %");
                }
            }
            gpuUsage.show(text.toString());
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
    public boolean confirmStop() {
        return Dialogs.confirmStop(this);
    }
}
