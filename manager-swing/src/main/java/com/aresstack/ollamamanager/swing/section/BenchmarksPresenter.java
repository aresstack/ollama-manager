package com.aresstack.ollamamanager.swing.section;

import com.aresstack.ollamamanager.application.benchmark.BenchmarkProfiles;
import com.aresstack.ollamamanager.application.benchmark.BenchmarkReport;
import com.aresstack.ollamamanager.application.benchmark.ConcurrencyBenchmarkRequest;
import com.aresstack.ollamamanager.application.benchmark.ConcurrencyBenchmarkService;
import com.aresstack.ollamamanager.application.benchmark.ParallelismSweepService;
import com.aresstack.ollamamanager.application.benchmark.StageResult;
import com.aresstack.ollamamanager.application.common.CancellationToken;
import com.aresstack.ollamamanager.application.common.OperationCancelledException;
import com.aresstack.ollamamanager.application.common.OperationException;
import com.aresstack.ollamamanager.application.configuration.ExistingSettingsImport;
import com.aresstack.ollamamanager.application.model.ModelEntry;
import com.aresstack.ollamamanager.application.server.ServerLifecycleService;
import com.aresstack.ollamamanager.application.server.ServerParallelismService;
import com.aresstack.ollamamanager.domain.server.Endpoint;
import com.aresstack.ollamamanager.domain.server.RuntimeOwnership;
import com.aresstack.ollamamanager.domain.server.ServerSettings;
import com.aresstack.ollamamanager.domain.server.ServerState;
import com.aresstack.ollamamanager.domain.server.ServerStatus;
import com.aresstack.ollamamanager.swing.job.Job;
import com.aresstack.ollamamanager.swing.job.JobManager;
import com.aresstack.ollamamanager.swing.job.JobState;
import com.aresstack.ollamamanager.swing.job.JobWork;
import com.aresstack.ollamamanager.swing.shell.ConsoleServices;

import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs the concurrency benchmark against the running server (owned or external), the sweep over server values
 * on an owned server, and keeps the history. Saving an optimum as a profile goes through the application layer.
 */
public final class BenchmarksPresenter {

    /** Marker for an owned server that runs without OLLAMA_NUM_PARALLEL set. */
    static final String SERVER_DEFAULT = ConcurrencyBenchmarkService.SERVER_DEFAULT;
    static final String SWEEP_NEEDS_OWNED =
        "Trying several server values restarts the server per value, which only works for a server started by the manager.";

    private final ConsoleServices services;
    private final JobManager jobs;
    private BenchmarksView view;
    private boolean running;
    /**
     * OLLAMA_NUM_PARALLEL of an external server as last read from its environment, so that it is still known
     * after a run; {@code null} until read. Set on the UI thread only.
     */
    private String externalParallel;

    public BenchmarksPresenter(ConsoleServices services, JobManager jobs) {
        this.services = services;
        this.jobs = jobs;
    }

    public void bind(BenchmarksView boundView) {
        this.view = boundView;
    }

    Endpoint runningEndpoint() {
        ServerLifecycleService lifecycle = services.serverLifecycle();
        if (services.benchmarks() == null || services.models() == null || lifecycle == null) {
            view.showUnavailable("Benchmarks are not connected in this build.");
            return null;
        }
        ServerStatus status = lifecycle.status();
        if (status.state() != ServerState.RUNNING || status.installation() == null) {
            view.showUnavailable("No running Ollama server. Start or attach a server first.");
            return null;
        }
        return status.installation().endpoint();
    }

    /**
     * Reload the model list, the server's parallel setting and the stored runs. While a benchmark runs only the
     * history is reloaded: a sweep restarts the server, and a refresh during that would report it as missing.
     */
    public void refresh() {
        loadHistory();
        if (running) {
            return;
        }
        final Endpoint endpoint = runningEndpoint();
        if (endpoint == null) {
            return;
        }
        view.showServerParallel(serverParallelText());
        ServerStatus status = services.serverLifecycle().status();
        boolean owned = status.ownership() == RuntimeOwnership.OWNED;
        view.setSweepAvailable(owned && services.sweeps() != null, owned ? null : SWEEP_NEEDS_OWNED);
        ServerParallelismService parallelism = services.serverParallelism();
        view.setServerRestartAvailable(parallelism != null && parallelism.canApply(status), !owned);
        if (!owned && services.existingSettings() != null) {
            // An external server's OLLAMA_* come from the system environment it was started in.
            jobs.submit("Read the server's environment", false,
                context -> externalSettings(context.cancellation()), settings -> {
                    externalParallel = ConcurrencyBenchmarkService.serverParallelText(settings);
                    view.showServerParallel(externalParallel);
                }, null);
        }
        jobs.submit("Load models for benchmark", true,
            context -> services.models().inventory(endpoint, context.cancellation()), entries -> {
                List<String> names = new ArrayList<String>();
                for (ModelEntry entry : entries) {
                    if (entry.isInstalled()) {
                        names.add(entry.identity().name());
                    }
                }
                view.showModels(names);
            }, error -> view.showFailure("Load models", error));
    }

