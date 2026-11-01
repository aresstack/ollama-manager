package com.aresstack.ollamamanager.swing.section;

import java.util.List;

import com.aresstack.ollamamanager.application.common.TransferProgress;
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

/**
 * Model inventory and model operations through {@link ModelService}. Requests target only the server the
 * lifecycle service currently reports as running; there is no fallback to a configured or default address.
 * Pull, load, unload and delete work on any running server, including an existing installation that the
 * manager did not start: they are ordinary API requests and never stop or restart that server.
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
            view.showUnavailable("No running Ollama server. Start one in Setup.");
            return null;
        }
        return status.installation().endpoint();
    }

    public void refresh() {
        // Invalidate any pending response first, also when no server is available any more.
        final long generation = ++refreshGeneration;
        final Endpoint endpoint = runningEndpoint();
        if (endpoint == null) {
            adoptRunningServer(generation);
            return;
        }
        view.setMutationsAllowed(true, null);
        jobs.submit("Load models", true, context -> services.models().inventory(endpoint, context.cancellation()),
            entries -> {
                if (generation == refreshGeneration) {
                    view.showModels(entries);
                    loadCapabilities(generation, endpoint, entries);
                    view.showSource("from " + endpoint + ", read at "
                        + new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date()));
                }
            }, error -> {
                if (generation == refreshGeneration) {
                    // Never keep showing an old list: a failed read must not look like "0 running".
                    view.showUnavailable("Could not read models from " + endpoint + ": " + error.getMessage());
                }
            });
    }

    /** Use a server that answers on the configured address without a claim step, then load its models. */
    private void adoptRunningServer(long generation) {
        ServerLifecycleService lifecycle = services.serverLifecycle();
        if (services.setup() == null || services.models() == null || lifecycle == null
            || lifecycle.status().ownership() != RuntimeOwnership.NONE) {
            return;
        }
        jobs.submit("Check server", true, context -> services.setup().synchronize(
            services.configuration().current().desiredServerSettings().bind(), context.cancellation()), status -> {
                if (generation == refreshGeneration && status.state() == ServerState.RUNNING) {
                    refresh();
                }
            }, error -> {
                // Nothing usable answers; the view already says that no server runs.
            });
    }

    private void loadCapabilities(final long generation, final Endpoint endpoint, List<ModelEntry> entries) {
        final List<String> names = new java.util.ArrayList<String>();
        for (ModelEntry entry : entries) {
            if (!names.contains(entry.identity().name())) {
                names.add(entry.identity().name());
            }
        }
        if (names.isEmpty()) {
            return;
        }
        jobs.submit("Read model capabilities", true,
            context -> services.models().capabilities(endpoint, names, context.cancellation()),
            capabilities -> {
                if (generation == refreshGeneration) {
                    view.showCapabilities(capabilities);
                }
            }, error -> {
                // Capabilities are decoration; the list itself stays valid.
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
