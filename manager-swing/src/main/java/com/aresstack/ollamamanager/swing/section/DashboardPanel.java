package com.aresstack.ollamamanager.swing.section;

import com.aresstack.ollamamanager.domain.configuration.ManagerConfiguration;
import com.aresstack.ollamamanager.domain.hardware.GpuDescriptor;
import com.aresstack.ollamamanager.domain.hardware.GpuUsageSample;
import com.aresstack.ollamamanager.domain.hardware.HardwareSnapshot;
import com.aresstack.ollamamanager.domain.server.RuntimeOwnership;
import com.aresstack.ollamamanager.domain.server.ServerState;
import com.aresstack.ollamamanager.domain.server.ServerStatus;
import com.aresstack.ollamamanager.swing.component.CardPanel;
import com.aresstack.ollamamanager.swing.component.Dialogs;
import com.aresstack.ollamamanager.swing.component.FormPanel;
import com.aresstack.ollamamanager.swing.component.JobProgressPanel;
import com.aresstack.ollamamanager.swing.component.MessageBanner;
import com.aresstack.ollamamanager.swing.component.MeterBar;
import com.aresstack.ollamamanager.swing.component.SectionPanel;
import com.aresstack.ollamamanager.swing.component.Spacing;
import com.aresstack.ollamamanager.swing.component.StatusBadge;
import com.aresstack.ollamamanager.swing.component.Theme;
import com.aresstack.ollamamanager.swing.component.ValueLabel;
import com.aresstack.ollamamanager.swing.job.Job;
import com.aresstack.ollamamanager.swing.shell.Section;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.List;
import java.util.Locale;

/**
 * Server status and hardware overview. Pure view: all data comes from {@link DashboardPresenter}.
 */
public final class DashboardPanel extends SectionPanel implements DashboardView {

    private static final long serialVersionUID = 1L;

    private final DashboardPresenter presenter;
    private final MessageBanner banner = new MessageBanner();
    private final StatusBadge serverState = new StatusBadge();
    private final StatusBadge ownership = new StatusBadge();
    private final ValueLabel version = new ValueLabel();
    private final ValueLabel endpoint = new ValueLabel();
    private final ValueLabel installation = new ValueLabel();
    private final ValueLabel os = new ValueLabel();
    private final ValueLabel cpu = new ValueLabel();
    private final ValueLabel memory = new ValueLabel();
    private final ValueLabel gpus = new ValueLabel();
    private final FormPanel gpuUsage = new FormPanel();
    private final ValueLabel gpuUsageText = new ValueLabel();
    private final JButton refresh = new JButton("Refresh");
    private final JButton stop = new JButton("Stop server…");
    private final JButton restart = new JButton("Restart…");
    private final JobProgressPanel progress = new JobProgressPanel();

