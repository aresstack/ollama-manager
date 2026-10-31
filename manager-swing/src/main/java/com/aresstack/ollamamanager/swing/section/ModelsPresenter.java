package com.aresstack.ollamamanager.swing.section;

import com.aresstack.ollamamanager.application.common.TransferProgress;
import com.aresstack.ollamamanager.application.model.ModelService;
import com.aresstack.ollamamanager.application.server.ServerLifecycleService;
import com.aresstack.ollamamanager.domain.server.Endpoint;
import com.aresstack.ollamamanager.domain.server.ServerState;
import com.aresstack.ollamamanager.domain.server.ServerStatus;
import com.aresstack.ollamamanager.swing.job.Job;
import com.aresstack.ollamamanager.swing.job.JobManager;
import com.aresstack.ollamamanager.swing.job.JobWork;
import com.aresstack.ollamamanager.swing.shell.ConsoleServices;

/**
 * Model inventory and model operations through {@link ModelService}. Operations target only the server the
 * lifecycle service currently reports as running; there is no fallback to a configured or default address.
 */
public final class ModelsPresenter {

    /** Keep-alive for an explicit preload; the server's own default applies otherwise. */
    static final String PRELOAD_KEEP_ALIVE = "30m";

    private final ConsoleServices services;
    private final JobManager jobs;
    private ModelsView view;
    private boolean busy;
    /** Incremented per refresh so that a slower, older inventory response cannot overwrite a newer one. */
    private long refreshGeneration;

    public ModelsPresenter(ConsoleServices services, JobManager jobs) {
        this.services = services;
        this.jobs = jobs;
    }

    public void bind(ModelsView boundView) {
        this.view = boundView;
    }

    /** Endpoint of the running server, or {@code null} with the reason shown in the view. */
    Endpoint runningEndpoint() {
        ModelService models = services.models();
        ServerLifecycleService lifecycle = services.serverLifecycle();
        if (models == null || lifecycle == null) {
            view.showUnavailable("Model management is not connected in this build.");
            return null;
        }
        ServerStatus status = lifecycle.status();
        if (status.state() != ServerState.RUNNING || status.installation() == null) {
            view.showUnavailable("No running Ollama server. Start or attach a server first.");
            return null;
        }
        return status.installation().endpoint();
    }

    public void refresh() {
        final Endpoint endpoint = runningEndpoint();
        if (endpoint == null) {
            return;
        }
        final long generation = ++refreshGeneration;
        jobs.submit("Load models", true, context -> services.models().inventory(endpoint, context.cancellation()),
            entries -> {
                if (generation == refreshGeneration) {
                    view.showModels(entries);
                }
            }, error -> {
                if (generation == refreshGeneration) {
                    view.showFailure("Load models", error);
                }
            });
    }

    public void pull(String modelName) {
        final String name = modelName == null ? "" : modelName.trim();
        if (name.isEmpty()) {
            return;
        }
        final Endpoint endpoint = runningEndpoint();
        if (endpoint != null) {
            mutate("Pull " + name, true, context -> {
                services.models().pull(endpoint, name, (TransferProgress progress) -> {
                    if (progress.totalBytes() > 0) {
                        context.progress(progress.phase(), progress.fraction());
                    } else {
                        context.phase(progress.phase());
                    }
                }, context.cancellation());
                return null;
            });
        }
    }

    public void load(String modelName) {
        final Endpoint endpoint = runningEndpoint();
        if (endpoint != null) {
            mutate("Load " + modelName, true, context -> services.models().preloadAndVerify(endpoint, modelName,
                null, PRELOAD_KEEP_ALIVE, context.cancellation()));
        }
    }

    public void unload(String modelName) {
        final Endpoint endpoint = runningEndpoint();
        if (endpoint != null) {
            mutate("Unload " + modelName, false, context -> {
                services.models().unload(endpoint, modelName, context.cancellation());
                return null;
            });
        }
    }

    /** Delete after the user typed the model name; the service checks the confirmation again. */
    public void delete(String modelName) {
        final Endpoint endpoint = runningEndpoint();
        if (endpoint == null || busy) {
            return;
        }
        final String confirmed = view.confirmDelete(modelName);
        if (confirmed == null) {
            return;
        }
        mutate("Delete " + modelName, false, context -> {
            services.models().delete(endpoint, modelName, confirmed, context.cancellation());
            return null;
        });
    }

    boolean isBusy() {
        return busy;
    }

    /** Run one mutating operation at a time and reload the inventory afterwards, whatever the outcome. */
    private <T> void mutate(String title, boolean cancellable, JobWork<T> work) {
        if (busy) {
            return;
        }
        busy = true;
        view.setActionsEnabled(false);
        Job job = jobs.submit(title, cancellable, work, result -> finish(), error -> {
            view.showFailure(title, error);
            finish();
        });
        job.addListener(current -> {
            if (current.state() == com.aresstack.ollamamanager.swing.job.JobState.CANCELLED) {
                finish();
            }
        });
        view.trackJob(job);
    }

    private void finish() {
        if (!busy) {
            return;
        }
        busy = false;
        view.setActionsEnabled(true);
        refresh();
    }
}
