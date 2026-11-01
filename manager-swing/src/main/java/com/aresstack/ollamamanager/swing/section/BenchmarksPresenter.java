package com.aresstack.ollamamanager.swing.section;

import com.aresstack.ollamamanager.application.benchmark.BenchmarkReport;
import com.aresstack.ollamamanager.application.benchmark.ConcurrencyBenchmarkRequest;
import com.aresstack.ollamamanager.application.benchmark.ConcurrencyBenchmarkService;
import com.aresstack.ollamamanager.application.benchmark.StageResult;
import com.aresstack.ollamamanager.application.model.ModelEntry;
import com.aresstack.ollamamanager.application.server.ServerLifecycleService;
import com.aresstack.ollamamanager.domain.server.Endpoint;
import com.aresstack.ollamamanager.domain.server.RuntimeOwnership;
import com.aresstack.ollamamanager.domain.server.ServerSettings;
import com.aresstack.ollamamanager.domain.server.ServerState;
import com.aresstack.ollamamanager.domain.server.ServerStatus;
import com.aresstack.ollamamanager.swing.job.Job;
import com.aresstack.ollamamanager.swing.job.JobState;
import com.aresstack.ollamamanager.swing.job.JobManager;
import com.aresstack.ollamamanager.swing.shell.ConsoleServices;

import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs the concurrency benchmark against the running server (owned or external) and keeps the history.
 */
public final class BenchmarksPresenter {

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

    /** Reload the model list, the server's parallel setting and the stored runs. */
    public void refresh() {
        loadHistory();
        final Endpoint endpoint = runningEndpoint();
        if (endpoint == null) {
            return;
        }
        view.showServerParallel(serverParallelText());
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

    /** Marker for an owned server that runs without OLLAMA_NUM_PARALLEL set. */
    static final String SERVER_DEFAULT = "default";

    /**
     * OLLAMA_NUM_PARALLEL as applied to the running server: the value of the owned server's actual settings,
     * {@link #SERVER_DEFAULT} when it runs unset, or {@code null} for an external server whose environment the
     * manager cannot read. Pending desired settings are never reported.
     */
    String serverParallelText() {
        ServerLifecycleService lifecycle = services.serverLifecycle();
        if (lifecycle == null || lifecycle.status().ownership() != RuntimeOwnership.OWNED) {
            return null;
        }
        ServerSettings applied = lifecycle.appliedSettings();
        if (applied == null) {
            return null;
        }
        Integer parallel = applied.parallelism();
        return parallel == null ? SERVER_DEFAULT : String.valueOf(parallel);
    }

    public void run(final ConcurrencyBenchmarkRequest request) {
        if (running) {
            return;
        }
        final Endpoint endpoint = runningEndpoint();
        if (endpoint == null) {
            return;
        }
        final ConcurrencyBenchmarkService service = services.benchmarks();
        final String serverParallel = serverParallelText();
        running = true;
        view.setRunning(true);
        Job job = jobs.submit("Benchmark " + request.model(), true, context -> service.run(endpoint, request,
            serverParallel, new ConcurrencyBenchmarkService.Listener() {
                @Override
                public void phase(String text, double fraction) {
                    context.progress(text, fraction);
                }

                @Override
                public void stageFinished(StageResult stage) {
                    SwingUtilities.invokeLater(() -> view.stageFinished(stage));
                }
            }, context.cancellation()), report -> {
                finish();
                view.showReport(report);
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

    boolean isRunning() {
        return running;
    }

    private void finish() {
        if (running) {
            running = false;
            view.setRunning(false);
        }
    }

}