    public DashboardPanel(DashboardPresenter presenter) {
        super(Section.DASHBOARD.title(), Section.DASHBOARD.description());
        this.presenter = presenter;
        refresh.setMnemonic('R');
        stop.setMnemonic('S');
        stop.setToolTipText("Stops the server after a confirmation. Running requests are aborted.");
        stop.setEnabled(false);
        restart.setToolTipText("Restarts the server with the stored server settings after a confirmation. An external"
            + " server is replaced by one started from the same installation.");
        restart.setEnabled(false);
        restart.addActionListener(event -> presenter.restartRequested());
        refresh.addActionListener(event -> presenter.refresh());
        stop.addActionListener(event -> presenter.stopRequested());
        addToolbarItem(refresh);

        FormPanel server = new FormPanel();
        server.addRow("State", serverState, "Lifecycle state of the server the manager is looking at.");
        server.addRow("Owner", ownership, "Whether Ollama Manager started this server. Both kinds can be stopped and restarted after a confirmation.");
        server.addRow("Version", version, "Version reported by the running server.");
        server.addRow("Configured address", endpoint, "Address the manager uses for its own server (OLLAMA_HOST).");
        server.addRow("Installation", installation, "Selected installation kind from the configuration.");
        CardPanel serverCard = new CardPanel("Server", server);
        JPanel serverActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        serverActions.setOpaque(false);
        serverActions.add(restart);
        serverActions.add(stop);
        serverCard.setTrailing(serverActions);

        FormPanel hardware = new FormPanel();
        hardware.addRow("System", os, "Operating system and architecture.");
        hardware.addRow("CPU", cpu, "Processor name and logical cores.");
        hardware.addRow("Memory", memory, "Installed system memory.");
        hardware.addRow("GPUs", gpus, "Detected GPUs. Detection is not proof that Ollama uses the GPU.");
        CardPanel hardwareCard = new CardPanel("Hardware", hardware);

        gpuUsage.addRow("GPU usage", gpuUsageText, "Live VRAM and utilisation from GPU telemetry.");
        CardPanel usageCard = new CardPanel("GPU usage", gpuUsage);

        JPanel column = new JPanel();
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
        column.setOpaque(false);
        column.add(serverCard);
        column.add(Box.createVerticalStrut(Spacing.GAP));
        column.add(hardwareCard);
        column.add(Box.createVerticalStrut(Spacing.GAP));
        column.add(usageCard);
        column.add(Box.createVerticalStrut(Spacing.GAP));
        column.add(progress);

        JPanel north = new JPanel(new BorderLayout(0, Spacing.GAP));
        north.setOpaque(false);
        north.add(banner, BorderLayout.NORTH);
        north.add(column, BorderLayout.CENTER);
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(north, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(top);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(Spacing.MARGIN);
        setBody(scroll);
        presenter.bind(this);
        // The server may have been started, attached or stopped elsewhere meanwhile: never show a stale state.
        addHierarchyListener(event -> {
            if ((event.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0 && isShowing()) {
                presenter.refreshStatus();
            }
        });
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
            serverState.show("Not connected in this build (server lifecycle not wired)", Theme.Tone.NEUTRAL);
            ownership.show("", Theme.Tone.NEUTRAL);
            version.show("");
            stop.setEnabled(false);
            restart.setEnabled(false);
            banner.clear();
            return;
        }
        String message = status.message().isEmpty() ? "" : " — " + status.message();
        serverState.show(status.state().name().toLowerCase(Locale.ROOT) + message, stateTone(status.state()));
        serverState.setPulsing(status.state() == ServerState.STARTING || status.state() == ServerState.STOPPING);
        ownership.show(ownershipText(status.ownership()), ownershipTone(status.ownership()));
        version.show(status.reportedVersion());
        stop.setEnabled(presenter.canStop(status));
        restart.setEnabled(presenter.canRestart(status));
        if (status.ownership() == RuntimeOwnership.EXTERNAL) {
            banner.info("This server was found running but not started by Ollama Manager. Models can be managed"
                + " on it. Restart replaces it with a server started from the same installation with the stored"
                + " server settings; Stop ends it. Both ask for confirmation first.");
        } else if (status.state() == ServerState.FAILED) {
            banner.error("The managed server failed." + (status.message().isEmpty() ? "" : " " + status.message())
                + " See Jobs & logs for the server log.");
        } else {
            banner.clear();
        }
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
        gpuUsage.removeAll();
        gpuUsage.reset();
        if (samples == null) {
            gpuUsage.addRow("GPU usage", gpuUsageText, "Live VRAM and utilisation from GPU telemetry.");
            gpuUsageText.show("Not connected in this build (GPU telemetry not wired)");
        } else if (samples.isEmpty()) {
            gpuUsage.addRow("GPU usage", gpuUsageText, "Live VRAM and utilisation from GPU telemetry.");
            gpuUsageText.show("No telemetry tool available");
        } else {
            for (GpuUsageSample sample : samples) {
                MeterBar meter = new MeterBar();
                meter.show(fraction(sample), usageCaption(sample));
                gpuUsage.addRow(sample.gpuName(), meter, "VRAM in use and GPU utilisation reported by the driver.");
            }
        }
        gpuUsage.addFiller();
        gpuUsage.revalidate();
        gpuUsage.repaint();
    }

    static double fraction(GpuUsageSample sample) {
        if (sample.usedVramBytes() == null || sample.totalVramBytes() == null || sample.totalVramBytes() <= 0) {
            return -1;
        }
        return sample.usedVramBytes() / (double) sample.totalVramBytes();
    }

    static String usageCaption(GpuUsageSample sample) {
        StringBuilder text = new StringBuilder();
        if (sample.usedVramBytes() != null && sample.totalVramBytes() != null) {
            text.append(Format.bytes(sample.usedVramBytes())).append(" / ")
                .append(Format.bytes(sample.totalVramBytes())).append(" VRAM");
        } else {
            text.append("VRAM unknown");
        }
        if (sample.utilizationPercent() != null) {
            text.append(", ").append(sample.utilizationPercent()).append(" % busy");
        }
        return text.toString();
    }

    static Theme.Tone stateTone(ServerState state) {
        switch (state) {
            case RUNNING:
                return Theme.Tone.SUCCESS;
            case STARTING:
            case STOPPING:
                return Theme.Tone.INFO;
            case FAILED:
                return Theme.Tone.ERROR;
            case STOPPED:
            default:
                return Theme.Tone.NEUTRAL;
        }
    }

    static Theme.Tone ownershipTone(RuntimeOwnership ownership) {
        switch (ownership) {
            case OWNED:
                return Theme.Tone.SUCCESS;
            case EXTERNAL:
                return Theme.Tone.WARNING;
            case NONE:
            default:
                return Theme.Tone.NEUTRAL;
        }
    }

    static String ownershipText(RuntimeOwnership ownership) {
        switch (ownership) {
            case OWNED:
                return "started by Ollama Manager";
            case EXTERNAL:
                return "external (not started by the manager)";
            case NONE:
            default:
                return "no server";
        }
    }

    @Override
    public void setStopEnabled(boolean enabled) {
        stop.setEnabled(enabled);
    }

    @Override
    public void setRestartEnabled(boolean enabled) {
        restart.setEnabled(enabled);
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
    public boolean confirmStop(boolean external) {
        return Dialogs.confirmStop(this, external);
    }

    @Override
    public boolean confirmRestart(boolean external) {
        return Dialogs.confirmRestart(this, external);
    }
}
