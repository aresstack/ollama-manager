package com.aresstack.ollamamanager.swing.section;

import com.aresstack.ollamamanager.application.common.ErrorKind;
import com.aresstack.ollamamanager.application.common.OperationException;
import com.aresstack.ollamamanager.application.common.TransferProgress;
import com.aresstack.ollamamanager.application.hardware.SupportOverrides;
import com.aresstack.ollamamanager.application.installation.PortableInstallationService;
import com.aresstack.ollamamanager.application.server.ServerLifecycleService;
import com.aresstack.ollamamanager.application.setup.SetupService;
import com.aresstack.ollamamanager.domain.configuration.ManagerConfiguration;
import com.aresstack.ollamamanager.domain.distribution.DistributionPackage;
import com.aresstack.ollamamanager.domain.server.Endpoint;
import com.aresstack.ollamamanager.domain.server.InstallationKind;
import com.aresstack.ollamamanager.domain.server.OllamaInstallation;
import com.aresstack.ollamamanager.domain.server.RuntimeOwnership;
import com.aresstack.ollamamanager.domain.server.ServerSettings;
import com.aresstack.ollamamanager.domain.server.ServerState;
import com.aresstack.ollamamanager.domain.server.ServerStatus;
import com.aresstack.ollamamanager.swing.job.Job;
import com.aresstack.ollamamanager.swing.job.JobState;
import com.aresstack.ollamamanager.swing.job.JobWork;
import com.aresstack.ollamamanager.swing.job.JobManager;
import com.aresstack.ollamamanager.swing.shell.ConsoleServices;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Setup assistant: detect a running server and installations on disk, then either use the running server
 * read-only, select an installation, or install a portable version with progress and Cancel. Every step goes
 * through {@link SetupService}; the shared lifecycle lock in the application layer refuses an uninstall while a
 * start or stop runs, which shows up here as INVALID_STATE.
 */
public final class SetupPresenter {

    static final String NOT_WIRED = "Setup is not connected in this build.";
    static final String ATTACHED = "Using the running server read-only. The manager shows it but never stops,"
        + " restarts or reconfigures it.";
    static final String SELECTED = "Installation selected. Start it here or from the dashboard.";
    static final String INSTALLED = "Portable version installed and selected.";
    static final String INSTALL_CANCELLED = "Installation cancelled. Nothing was selected.";
    static final String UNINSTALLED = "Portable version removed.";
    static final String STARTED = "Server started by the manager.";
    static final String START_CANCELLED = "Start cancelled. Check the dashboard for the server state.";
    static final String LIFECYCLE_BUSY = "Another server operation is running. Try again when it has finished.";
    static final String IN_USE = "This version is in use by the running server, or another server operation is"
        + " running. Stop the server first.";
    static final String PORT_TAKEN = "A server already answers on %s. Use it read-only, or change Bind in the"
        + " server settings to start the manager's own server on another port.";
    static final String ALREADY_RUNNING = "The manager's own server is already running. Stop it on the dashboard"
        + " before starting another installation.";
    static final String NO_PACKAGES = "Select at least the base package.";

    private final ConsoleServices services;
    private final JobManager jobs;
    private SetupView view;
    private SetupDetection detection;
    private boolean busy;
    private long detectGeneration;

    public SetupPresenter(ConsoleServices services, JobManager jobs) {
        this.services = services;
        this.jobs = jobs;
    }

    public void bind(SetupView boundView) {
        this.view = boundView;
    }

    /** Detect a running server on the configured endpoint and installations on disk. Nothing is changed. */
    public void detect() {
        final long generation = ++detectGeneration;
        final SetupService setup = services.setup();
        if (setup == null) {
            view.showNotice(NOT_WIRED);
            return;
        }
        Job job = jobs.submit("Detect Ollama", true, context -> {
            ManagerConfiguration configuration = services.configuration().current();
            Endpoint endpoint = configuration.desiredServerSettings().bind();
            context.phase("Checking " + endpoint.toHostValue());
            String version = setup.detectRunning(endpoint, context.cancellation());
            context.phase("Searching installations");
            List<OllamaInstallation> discovered = setup.detectExisting(Collections.emptyList(),
                context.cancellation());
            PortableInstallationService installations = services.installations();
            List<OllamaInstallation> portable = installations == null
                ? Collections.<OllamaInstallation>emptyList() : installations.installedVersions();
            return new SetupDetection(endpoint, version, discovered, portable,
                configuration.selectedInstallationKind(), configuration.selectedBinaryDirectory());
        }, found -> {
            if (generation == detectGeneration) {
                detection = found;
                view.showDetection(found);
            }
        }, error -> {
            if (generation == detectGeneration) {
                view.showFailure("Detect Ollama", error);
            }
        });
        view.trackJob(job);
    }

    /** Use the server found on the endpoint read-only; it is attached as external and never controlled. */
    public void useRunningServer() {
        final SetupService setup = services.setup();
        if (busy || setup == null || detection == null || !detection.isServerRunning()) {
            return;
        }
        final Endpoint endpoint = detection.endpoint();
        run("Use running server", false, context -> {
            setup.attachExisting(endpoint, context.cancellation());
            return null;
        }, ATTACHED);
    }

    /** Store {@code installation} as the one the manager starts. */
    public void select(OllamaInstallation installation) {
        if (busy || installation == null || !installation.kind().isStartableByManager()) {
            return;
        }
        run("Select installation", false, context -> {
            services.configuration().update(current -> current.withSelectedInstallation(installation.kind(),
                installation.binaryDirectory()));
            return null;
        }, SELECTED);
    }