    private void loadHistory() {
        if (services.benchmarkHistory() == null) {
            return;
        }
        jobs.submit("Load benchmark history", false, context -> services.benchmarkHistory().list(),
            view::showHistory, error -> view.showFailure("Load benchmark history", error));
    }

    /**
     * The settings the owned server actually runs with, or {@code null} for an external server whose environment
     * the manager cannot read. Pending desired settings are never reported.
     */
    ServerSettings appliedSettings() {
        ServerLifecycleService lifecycle = services.serverLifecycle();
        if (lifecycle == null || lifecycle.status().ownership() != RuntimeOwnership.OWNED) {
            return null;
        }
        return lifecycle.appliedSettings();
    }

    /**
     * The OLLAMA_* settings an external server was started with, as far as the system environment (Windows user
     * and machine variables, or the process environment) tells; unset variables mean Ollama's defaults. Returns
     * {@code null} when the environment cannot be read. Blocks, so it runs inside a job.
     */
    ServerSettings externalSettings(CancellationToken cancellation) throws OperationCancelledException {
        ExistingSettingsImport existing = services.existingSettings();
        if (existing == null) {
            return null;
        }
        try {
            return existing.detect(cancellation).settings();
        } catch (OperationCancelledException e) {
            throw e;
        } catch (OperationException e) {
            return null;
        }
    }

    /**
     * OLLAMA_NUM_PARALLEL of the running server: a number, {@link #SERVER_DEFAULT} when it runs unset, or
     * {@code null} when unknown. An owned server reports what it was started with; an external server the value
     * last read from its environment.
     */
    String serverParallelText() {
        ServerLifecycleService lifecycle = services.serverLifecycle();
        if (lifecycle != null && lifecycle.status().ownership() == RuntimeOwnership.OWNED) {
            return ConcurrencyBenchmarkService.serverParallelText(lifecycle.appliedSettings());
        }
        return externalParallel;
    }

    /**
     * Benchmark the running server as it is.
     *
     * @return whether a run was started
     */
    public boolean run(final ConcurrencyBenchmarkRequest request) {
        if (running) {
            return false;
        }
        final Endpoint endpoint = runningEndpoint();
        if (endpoint == null) {
            return false;
        }
        final ConcurrencyBenchmarkService service = services.benchmarks();
        final ServerSettings owned = appliedSettings();
        start("Benchmark " + request.model(), "Benchmark", true, null, context -> {
            ServerSettings applied = owned;
            if (applied == null) {
                context.progress("Reading the server's environment", 0);
                applied = externalSettings(context.cancellation());
            }
            return service.run(endpoint, request, applied, new ConcurrencyBenchmarkService.Listener() {
                @Override
                public void phase(String text, double fraction) {
                    context.progress(text, fraction);
                }

                @Override
                public void stageFinished(StageResult stage) {
                    SwingUtilities.invokeLater(() -> view.stageFinished(stage));
                }

                @Override
                public void workload(boolean embedding) {
                    SwingUtilities.invokeLater(() -> view.showWorkload(embedding));
                }
            }, context.cancellation());
        }, report -> view.showReport(report));
        return true;
    }

