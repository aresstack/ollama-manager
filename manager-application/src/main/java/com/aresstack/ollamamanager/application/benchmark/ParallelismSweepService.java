package com.aresstack.ollamamanager.application.benchmark;

import com.aresstack.ollamamanager.application.common.CancellationToken;
import com.aresstack.ollamamanager.application.common.ErrorKind;
import com.aresstack.ollamamanager.application.common.LifecycleLock;
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
 * each candidate and, whatever happens, brought back to its original settings at the end, as long as it still
 * runs the way the sweep left it: a server the user stopped or restarted with other settings in the meantime is
 * left alone. The stored desired settings are never touched, so nothing in the configuration needs restoring.
 */
public final class ParallelismSweepService {

    /** Receives progress; called on the worker thread. */
    public interface Listener {
        void phase(String text, double fraction);

        void stageFinished(StageResult stage);

        void reportFinished(BenchmarkReport report);

        /**
         * The server could not be brought back to its original settings after a cancelled sweep; the cancelled
         * run itself reports no error, so the failure is delivered here. After a failed or finished sweep the
         * failure propagates from {@link #run} instead.
         */
        void restoreFailed(OperationException failure);
    }

    private final ServerLifecycleService lifecycle;
    private final ConcurrencyBenchmarkService benchmarks;
    private final LifecycleLock lock;

    /**
     * @param lock the lock the lifecycle service uses; the whole sweep runs under it, so that a stop, restart or
     *             profile activation from elsewhere fails fast instead of changing the server under measurement
     */
    public ParallelismSweepService(ServerLifecycleService lifecycle, ConcurrencyBenchmarkService benchmarks,
                                   LifecycleLock lock) {
        if (lifecycle == null || benchmarks == null || lock == null) {
            throw new IllegalArgumentException("lifecycle, benchmarks and lock must not be null");
        }
        this.lifecycle = lifecycle;
        this.benchmarks = benchmarks;
        this.lock = lock;
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
     * cancelled, also when the cancel hits a restart; throws {@link OperationCancelledException} only when none
     * finished.
     *
     * @throws OperationException {@code NOT_OWNED} for an external server, {@code INVALID_STATE} without a running
     *                            server, while another lifecycle operation holds the lock, or when the server is
     *                            stopped during the sweep, or the failure of a restart or benchmark; the original
     *                            settings are restored before the exception propagates
     */
    public List<BenchmarkReport> run(final ConcurrencyBenchmarkRequest request, List<Integer> rawCandidates,
                                     final Listener listener, final CancellationToken cancellation)
        throws OperationException {
        final List<Integer> values = candidates(rawCandidates);
        // The lock is reentrant for this thread, so the sweep's own restarts still go through.
        return lock.run("benchmark server values", () -> runUnderLock(request, values, listener, cancellation));
    }

    private List<BenchmarkReport> runUnderLock(ConcurrencyBenchmarkRequest request, List<Integer> values,
                                               final Listener listener, final CancellationToken cancellation)
        throws OperationException {
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
        Restore restore = new Restore(status.installation(), original);
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
                ServerSettings settings = original.with(ServerSettingCatalog.PARALLELISM, candidate);
                ServerStatus restarted;
                try {
                    restarted = restartWith(settings, restore, cancellation);
                } catch (OperationCancelledException e) {
                    break;
                }
                BenchmarkReport report;
                try {
                    report = benchmarks.run(restarted.installation().endpoint(), request, settings,
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
            restore.run(listener, failure, cancellation.isCancelled());
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
     * Restart the owned server with {@code candidate}. Refuses when the server is no longer running as the sweep
     * left it, which means the user stopped it meanwhile; the sweep then ends without touching it.
     */
    private ServerStatus restartWith(ServerSettings candidate, Restore restore, CancellationToken cancellation)
        throws OperationException {
        ServerStatus now = lifecycle.status();
        if (now.ownership() != RuntimeOwnership.OWNED || now.state() != ServerState.RUNNING) {
            throw new OperationException(ErrorKind.INVALID_STATE,
                "the server was stopped during the benchmark; start it again to continue");
        }
        restore.restartAttempted();
        ServerStatus restarted = lifecycle.restart(candidate, cancellation);
        if (restarted.installation() == null || restarted.state() != ServerState.RUNNING) {
            throw new OperationException(ErrorKind.PROCESS_FAILED,
                "server did not come up with OLLAMA_NUM_PARALLEL=" + candidate.parallelism());
        }
        restore.restarted(candidate);
        return restarted;
    }

    /** What the sweep did to the server, and how to undo it. */
    private final class Restore {
        private final OllamaInstallation installation;
        private final ServerSettings original;
        /** Settings the sweep last applied successfully, or {@code null} before the first restart. */
        private ServerSettings applied;
        /** Whether a restart started by the sweep may have left the server down. */
        private boolean restartPending;

        Restore(OllamaInstallation installation, ServerSettings original) {
            this.installation = installation;
            this.original = original;
        }

        void restartAttempted() {
            restartPending = true;
        }

        void restarted(ServerSettings candidate) {
            applied = candidate;
            restartPending = false;
        }

        /**
         * Bring the server back with its original settings when it still runs on a sweep value, or start it again
         * when a restart of the sweep itself left it down. A server the user stopped or restarted with other
         * settings is not touched.
         */
        void run(Listener listener, OperationException earlier, boolean cancelled) throws OperationException {
            try {
                ServerStatus now = lifecycle.status();
                boolean running = now.ownership() == RuntimeOwnership.OWNED && now.state() == ServerState.RUNNING;
                if (running) {
                    if (applied != null && applied.equals(lifecycle.appliedSettings())) {
                        lifecycle.restart(original, CancellationToken.none());
                    }
                } else if (restartPending) {
                    lifecycle.start(installation, original, CancellationToken.none());
                }
            } catch (OperationException e) {
                OperationException failure = new OperationException(e.kind(), "the server could not be brought "
                    + "back to its previous settings: " + e.getMessage() + ". Restart it from the dashboard.", e);
                if (cancelled) {
                    // A cancelled run reports no error, so the failure goes through the listener.
                    listener.restoreFailed(failure);
                } else if (earlier == null) {
                    throw failure;
                } else {
                    earlier.addSuppressed(failure);
                }
            }
        }
    }
}
