package com.aresstack.ollamamanager.swing.section;

import com.aresstack.ollamamanager.application.common.ErrorKind;
import com.aresstack.ollamamanager.application.common.OperationCancelledException;
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
import com.aresstack.ollamamanager.swing.job.JobContext;
import com.aresstack.ollamamanager.swing.job.JobManager;
import com.aresstack.ollamamanager.swing.shell.ConsoleServices;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Setup assistant: detect a running server and installations on disk, then either use the running server
 * as it is, select an installation, or install a portable version with progress and Cancel. Every step goes
 * through {@link SetupService}; the shared lifecycle lock in the application layer refuses an uninstall while a
 * start or stop runs, which shows up here as INVALID_STATE.
 */
public final class SetupPresenter {

    static final String NOT_WIRED = "Setup is not connected in this build.";
    static final String ATTACHED = "Using the running server. Models can be pulled, loaded and deleted on it; the"
        + " manager never stops or restarts it. To use your own server settings, start an installation as the"
        + " manager's server on another port.";
    static final String SELECTED = "Installation selected. Start it here or from the dashboard.";
    static final String INSTALLED = "Portable version installed and selected.";
    static final String INSTALL_CANCELLED = "Installation cancelled. Nothing was selected.";
    static final String UNINSTALLED = "Portable version removed.";
    static final String STARTED = "Server started by the manager.";
    static final String START_CANCELLED = "Start cancelled. Check the dashboard for the server state.";
    static final String LIFECYCLE_BUSY = "Another server operation is running. Try again when it has finished.";
    static final String NOT_REMOVED = "Not removed: ";
    static final String DETACHED = "No longer using the external server. It keeps running unchanged.";
    static final String DETACH_FIRST = "The manager still uses an external server. Choose \"Stop using it\" first;"
        + " the external server itself is not touched.";
    static final String PORT_TAKEN = "A server already answers on %s. Use it, or change the network address in the"
        + " server settings to start the manager's own server on another port.";
    static final String ALREADY_RUNNING = "The manager's own server is already running. Stop it on the dashboard"
        + " before starting another installation.";
    static final String SELECT_FIRST = "Select an installation first.";
    static final String PORT_UNUSABLE = "%s cannot be used: %s. Change Bind in the server settings to another"
        + " port.";
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
        if (busy) {
            // Never replace the progress and Cancel of a running install, start or removal.
            return;
        }
        final long generation = ++detectGeneration;
        final SetupService setup = services.setup();
        if (setup == null) {
            view.showNotice(NOT_WIRED);
            return;
        }
        Job job = jobs.submit("Detect Ollama", true, context -> collect(setup, context), found -> {
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

    /** Use the server found on the endpoint; it is attached as external and never stopped or restarted. */
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

    /** Forget the attached external server; the server process itself is never touched. */
    public void stopUsingExternal() {
        ServerLifecycleService lifecycle = services.serverLifecycle();
        if (busy || lifecycle == null || lifecycle.status().ownership() != RuntimeOwnership.EXTERNAL) {
            return;
        }
        lifecycle.detachExternal();
        view.showNotice(DETACHED);
        detect();
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
        Job job = jobs.submit("Recommend packages", true,
            context -> setup.recommendPackages(SupportOverrides.none(), false, context.cancellation()),
            recommendation -> {
                setBusy(false);
                view.showRecommendation(recommendation);
            }, error -> {
                setBusy(false);
                view.showFailure("Recommend packages", error);
            });
        job.addListener(this::releaseIfCancelled);
        view.trackJob(job);
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
        final SetupService setup = services.setup();
        if (busy || lifecycle == null || setup == null) {
            return;
        }
        setBusy(true);
        // Decide on a fresh snapshot of selection, settings and endpoint, and start exactly what was confirmed.
        Job check = jobs.submit("Check before start", true, context -> collect(setup, context), fresh -> {
            ++detectGeneration;
            detection = fresh;
            view.showDetection(fresh);
            String refusal = startRefusal(fresh, lifecycle.status());
            if (refusal != null) {
                setBusy(false);
                view.showNotice(refusal);
                return;
            }
            if (!view.confirmStart(fresh.selected(), fresh.endpoint().toHostValue())) {
                setBusy(false);
                return;
            }
            start(lifecycle, fresh.selected(), fresh.settings());
        }, error -> {
            setBusy(false);
            view.showFailure("Check before start", error);
        });
        check.addListener(this::releaseIfCancelled);
        view.trackJob(check);
    }

    private static String startRefusal(SetupDetection fresh, ServerStatus now) {
        if (fresh.selected() == null) {
            return SELECT_FIRST;
        }
        if (now.ownership() == RuntimeOwnership.OWNED && now.state() == ServerState.RUNNING) {
            return ALREADY_RUNNING;
        }
        if (now.ownership() == RuntimeOwnership.EXTERNAL) {
            return DETACH_FIRST;
        }
        if (fresh.isServerRunning()) {
            // Never start a second server on a port that another Ollama already serves.
            return String.format(PORT_TAKEN, fresh.endpoint().toHostValue());
        }
        if (fresh.probeFailure() != null) {
            return String.format(PORT_UNUSABLE, fresh.endpoint().toHostValue(), fresh.probeFailure());
        }
        return null;
    }

    private void start(ServerLifecycleService lifecycle, OllamaInstallation installation, ServerSettings settings) {
        Job job = jobs.submit("Start server", true,
            context -> lifecycle.start(installation, settings, context.cancellation()), status -> {
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

    /**
     * Read the configuration, probe the endpoint and list installations. A probe that fails for another reason
     * than "nothing answers" is recorded, never treated as a free port, and does not hide the installations.
     */
    private SetupDetection collect(SetupService setup, JobContext context) throws OperationException {
        ManagerConfiguration configuration = services.configuration().current();
        ServerSettings settings = configuration.desiredServerSettings();
        Endpoint endpoint = settings.bind();
        context.phase("Checking " + endpoint.toHostValue());
        String version = null;
        String probeFailure = null;
        try {
            version = setup.detectRunning(endpoint, context.cancellation());
        } catch (OperationCancelledException e) {
            throw e;
        } catch (OperationException e) {
            probeFailure = e.getMessage();
        }
        context.phase("Searching installations");
        List<File> extra = new ArrayList<File>();
        if (configuration.selectedInstallationKind() == InstallationKind.MANAGED_INSTALLED
            && configuration.selectedBinaryDirectory() != null) {
            // A stored installation outside the default locations must not look missing.
            extra.add(configuration.selectedBinaryDirectory());
        }
        List<OllamaInstallation> discovered = setup.detectExisting(extra, context.cancellation());
        PortableInstallationService installations = services.installations();
        List<OllamaInstallation> portable = installations == null
            ? Collections.<OllamaInstallation>emptyList() : installations.installedVersions();
        ServerLifecycleService lifecycle = services.serverLifecycle();
        boolean attached = lifecycle != null && lifecycle.status().ownership() == RuntimeOwnership.EXTERNAL;
        return new SetupDetection(endpoint, settings, version, probeFailure, attached, discovered, portable,
            configuration.selectedInstallationKind(), configuration.selectedBinaryDirectory());
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
            // A removal can also be refused for the active version; keep the service's own reason.
            view.showNotice(action.startsWith("Remove") ? NOT_REMOVED + error.getMessage() : LIFECYCLE_BUSY);
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
