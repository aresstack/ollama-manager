package com.aresstack.ollamamanager.swing.section;

import com.aresstack.ollamamanager.application.benchmark.BenchmarkProfiles;
import com.aresstack.ollamamanager.application.benchmark.BenchmarkReport;
import com.aresstack.ollamamanager.application.benchmark.ConcurrencyBenchmarkRequest;
import com.aresstack.ollamamanager.application.benchmark.ConcurrencyBenchmarkService;
import com.aresstack.ollamamanager.application.benchmark.ParallelismSweepService;
import com.aresstack.ollamamanager.application.benchmark.StageResult;
import com.aresstack.ollamamanager.application.common.OperationException;
import com.aresstack.ollamamanager.application.model.ModelEntry;
import com.aresstack.ollamamanager.application.server.ServerLifecycleService;
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
        boolean owned = services.serverLifecycle().status().ownership() == RuntimeOwnership.OWNED;
        view.setSweepAvailable(owned && services.sweeps() != null, owned ? null : SWEEP_NEEDS_OWNED);
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
     * OLLAMA_NUM_PARALLEL as applied to the running server: a number, {@link #SERVER_DEFAULT} when the owned
     * server runs unset, or {@code null} when unknown.
     */
    String serverParallelText() {
        return ConcurrencyBenchmarkService.serverParallelText(appliedSettings());
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
        final ServerSettings applied = appliedSettings();
        start("Benchmark " + request.model(), context -> service.run(endpoint, request, applied,
            new ConcurrencyBenchmarkService.Listener() {
                @Override
                public void phase(String text, double fraction) {
                    context.progress(text, fraction);
                }

                @Override
                public void stageFinished(StageResult stage) {
                    SwingUtilities.invokeLater(() -> view.stageFinished(stage));
                }
            }, context.cancellation()), report -> view.showReport(report));
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
        start("Benchmark " + request.model() + " over server values", context -> sweeps.run(request, candidates,
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
                public void reportFinished(BenchmarkReport report) {
                    SwingUtilities.invokeLater(() -> view.reportFinished(report));
                }

                @Override
                public void restoreFailed(OperationException failure) {
                    SwingUtilities.invokeLater(() -> view.showFailure("Restore server settings", failure));
                }
            }, context.cancellation()), reports -> view.showReports(reports));
        return true;
    }

    private <T> void start(String title, JobWork<T> work, java.util.function.Consumer<T> onResult) {
        running = true;
        view.setRunning(true);
        Job job = jobs.submit(title, true, work, result -> {
            finish();
            onResult.accept(result);
            loadHistory();
        }, error -> {
            finish();
            view.showFailure("Benchmark", error);
        });
        job.addListener(current -> {
            if (current.state() == JobState.CANCELLED) {
                finish();
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
