package com.aresstack.ollamamanager.swing.section;

import com.aresstack.ollamamanager.application.model.ModelEntry;
import com.aresstack.ollamamanager.application.model.ModelService;
import com.aresstack.ollamamanager.application.server.ServerLifecycleService;
import com.aresstack.ollamamanager.domain.server.Endpoint;
import com.aresstack.ollamamanager.domain.server.RuntimeOwnership;
import com.aresstack.ollamamanager.domain.server.ServerState;
import com.aresstack.ollamamanager.domain.server.ServerStatus;
import com.aresstack.ollamamanager.swing.job.Job;
import com.aresstack.ollamamanager.swing.job.JobManager;
import com.aresstack.ollamamanager.swing.job.JobWork;
import com.aresstack.ollamamanager.swing.shell.ConsoleServices;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Loads dashboard data through the application services and runs the stop and restart use cases.
 */
public final class DashboardPresenter {

    private final ConsoleServices services;
    private final JobManager jobs;
    private DashboardView view;
    private boolean busy;
    private boolean unloading;
    private long modelsGeneration;

    public DashboardPresenter(ConsoleServices services, JobManager jobs) {
        this.services = services;
        this.jobs = jobs;
    }

    public void bind(DashboardView boundView) {
        this.view = boundView;
    }

    /** Reload everything; each part loads independently so one failure does not hide the rest. */
    public void refresh() {
        jobs.submit("Load configuration", false, context -> services.configuration().current(),
            view::showConfiguration, error -> view.showFailure("Load configuration", error));

        ServerLifecycleService lifecycle = services.serverLifecycle();
        view.showServerStatus(lifecycle == null ? null : lifecycle.status());
        if (lifecycle != null && services.setup() != null
            && lifecycle.status().ownership() != RuntimeOwnership.OWNED) {
            // Use a server on the configured address automatically, and forget one that disappeared.
            jobs.submit("Check server", true, context -> services.setup().synchronize(
                services.configuration().current().desiredServerSettings().bind(), context.cancellation()),
                view::showServerStatus, error -> view.showServerStatus(lifecycle.status()));
        }

        if (services.hardwareProbe() == null) {
            view.showHardware(null);
        } else {
            view.trackJob(jobs.submit("Detect hardware", true,
                context -> services.hardwareProbe().probe(context.cancellation()),
                view::showHardware, error -> view.showFailure("Detect hardware", error)));
        }
        refreshGpuUsage();
        refreshRunningModels();
    }

    /** Re-read the server state, GPU usage and running models, for example whenever the dashboard becomes visible. */
    public void refreshStatus() {
        if (busy) {
            return;
        }
        ServerLifecycleService lifecycle = services.serverLifecycle();
        view.showServerStatus(lifecycle == null ? null : lifecycle.status());
        refreshGpuUsage();
        refreshRunningModels();
    }

    /** Read the loaded models of the running server; installed-only models are not the dashboard's business. */
    public void refreshRunningModels() {
        final long generation = ++modelsGeneration;
        ModelService models = services.models();
        ServerLifecycleService lifecycle = services.serverLifecycle();
        if (models == null || lifecycle == null) {
            view.showRunningModels(null, "Model management is not connected in this build.");
            return;
        }
        ServerStatus status = lifecycle.status();
        if (status.state() != ServerState.RUNNING || status.installation() == null) {
            view.showRunningModels(Collections.<ModelEntry>emptyList(), "No running Ollama server.");
            return;
        }
        final Endpoint endpoint = status.installation().endpoint();
        jobs.submit("Load running models", true, context -> models.inventory(endpoint, context.cancellation()),
            entries -> {
                if (generation != modelsGeneration) {
                    return;
                }
                List<ModelEntry> loaded = new ArrayList<ModelEntry>();
                List<String> names = new ArrayList<String>();
                for (ModelEntry entry : entries) {
                    if (entry.isLoaded()) {
                        loaded.add(entry);
                        names.add(entry.identity().name());
                    }
                }
                view.showRunningModels(loaded, null);
                if (!names.isEmpty()) {
                    jobs.submit("Read model capabilities", true,
                        context -> models.capabilities(endpoint, names, context.cancellation()),
                        capabilities -> {
                            if (generation == modelsGeneration) {
                                view.showRunningModelCapabilities(capabilities);
                            }
                        }, error -> {
                            // Capabilities are decoration; the list itself stays valid.
                        });
                }
            }, error -> {
                if (generation == modelsGeneration) {
                    // Never keep an old list: a failed read must not look like "nothing loaded".
                    view.showRunningModels(Collections.<ModelEntry>emptyList(),
                        "Could not read models from " + endpoint + ": " + error.getMessage());
                }
            });
    }

