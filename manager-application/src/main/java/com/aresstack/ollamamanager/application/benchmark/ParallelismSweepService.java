package com.aresstack.ollamamanager.application.benchmark;

import com.aresstack.ollamamanager.application.common.CancellationToken;
import com.aresstack.ollamamanager.application.common.ErrorKind;
import com.aresstack.ollamamanager.application.common.OperationCancelledException;
import com.aresstack.ollamamanager.application.common.OperationException;
import com.aresstack.ollamamanager.application.server.ServerLifecycleService;
import com.aresstack.ollamamanager.domain.server.OllamaInstallation;
import com.aresstack.ollamamanager.domain.server.RuntimeOwnership;
import com.aresstack.ollamamanager.domain.server.ServerSettingCatalog;
import com.aresstack.ollamamanager.domain.server.ServerSettings;
import com.aresstack.ollamamanager.domain.server.ServerState;
import com.aresstack.ollamamanager.domain.server.ServerStatus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Run the concurrency benchmark once per {@code OLLAMA_NUM_PARALLEL} candidate. The owned server is restarted with
 * each candidate and, whatever happens, restarted with its original settings at the end. The stored desired
 * settings are never touched, so nothing in the configuration needs restoring.
 */
public final class ParallelismSweepService {

    /** Receives progress; called on the worker thread. */
    public interface Listener {
        void phase(String text, double fraction);

        void stageFinished(StageResult stage);

        void reportFinished(BenchmarkReport report);
    }

    private final ServerLifecycleService lifecycle;
    private final ConcurrencyBenchmarkService benchmarks;

    public ParallelismSweepService(ServerLifecycleService lifecycle, ConcurrencyBenchmarkService benchmarks) {
        if (lifecycle == null || benchmarks == null) {
            throw new IllegalArgumentException("lifecycle and benchmarks must not be null");
        }
        this.lifecycle = lifecycle;
        this.benchmarks = benchmarks;
    }

    /** Distinct candidates in ascending order; each between 1 and 256. */
    public static List<Integer> candidates(List<Integer> values) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("at least one OLLAMA_NUM_PARALLEL value is required");
        }
        List<Integer> sorted = new ArrayList<Integer>(new LinkedHashSet<Integer>(values));
        for (Integer value : sorted) {
            if (value == null || value < 1 || value > 256) {
                throw new IllegalArgumentException("OLLAMA_NUM_PARALLEL must be between 1 and 256: " + value);
            }
        }
        Collections.sort(sorted);
        return Collections.unmodifiableList(sorted);
    }

    /**
     * Run the sweep. Requires a running server owned by the manager. Returns the reports finished so far when
     * cancelled; throws {@link OperationCancelledException} only when none finished.
     *
     * @throws OperationException {@code NOT_OWNED} for an external server, {@code INVALID_STATE} without a running
     *                            server, or the failure of a restart or benchmark; the original settings are
     *                            restored before the exception propagates
     */
    public List<BenchmarkReport> run(ConcurrencyBenchmarkRequest request, List<Integer> rawCandidates,
                                     final Listener listener, final CancellationToken cancellation)
        throws OperationException {
        List<Integer> values = candidates(rawCandidates);
        ServerStatus status = lifecycle.status();
        if (status.state() != ServerState.RUNNING || status.installation() == null) {
            throw new OperationException(ErrorKind.INVALID_STATE, "no running server to benchmark");
        }
        if (status.ownership() != RuntimeOwnership.OWNED) {
            throw new OperationException(ErrorKind.NOT_OWNED,
                "server values can only be tried on a server started by the manager");
        }
        ServerSettings original = lifecycle.appliedSettings();
        if (original == null) {
            throw new OperationException(ErrorKind.INVALID_STATE, "the running server's settings are unknown");
        }
        OllamaInstallation installation = status.installation();
        final List<BenchmarkReport> reports = new ArrayList<BenchmarkReport>();
        final int perCandidate = request.levels().size() + 2;
        final int totalSteps = values.size() * perCandidate;
        OperationException failure = null;
        try {
            int index = 0;
            for (final int candidate : values) {
                if (cancellation.isCancelled()) {
                    break;
                }
                final int base = index * perCandidate;
                listener.phase("Restarting server with OLLAMA_NUM_PARALLEL=" + candidate, (double) base / totalSteps);
                ServerStatus restarted = lifecycle.restart(original.with(ServerSettingCatalog.PARALLELISM, candidate),
                    cancellation);
                if (restarted.installation() == null || restarted.state() != ServerState.RUNNING) {
                    throw new OperationException(ErrorKind.PROCESS_FAILED,
                        "server did not come up with OLLAMA_NUM_PARALLEL=" + candidate);
                }
                BenchmarkReport report;
                try {
                    report = benchmarks.run(restarted.installation().endpoint(), request, String.valueOf(candidate),
                        new ConcurrencyBenchmarkService.Listener() {
                            @Override
                            public void phase(String text, double fraction) {
                                listener.phase("OLLAMA_NUM_PARALLEL=" + candidate + ": " + text,
                                    (base + 1 + fraction * (perCandidate - 1)) / totalSteps);
                            }

                            @Override
                            public void stageFinished(StageResult stage) {
                                listener.stageFinished(stage);
                            }
                        }, cancellation);
                } catch (OperationCancelledException e) {
                    break;
                }
                reports.add(report);
                listener.reportFinished(report);
                index++;
            }
        } catch (OperationException e) {
            failure = e;
        } finally {
            listener.phase("Restoring the server's previous settings", 1);
            restore(installation, original, failure);
        }
        if (failure != null) {
            throw failure;
        }
        if (reports.isEmpty() && cancellation.isCancelled()) {
            throw new OperationCancelledException("benchmark cancelled");
        }
        listener.phase("Done", 1);
        return reports;
    }

    /**
     * Bring the server back with its original settings; a cancel or failure of the sweep must not leave it on a
     * candidate or down. A failed candidate restart leaves no owned server, so the installation is started again.
     */
    private void restore(OllamaInstallation installation, ServerSettings original, OperationException earlier)
        throws OperationException {
        try {
            ServerStatus now = lifecycle.status();
            if (now.ownership() == RuntimeOwnership.OWNED && now.state() == ServerState.RUNNING) {
                lifecycle.restart(original, CancellationToken.none());
            } else {
                lifecycle.start(installation, original, CancellationToken.none());
            }
        } catch (OperationException e) {
            if (earlier == null) {
                throw new OperationException(e.kind(), "benchmark finished, but restoring the server's previous "
                    + "settings failed: " + e.getMessage() + ". Restart it from the dashboard.", e);
            }
            earlier.addSuppressed(e);
        }
    }
}
