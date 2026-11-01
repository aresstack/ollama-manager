package com.aresstack.ollamamanager.application.benchmark;

import com.aresstack.ollamamanager.application.common.CancellationToken;
import com.aresstack.ollamamanager.application.common.LifecycleLock;
import com.aresstack.ollamamanager.application.common.ErrorKind;
import com.aresstack.ollamamanager.application.common.OperationCancelledException;
import com.aresstack.ollamamanager.application.common.OperationException;
import com.aresstack.ollamamanager.application.common.ProgressListener;
import com.aresstack.ollamamanager.application.common.TransferProgress;
import com.aresstack.ollamamanager.application.port.ManagedServer;
import com.aresstack.ollamamanager.application.port.OllamaApiPort;
import com.aresstack.ollamamanager.application.port.OllamaRuntimePort;
import com.aresstack.ollamamanager.application.server.ServerLifecycleService;
import com.aresstack.ollamamanager.domain.benchmark.GenerationMetrics;
import com.aresstack.ollamamanager.domain.model.InstalledModel;
import com.aresstack.ollamamanager.domain.model.LoadedModel;
import com.aresstack.ollamamanager.domain.model.ModelDetails;
import com.aresstack.ollamamanager.domain.model.ModelIdentity;
import com.aresstack.ollamamanager.domain.model.ModelOptions;
import com.aresstack.ollamamanager.domain.server.Endpoint;
import com.aresstack.ollamamanager.domain.server.InstallationKind;
import com.aresstack.ollamamanager.domain.server.OllamaInstallation;
import com.aresstack.ollamamanager.domain.server.ServerSettingCatalog;
import com.aresstack.ollamamanager.domain.server.ServerSettings;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ParallelismSweepServiceTest {

    private static final Endpoint ENDPOINT = Endpoint.parse("127.0.0.1:11499");
    private static final OllamaInstallation INSTALLATION = new OllamaInstallation(InstallationKind.MANAGED_PORTABLE,
        "0.1.0", new File("ollama").getAbsoluteFile(), ENDPOINT);

    /** Fake server process that remembers its settings. */
    private static final class FakeServer implements ManagedServer {
        final ServerSettings settings;
        Integer exitCode;

        FakeServer(ServerSettings settings) {
            this.settings = settings;
        }

        @Override
        public OllamaInstallation installation() {
            return INSTALLATION;
        }

        @Override
        public ServerSettings appliedSettings() {
            return settings;
        }

        @Override
        public boolean isAlive() {
            return exitCode == null;
        }

        @Override
        public Integer exitCode() {
            return exitCode;
        }
    }

    /** Records the OLLAMA_NUM_PARALLEL of every start, in order. */
    private static final class FakeRuntime implements OllamaRuntimePort {
        final List<Integer> startedWith = new ArrayList<Integer>();
        FakeServer current;
        int failStartNumber = -1;
        /** Start number at which the user cancels while the process comes up; -1 for never. */
        int cancelStartNumber = -1;
        CancellationToken userToken;

        @Override
        public List<OllamaInstallation> discover(List<File> additionalDirectories, CancellationToken cancellation) {
            return Collections.emptyList();
        }

        @Override
        public ManagedServer start(OllamaInstallation installation, ServerSettings settings,
                                   CancellationToken cancellation) throws OperationException {
            startedWith.add(settings.parallelism());
            if (startedWith.size() == failStartNumber) {
                throw new OperationException(ErrorKind.PROCESS_FAILED, "boom");
            }
            if (startedWith.size() == cancelStartNumber) {
                userToken.cancel();
                throw new OperationCancelledException("cancelled while starting");
            }
            current = new FakeServer(settings);
            return current;
        }

        @Override
        public void stop(ManagedServer server, long gracePeriodMillis) {
            ((FakeServer) server).exitCode = 0;
        }

        @Override
        public List<String> recentLog(ManagedServer server) {
            return Collections.emptyList();
        }
    }

    /** Answers faster the higher the server's OLLAMA_NUM_PARALLEL, so that reports differ measurably. */
    private final class FakeApi implements OllamaApiPort {
        @Override
        public GenerationMetrics generate(Endpoint endpoint, String modelName, String prompt, ModelOptions options,
                                          String keepAlive, CancellationToken cancellation) throws OperationException {
            Integer parallel = runtime.current.settings.parallelism();
            int factor = parallel == null ? 1 : parallel;
            try {
                Thread.sleep(200 / factor);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new OperationException(ErrorKind.CANCELLED, "interrupted");
            }
            long evalNanos = 1_000_000_000L / factor;
            return new GenerationMetrics(1, 200 / factor, 1, 1, 1, 10, evalNanos);
        }

        @Override
        public List<LoadedModel> loadedModels(Endpoint endpoint, CancellationToken cancellation) {
            return Collections.singletonList(new LoadedModel(ModelIdentity.named("m"), 10, 10, null, null));
        }

        @Override
        public String version(Endpoint endpoint, CancellationToken cancellation) {
            return "0.1.0";
        }

        @Override
        public List<InstalledModel> installedModels(Endpoint endpoint, CancellationToken cancellation) {
            return Collections.emptyList();
        }

        @Override
        public ModelDetails show(Endpoint endpoint, String modelName, CancellationToken cancellation) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void pull(Endpoint endpoint, String modelName, ProgressListener<TransferProgress> listener,
                         CancellationToken cancellation) {
        }

        @Override
        public void preload(Endpoint endpoint, String modelName, ModelOptions options, String keepAlive,
                            CancellationToken cancellation) {
        }

        @Override
        public void unload(Endpoint endpoint, String modelName, CancellationToken cancellation) {
        }

        @Override
        public void delete(Endpoint endpoint, String modelName, CancellationToken cancellation) {
        }
    }

    private static class Recorder implements ParallelismSweepService.Listener {
        final List<BenchmarkReport> reports = new ArrayList<BenchmarkReport>();
        final List<String> phases = new ArrayList<String>();
        OperationException restoreFailure;

        @Override
        public void phase(String text, double fraction) {
            phases.add(text);
            assertTrue(text, fraction >= 0 && fraction <= 1);
        }

        @Override
        public void stageFinished(StageResult stage) {
        }

        @Override
        public void reportFinished(BenchmarkReport report) {
            reports.add(report);
        }

        @Override
        public void failedAfterCancel(OperationException failure) {
            restoreFailure = failure;
        }
    }

    private final FakeRuntime runtime = new FakeRuntime();
    private final LifecycleLock lock = new LifecycleLock();
    private ServerLifecycleService lifecycle;
    private ParallelismSweepService service;

    @Before
    public void startOwnedServer() throws Exception {
        lifecycle = new ServerLifecycleService(runtime, 0, lock);
        // The original server runs with OLLAMA_NUM_PARALLEL unset.
        lifecycle.start(INSTALLATION, ServerSettings.defaults(), CancellationToken.none());
        service = new ParallelismSweepService(lifecycle, new ConcurrencyBenchmarkService(new FakeApi(), null, null),
            lock);
    }

    private static ConcurrencyBenchmarkRequest request() {
        return new ConcurrencyBenchmarkRequest("m", Arrays.asList(1, 2), null, 10, 1, null, true);
    }

    @Test
    public void restartsPerCandidateAndRestoresOriginalSettings() throws Exception {
        Recorder recorder = new Recorder();
        List<BenchmarkReport> reports = service.run(request(), Arrays.asList(8, 4), recorder, CancellationToken.none());
        // Initial start, then 4, 8, then the restore with the original (unset) value.
        assertEquals(Arrays.asList(null, 4, 8, null), runtime.startedWith);
        assertEquals(2, reports.size());
        assertEquals("4", reports.get(0).serverNumParallel());
        assertEquals("8", reports.get(1).serverNumParallel());
        assertEquals(2, recorder.reports.size());
        assertNull(lifecycle.appliedSettings().parallelism());
        assertTrue(reports.get(1).optimum().aggregateTps() > reports.get(0).optimum().aggregateTps());
        // Each report records the settings its server ran with.
        assertEquals(Integer.valueOf(4), reports.get(0).serverSettings().parallelism());
        assertEquals(Integer.valueOf(8), reports.get(1).serverSettings().parallelism());
    }

    @Test
    public void restoresSettingsWhenARestartFails() throws Exception {
        runtime.failStartNumber = 3;
        try {
            service.run(request(), Arrays.asList(4, 8), new Recorder(), CancellationToken.none());
            fail("expected failure");
        } catch (OperationException e) {
            assertEquals(ErrorKind.PROCESS_FAILED, e.kind());
        }
        // The failed start left no owned server; the installation is started again with the original settings.
        assertEquals(Arrays.asList(null, 4, 8, null), runtime.startedWith);
        assertTrue(lifecycle.appliedSettings() != null);
        assertNull(lifecycle.appliedSettings().parallelism());
    }

    @Test
    public void cancellationKeepsFinishedReportsAndRestores() throws Exception {
        final CancellationToken token = new CancellationToken();
        List<BenchmarkReport> reports = service.run(request(), Arrays.asList(2, 4, 8), new Recorder() {
            @Override
            public void reportFinished(BenchmarkReport report) {
                super.reportFinished(report);
                token.cancel();
            }
        }, token);
        assertEquals(1, reports.size());
        assertEquals(Arrays.asList(null, 2, null), runtime.startedWith);
        assertNull(lifecycle.appliedSettings().parallelism());
    }

    @Test
    public void cancelDuringARestartKeepsFinishedReportsAndRestores() throws Exception {
        final CancellationToken token = new CancellationToken();
        runtime.userToken = token;
        // Initial start, candidate 2, then the user cancels while candidate 4 comes up.
        runtime.cancelStartNumber = 3;
        List<BenchmarkReport> reports = service.run(request(), Arrays.asList(2, 4, 8), new Recorder(), token);
        assertEquals(1, reports.size());
        assertEquals("2", reports.get(0).serverNumParallel());
        // The cancelled start left no server, so the installation is started again with the original settings.
        assertEquals(Arrays.asList(null, 2, 4, null), runtime.startedWith);
        assertNull(lifecycle.appliedSettings().parallelism());
    }

    @Test
    public void reportsARestoreFailureAfterCancelThroughTheListener() throws Exception {
        final CancellationToken token = new CancellationToken();
        // Initial start, candidate 2, then the restore's start fails.
        runtime.failStartNumber = 3;
        Recorder recorder = new Recorder() {
            @Override
            public void reportFinished(BenchmarkReport report) {
                super.reportFinished(report);
                token.cancel();
            }
        };
        List<BenchmarkReport> reports = service.run(request(), Arrays.asList(2, 4), recorder, token);
        assertEquals(1, reports.size());
        assertTrue(recorder.restoreFailure != null);
        assertEquals(ErrorKind.PROCESS_FAILED, recorder.restoreFailure.kind());
        assertTrue(recorder.restoreFailure.getMessage(), recorder.restoreFailure.getMessage().contains("dashboard"));
        assertNull(lifecycle.appliedSettings());
    }

    @Test
    public void holdsTheLifecycleLockSoOtherOperationsFailFast() throws Exception {
        final List<ErrorKind> otherThread = new ArrayList<ErrorKind>();
        final List<Boolean> held = new ArrayList<Boolean>();
        Recorder prober = new Recorder() {
            @Override
            public void stageFinished(StageResult stage) {
                held.add(lock.isHeld());
                Thread dashboard = new Thread(() -> {
                    try {
                        lifecycle.stop();
                        otherThread.add(null);
                    } catch (OperationException e) {
                        otherThread.add(e.kind());
                    }
                });
                dashboard.start();
                try {
                    dashboard.join();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        };
        List<BenchmarkReport> reports = service.run(request(), Arrays.asList(2), prober, CancellationToken.none());
        assertEquals(1, reports.size());
        assertEquals(Arrays.asList(true, true), held);
        // A stop from the dashboard during the measurement is refused instead of changing the server.
        assertEquals(Arrays.asList(ErrorKind.INVALID_STATE, ErrorKind.INVALID_STATE), otherThread);
        assertEquals(Arrays.asList(null, 2, null), runtime.startedWith);
        assertFalse(lock.isHeld());
    }

    @Test
    public void refusesWhileAnotherLifecycleOperationHoldsTheLock() throws Exception {
        final List<ErrorKind> kinds = new ArrayList<ErrorKind>();
        lock.run("test", () -> {
            Thread sweep = new Thread(() -> {
                try {
                    service.run(request(), Arrays.asList(2), new Recorder(), CancellationToken.none());
                } catch (OperationException e) {
                    kinds.add(e.kind());
                }
            });
            sweep.start();
            try {
                sweep.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return null;
        });
        assertEquals(Arrays.asList(ErrorKind.INVALID_STATE), kinds);
        assertEquals(Arrays.asList((Integer) null), runtime.startedWith);
    }

    @Test
    public void startsAServerAgainThatDiedUnderASweepValue() throws Exception {
        Recorder crash = new Recorder() {
            @Override
            public void stageFinished(StageResult stage) {
                // The process exits under the current value.
                runtime.current.exitCode = 3;
            }
        };
        try {
            service.run(request(), Arrays.asList(2, 4), crash, CancellationToken.none());
            fail("expected PROCESS_FAILED");
        } catch (OperationException e) {
            assertEquals(ErrorKind.PROCESS_FAILED, e.kind());
            assertTrue(e.getMessage(), e.getMessage().contains("exited unexpectedly with code 3"));
        }
        // Started again with the original (unset) value, not left down.
        assertEquals(Arrays.asList(null, 2, null), runtime.startedWith);
        assertNull(lifecycle.appliedSettings().parallelism());
    }

    @Test
    public void restoreFailureAfterAFailedSweepIsPartOfTheMessage() throws Exception {
        // Initial start, candidate 2, candidate 4 fails, the restore start fails too.
        runtime.failStartNumber = 3;
        FakeRuntime twice = runtime;
        try {
            service.run(request(), Arrays.asList(2, 4), new Recorder() {
                @Override
                public void phase(String text, double fraction) {
                    super.phase(text, fraction);
                    if (text.startsWith("Restoring")) {
                        twice.failStartNumber = 4;
                    }
                }
            }, CancellationToken.none());
            fail("expected failure");
        } catch (OperationException e) {
            assertEquals(ErrorKind.PROCESS_FAILED, e.kind());
            assertTrue(e.getMessage(), e.getMessage().contains("Afterwards"));
            assertTrue(e.getMessage(), e.getMessage().contains("dashboard"));
        }
        assertEquals(Arrays.asList(null, 2, 4, null), runtime.startedWith);
    }

    @Test
    public void cancelDuringTheRestoreStillReportsItsFailure() throws Exception {
        final CancellationToken token = new CancellationToken();
        runtime.userToken = token;
        // Initial start, candidate 2, then the restore's start: the user cancels while it runs and it fails.
        runtime.cancelStartNumber = 3;
        Recorder recorder = new Recorder();
        List<BenchmarkReport> reports = service.run(request(), Arrays.asList(2), recorder, token);
        assertEquals(1, reports.size());
        assertTrue(recorder.restoreFailure != null);
        assertTrue(recorder.restoreFailure.getMessage(), recorder.restoreFailure.getMessage().contains("dashboard"));
    }

    @Test
    public void leavesAServerRestartedWithOtherSettingsAlone() throws Exception {
        Recorder restarter = new Recorder() {
            boolean done;

            @Override
            public void stageFinished(StageResult stage) {
                if (done) {
                    return;
                }
                done = true;
                try {
                    lifecycle.restart(ServerSettings.defaults().with(ServerSettingCatalog.PARALLELISM, 7),
                        CancellationToken.none());
                } catch (OperationException e) {
                    throw new AssertionError(e);
                }
            }
        };
        List<BenchmarkReport> reports = service.run(request(), Arrays.asList(2), restarter, CancellationToken.none());
        assertEquals(1, reports.size());
        // The user's restart with 7 is the last word; the sweep does not revert it.
        assertEquals(Arrays.asList(null, 2, 7), runtime.startedWith);
        assertEquals(Integer.valueOf(7), lifecycle.appliedSettings().parallelism());
    }

    @Test
    public void refusesExternalServers() throws Exception {
        LifecycleLock externalLock = new LifecycleLock();
        ServerLifecycleService external = new ServerLifecycleService(new FakeRuntime(), 0, externalLock);
        external.attachExternal(OllamaInstallation.external("0.1.0", ENDPOINT), "0.1.0");
        ParallelismSweepService sweeps = new ParallelismSweepService(external,
            new ConcurrencyBenchmarkService(new FakeApi(), null, null), externalLock);
        try {
            sweeps.run(request(), Arrays.asList(4), new Recorder(), CancellationToken.none());
            fail("expected NOT_OWNED");
        } catch (OperationException e) {
            assertEquals(ErrorKind.NOT_OWNED, e.kind());
        }
    }

    @Test
    public void candidatesAreDistinctSortedAndBounded() {
        assertEquals(Arrays.asList(1, 4, 8), ParallelismSweepService.candidates(Arrays.asList(8, 4, 1, 4)));
        try {
            ParallelismSweepService.candidates(Arrays.asList(0));
            fail("expected rejection");
        } catch (IllegalArgumentException expected) {
            assertFalse(expected.getMessage().isEmpty());
        }
    }

    @Test
    public void keepsOriginalParallelismWhenItWasSet() throws Exception {
        lifecycle.restart(ServerSettings.defaults().with(ServerSettingCatalog.PARALLELISM, 3),
            CancellationToken.none());
        runtime.startedWith.clear();
        service.run(request(), Arrays.asList(6), new Recorder(), CancellationToken.none());
        assertEquals(Arrays.asList(6, 3), runtime.startedWith);
        assertEquals(Integer.valueOf(3), lifecycle.appliedSettings().parallelism());
    }
}