    /**
     * Store {@code numParallel} as OLLAMA_NUM_PARALLEL and restart the running server with it, so that a benchmark
     * can measure that many parallel chats. The view asked the user already; an external server is taken over.
     *
     * @return whether the restart was started
     */
    public boolean applyServerParallel(final int numParallel) {
        if (running || runningEndpoint() == null) {
            return false;
        }
        final ServerParallelismService parallelism = services.serverParallelism();
        if (parallelism == null || !parallelism.canApply(services.serverLifecycle().status())) {
            view.setServerRestartAvailable(false, false);
            return false;
        }
        // After a failed restart the server may be gone; a refresh shows what is left.
        start("Restart server with OLLAMA_NUM_PARALLEL=" + numParallel, "Restart server", false, this::refresh,
            context -> parallelism.apply(numParallel, context.cancellation()), status -> {
                view.showServerRestarted(numParallel);
                // Ownership and the sweep's availability may have changed with the restart.
                refresh();
            });
        return true;
    }

    /**
     * Benchmark once per OLLAMA_NUM_PARALLEL candidate, restarting the owned server for each.
     *
     * @return whether a sweep was started
     */
    public boolean runSweep(final ConcurrencyBenchmarkRequest request, final List<Integer> candidates) {
        if (running || runningEndpoint() == null) {
            return false;
        }
        final ParallelismSweepService sweeps = services.sweeps();
        if (sweeps == null || services.serverLifecycle().status().ownership() != RuntimeOwnership.OWNED) {
            view.setSweepAvailable(false, SWEEP_NEEDS_OWNED);
            return false;
        }
        start("Benchmark " + request.model() + " over server values", "Benchmark", true, null,
            context -> sweeps.run(request, candidates,
            new ParallelismSweepService.Listener() {
                @Override
                public void phase(String text, double fraction) {
                    context.progress(text, fraction);
                }

                @Override
                public void stageFinished(StageResult stage) {
                    SwingUtilities.invokeLater(() -> view.stageFinished(stage));
                }

                @Override
                public void workload(boolean embedding) {
                    SwingUtilities.invokeLater(() -> view.showWorkload(embedding));
                }

                @Override
                public void reportFinished(BenchmarkReport report) {
                    SwingUtilities.invokeLater(() -> view.reportFinished(report));
                }

                @Override
                public void failedAfterCancel(OperationException failure) {
                    SwingUtilities.invokeLater(() -> view.showFailure("Benchmark", failure));
                }
            }, context.cancellation()), reports -> view.showReports(reports));
        return true;
    }

    /**
     * Run {@code work} as the one job of this section: the form is locked meanwhile and a refresh touches only the
     * history. {@code action} names the work in a failure message; {@code onFailure} runs before it is shown.
     */
    private <T> void start(String title, final String action, boolean cancellable, final Runnable onFailure,
                           JobWork<T> work, java.util.function.Consumer<T> onResult) {
        running = true;
        view.setRunning(true);
        Job job = jobs.submit(title, cancellable, work, result -> {
            finish();
            onResult.accept(result);
            loadHistory();
        }, error -> {
            finish();
            view.runEnded(true);
            // First the state, then the failure, so that the failure stays on the banner.
            if (onFailure != null) {
                onFailure.run();
            }
            view.showFailure(action, error);
            // Reports finished before the failure are stored already.
            loadHistory();
        });
        job.addListener(current -> {
            if (current.state() == JobState.CANCELLED) {
                finish();
                view.runEnded(false);
                loadHistory();
            }
        });
        view.trackJob(job);
    }

    public void delete(BenchmarkReport report) {
        if (report == null || services.benchmarkHistory() == null) {
            return;
        }
        jobs.submit("Delete benchmark", false, context -> {
            services.benchmarkHistory().delete(report.id());
            return null;
        }, ignored -> loadHistory(), error -> view.showFailure("Delete benchmark", error));
    }

    /** Save {@code stage} of {@code report} as a model profile named {@code name} (empty for the default). */
    public void saveProfile(final BenchmarkReport report, final StageResult stage, final String name) {
        final BenchmarkProfiles profiles = services.benchmarkProfiles();
        if (report == null || stage == null || profiles == null) {
            return;
        }
        jobs.submit("Save benchmark profile", true,
            context -> profiles.save(report, stage, name, context.cancellation()),
            view::showProfileSaved, error -> view.showFailure("Save profile", error));
    }

    boolean isRunning() {
        return running;
    }

    private void finish() {
        if (running) {
            running = false;
            view.setRunning(false);
            // The sweep restored the server's settings; show what runs now.
            if (services.serverLifecycle() != null) {
                view.showServerParallel(serverParallelText());
            }
        }
    }
}