    /** Probe the hardware and show which portable packages fit. */
    public void recommend() {
        final SetupService setup = services.setup();
        if (busy || setup == null) {
            return;
        }
        setBusy(true);
        jobs.submit("Recommend packages", true,
            context -> setup.recommendPackages(SupportOverrides.none(), false, context.cancellation()),
            recommendation -> {
                setBusy(false);
                view.showRecommendation(recommendation);
            }, error -> {
                setBusy(false);
                view.showFailure("Recommend packages", error);
            }).addListener(this::releaseIfCancelled);
    }

    /** Download and install the latest portable release with {@code packages}, then select it. */
    public void install(Set<DistributionPackage> packages) {
        final SetupService setup = services.setup();
        if (busy || setup == null) {
            return;
        }
        final Set<DistributionPackage> selection = packages == null || packages.isEmpty()
            ? EnumSet.noneOf(DistributionPackage.class) : EnumSet.copyOf(packages);
        try {
            DistributionPackage.requireValidSelection(selection);
        } catch (IllegalArgumentException e) {
            view.showNotice(selection.isEmpty() ? NO_PACKAGES : e.getMessage());
            return;
        }
        setBusy(true);
        Job job = jobs.submit("Install portable Ollama", true, context -> {
            OllamaInstallation installed = setup.installLatest(selection, (TransferProgress progress) -> {
                if (progress.totalBytes() > 0) {
                    context.progress(progress.phase(), progress.fraction());
                } else {
                    context.phase(progress.phase());
                }
            }, context.cancellation());
            context.phase("Selecting " + installed.version());
            services.configuration().update(current -> current.withSelectedInstallation(installed.kind(),
                installed.binaryDirectory()));
            return installed;
        }, installed -> {
            setBusy(false);
            view.showNotice(INSTALLED + " Version " + installed.version() + ".");
            detect();
        }, error -> {
            setBusy(false);
            view.showFailure("Install portable Ollama", error);
            detect();
        });
        job.addListener(current -> {
            if (current.state() == JobState.CANCELLED && busy) {
                setBusy(false);
                view.showNotice(INSTALL_CANCELLED);
                detect();
            }
        });
        view.trackJob(job);
    }

    /** Remove a portable version after confirmation; refused while the owned server uses it. */
    public void uninstall(OllamaInstallation installation) {
        final SetupService setup = services.setup();
        if (busy || setup == null || installation == null || installation.kind() != InstallationKind.MANAGED_PORTABLE
            || !view.confirmUninstall(installation)) {
            return;
        }
        run("Remove portable version", false, context -> {
            setup.uninstall(installation);
            return null;
        }, UNINSTALLED);
    }

    /** Start the selected installation as the manager's own server with the stored settings. */
    public void startSelected() {
        final ServerLifecycleService lifecycle = services.serverLifecycle();
        final OllamaInstallation selected = detection == null ? null : detection.selected();
        if (busy || lifecycle == null || selected == null) {
            return;
        }
        ServerStatus now = lifecycle.status();
        if (now.ownership() == RuntimeOwnership.OWNED && now.state() == ServerState.RUNNING) {
            view.showNotice(ALREADY_RUNNING);
            return;
        }
        if (detection.isServerRunning()) {
            // Never start a second server on a port that another Ollama already serves.
            view.showNotice(String.format(PORT_TAKEN, detection.endpoint().toHostValue()));
            return;
        }
        if (!view.confirmStart(selected, detection.endpoint().toHostValue())) {
            return;
        }
        setBusy(true);
        Job job = jobs.submit("Start server", true, context -> {
            ServerSettings settings = services.configuration().current().desiredServerSettings();
            return lifecycle.start(selected, settings, context.cancellation());
        }, status -> {
            setBusy(false);
            view.showNotice(status.state() == ServerState.RUNNING ? STARTED : status.message());
            detect();
        }, error -> {
            setBusy(false);
            showFailure("Start server", error);
            detect();
        });
        job.addListener(current -> {
            if (current.state() == JobState.CANCELLED && busy) {
                setBusy(false);
                view.showNotice(START_CANCELLED);
                detect();
            }
        });
        view.trackJob(job);
    }

    boolean isBusy() {
        return busy;
    }

    SetupDetection detection() {
        return detection;
    }

    private void run(String action, boolean cancellable, JobWork<Void> work,
                     String successNotice) {
        setBusy(true);
        jobs.submit(action, cancellable, work, ignored -> {
            setBusy(false);
            view.showNotice(successNotice);
            detect();
        }, error -> {
            setBusy(false);
            showFailure(action, error);
            detect();
        }).addListener(this::releaseIfCancelled);
    }

    /** A concurrent lifecycle operation is refused immediately by the application layer; say so plainly. */
    private void showFailure(String action, Exception error) {
        if (error instanceof OperationException && ((OperationException) error).kind() == ErrorKind.INVALID_STATE) {
            view.showNotice(action.startsWith("Remove") ? IN_USE : LIFECYCLE_BUSY);
            return;
        }
        view.showFailure(action, error);
    }

    private void releaseIfCancelled(Job job) {
        if (job.state() == JobState.CANCELLED && busy) {
            setBusy(false);
        }
    }

    private void setBusy(boolean value) {
        busy = value;
        view.setBusy(value);
    }
}