    /** Remove a model from memory on the running server, then re-read the list. */
    public void unloadRequested(final String modelName) {
        final ModelService models = services.models();
        ServerLifecycleService lifecycle = services.serverLifecycle();
        if (unloading || models == null || lifecycle == null) {
            return;
        }
        ServerStatus status = lifecycle.status();
        if (status.state() != ServerState.RUNNING || status.installation() == null) {
            return;
        }
        final Endpoint endpoint = status.installation().endpoint();
        unloading = true;
        view.setModelActionsEnabled(false);
        Job job = jobs.submit("Unload " + modelName, false, context -> {
            models.unload(endpoint, modelName, context.cancellation());
            return null;
        }, result -> {
            unloading = false;
            view.setModelActionsEnabled(true);
            refreshRunningModels();
            refreshGpuUsage();
        }, error -> {
            unloading = false;
            view.setModelActionsEnabled(true);
            view.showFailure("Unload " + modelName, error);
            refreshRunningModels();
        });
        view.trackJob(job);
    }

    /** Sample GPU usage via the telemetry port only. */
    public void refreshGpuUsage() {
        if (services.gpuTelemetry() == null) {
            view.showGpuUsage(null);
            return;
        }
        jobs.submit("Read GPU usage", true, context -> services.gpuTelemetry().sample(context.cancellation()),
            view::showGpuUsage, error -> view.showFailure("Read GPU usage", error));
    }

    /** Whether the Stop action may be offered for {@code status}. */
    public boolean canStop(ServerStatus status) {
        ServerLifecycleService lifecycle = services.serverLifecycle();
        return !busy && lifecycle != null && lifecycle.canStop(status);
    }

    /** Whether the Restart action may be offered: a running owned server, or a controllable external one. */
    public boolean canRestart(ServerStatus status) {
        ServerLifecycleService lifecycle = services.serverLifecycle();
        if (busy || lifecycle == null || status == null || status.state() != ServerState.RUNNING) {
            return false;
        }
        return status.ownership() == RuntimeOwnership.OWNED
            || status.ownership() == RuntimeOwnership.EXTERNAL && lifecycle.canControlExternal();
    }

    /** Stop the server after the user confirmed the warning. */
    public void stopRequested() {
        final ServerLifecycleService lifecycle = services.serverLifecycle();
        if (lifecycle == null) {
            return;
        }
        ServerStatus status = lifecycle.status();
        if (!canStop(status) || !view.confirmStop(status.ownership() == RuntimeOwnership.EXTERNAL)) {
            return;
        }
        run("Stop server", lifecycle, context -> lifecycle.stop());
    }

    /** Restart with the stored server settings after the user confirmed the warning. */
    public void restartRequested() {
        final ServerLifecycleService lifecycle = services.serverLifecycle();
        if (lifecycle == null) {
            return;
        }
        ServerStatus status = lifecycle.status();
        if (!canRestart(status) || !view.confirmRestart(status.ownership() == RuntimeOwnership.EXTERNAL)) {
            return;
        }
        final boolean external = status.ownership() == RuntimeOwnership.EXTERNAL;
        run("Restart server", lifecycle, context -> {
            if (external && services.existingSettings() != null) {
                // The replacement must keep what the installation is configured with, above all its model folder.
                services.existingSettings().importUnchosen(context.cancellation());
            }
            return lifecycle.restart(services.configuration().current().desiredServerSettings(),
                context.cancellation());
        });
    }

    private void run(String title, ServerLifecycleService lifecycle, JobWork<ServerStatus> work) {
        // Disable at once so a second click cannot queue a second transition while this one runs.
        busy = true;
        view.setStopEnabled(false);
        view.setRestartEnabled(false);
        Job job = jobs.submit(title, false, work, status -> {
            busy = false;
            view.showServerStatus(status);
            refreshRunningModels();
        }, error -> {
            busy = false;
            view.showFailure(title, error);
            view.showServerStatus(lifecycle.status());
        });
        view.trackJob(job);
    }
}
