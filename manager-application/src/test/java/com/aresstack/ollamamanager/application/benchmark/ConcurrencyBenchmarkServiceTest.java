package com.aresstack.ollamamanager.application.benchmark;

import com.aresstack.ollamamanager.application.common.CancellationToken;
import com.aresstack.ollamamanager.application.common.ErrorKind;
import com.aresstack.ollamamanager.application.common.OperationException;
import com.aresstack.ollamamanager.application.common.ProgressListener;
import com.aresstack.ollamamanager.application.common.TransferProgress;
import com.aresstack.ollamamanager.application.port.GpuTelemetryPort;
import com.aresstack.ollamamanager.application.port.OllamaApiPort;
import com.aresstack.ollamamanager.application.profile.ProfileEnvironment;
import com.aresstack.ollamamanager.domain.hardware.GpuFingerprint;
import com.aresstack.ollamamanager.domain.hardware.GpuUsageSample;
import com.aresstack.ollamamanager.domain.benchmark.GenerationMetrics;
import com.aresstack.ollamamanager.domain.model.InstalledModel;
import com.aresstack.ollamamanager.domain.model.LoadedModel;
import com.aresstack.ollamamanager.domain.model.ModelDetails;
import com.aresstack.ollamamanager.domain.model.ModelIdentity;
import com.aresstack.ollamamanager.domain.model.ModelOptions;
import com.aresstack.ollamamanager.domain.server.Endpoint;
import com.aresstack.ollamamanager.domain.server.ServerSettingCatalog;
import com.aresstack.ollamamanager.domain.server.ServerSettings;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ConcurrencyBenchmarkServiceTest {

    private static final Endpoint ENDPOINT = Endpoint.DEFAULT;

    /** Fake server: each request takes {@code baseMillis} plus a share per concurrent request. */
    private static final class FakeApi implements OllamaApiPort {
        final AtomicInteger inFlight = new AtomicInteger();
        final List<Integer> observedConcurrency = Collections.synchronizedList(new ArrayList<Integer>());
        int failAbove = Integer.MAX_VALUE;
        double gpuShare = 1.0;
        int tokens = 50;
        long sleepMillis = 20;
        /** Capabilities {@code /api/show} reports; {@code null} makes show fail. */
        List<String> capabilities;
        /** Whether generate answers like Ollama does for an embedding-only model. */
        boolean generateUnsupported;
        final AtomicInteger generateCalls = new AtomicInteger();
        final AtomicInteger embedCalls = new AtomicInteger();
        final List<String> embedInputs = Collections.synchronizedList(new ArrayList<String>());

        @Override
        public GenerationMetrics generate(Endpoint endpoint, String modelName, String prompt, ModelOptions options,
                                          String keepAlive, CancellationToken cancellation) throws OperationException {
            generateCalls.incrementAndGet();
            if (generateUnsupported) {
                throw new OperationException(ErrorKind.HTTP_ERROR,
                    "HTTP 400: \"" + modelName + "\" does not support generate");
            }
            int now = inFlight.incrementAndGet();
            try {
                observedConcurrency.add(now);
                if (now > failAbove) {
                    throw new OperationException(ErrorKind.SERVICE_UNAVAILABLE, "HTTP 503");
                }
                Thread.sleep(sleepMillis);
                int predicted = options.numPredict() == null ? tokens : Math.min(tokens, options.numPredict());
                // Each chat gets a slice of a fixed server speed: 1000 tok/s shared.
                long evalNanos = predicted * now * 1_000_000L;
                return new GenerationMetrics(5, 20, 300_000_000L, 10, 50_000_000L, predicted, evalNanos);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new OperationException(ErrorKind.CANCELLED, "interrupted");
            } finally {
                inFlight.decrementAndGet();
            }
        }

        @Override
        public List<LoadedModel> loadedModels(Endpoint endpoint, CancellationToken cancellation) {
            if (gpuShare < 0) {
                return Collections.emptyList();
            }
            long size = 1000;
            return Collections.singletonList(new LoadedModel(ModelIdentity.named("m"), size,
                (long) (size * gpuShare), 4096, null));
        }

        @Override
        public String version(Endpoint endpoint, CancellationToken cancellation) {
            return "0.0";
        }

        @Override
        public List<InstalledModel> installedModels(Endpoint endpoint, CancellationToken cancellation) {
            return Collections.emptyList();
        }

        @Override
        public GenerationMetrics embed(Endpoint endpoint, String modelName, String input, ModelOptions options,
                                       String keepAlive, CancellationToken cancellation) throws OperationException {
            embedCalls.incrementAndGet();
            embedInputs.add(input);
            int now = inFlight.incrementAndGet();
            try {
                observedConcurrency.add(now);
                if (now > failAbove) {
                    throw new OperationException(ErrorKind.SERVICE_UNAVAILABLE, "HTTP 503");
                }
                Thread.sleep(sleepMillis);
                // Input tokens are what the server counts; it processes 2000 tok/s shared between requests.
                int inputTokens = input.isEmpty() ? 0 : input.split(" ").length;
                long promptNanos = inputTokens * now * 500_000L;
                return new GenerationMetrics(20, 20, 300_000_000L, inputTokens, promptNanos, 0, 0);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new OperationException(ErrorKind.CANCELLED, "interrupted");
            } finally {
                inFlight.decrementAndGet();
            }
        }

        @Override
        public ModelDetails show(Endpoint endpoint, String modelName, CancellationToken cancellation)
            throws OperationException {
            if (capabilities == null) {
                throw new OperationException(ErrorKind.NOT_FOUND, "no details");
            }
            return new ModelDetails(modelName, null, null, null, null, null, capabilities);
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

    private static final class MemoryHistory implements BenchmarkHistoryPort {
        final List<BenchmarkReport> saved = new ArrayList<BenchmarkReport>();

        @Override
        public void save(BenchmarkReport report) {
            saved.add(report);
        }

        @Override
        public List<BenchmarkReport> list() {
            return saved;
        }

        @Override
        public void delete(String id) {
        }
    }

    private static final ConcurrencyBenchmarkService.Listener NO_LISTENER =
        new ConcurrencyBenchmarkService.Listener() {
            @Override
            public void phase(String text, double fraction) {
            }

            @Override
            public void stageFinished(StageResult stage) {
            }
        };

    private static ConcurrencyBenchmarkRequest request(Integer... levels) {
        return new ConcurrencyBenchmarkRequest("m", Arrays.asList(levels), null, 50, 1, null, true);
    }

    @Test
    public void recordsTheServerSettingsTheRunWasMeasuredWith() throws Exception {
        ConcurrencyBenchmarkService service = new ConcurrencyBenchmarkService(new FakeApi(), null, null);
        BenchmarkReport unknown = service.run(ENDPOINT, request(1), null, NO_LISTENER, CancellationToken.none());
        assertNull(unknown.serverNumParallel());
        assertNull(unknown.serverSettings());
        BenchmarkReport unset = service.run(ENDPOINT, request(1), ServerSettings.defaults(), NO_LISTENER,
            CancellationToken.none());
        assertEquals(ConcurrencyBenchmarkService.SERVER_DEFAULT, unset.serverNumParallel());
        assertNull(unset.serverSettings().parallelism());
        ServerSettings four = ServerSettings.defaults().with(ServerSettingCatalog.PARALLELISM, 4);
        BenchmarkReport set = service.run(ENDPOINT, request(1), four, NO_LISTENER, CancellationToken.none());
        assertEquals("4", set.serverNumParallel());
        assertEquals(four, set.serverSettings());
    }

    @Test
    public void recordsTheEnvironmentTheRunWasMeasuredOn() throws Exception {
        ProfileEnvironment environment = cancellation -> new ProfileEnvironment.Snapshot(new GpuFingerprint("rtx"),
            "0.40.13", Collections.singletonList(new ModelIdentity("m", "sha256:abc", "Q4_K_M")));
        ConcurrencyBenchmarkService service = new ConcurrencyBenchmarkService(new FakeApi(), null, null, environment);
        BenchmarkReport report = service.run(ENDPOINT, request(1), null, NO_LISTENER, CancellationToken.none());
        assertNotNull(report.environment());
        assertTrue(report.environment().isComplete());
        assertEquals("rtx", report.environment().gpuFingerprint().value());
        assertEquals("0.40.13", report.environment().ollamaVersion());
        assertEquals("sha256:abc", report.environment().model().digest());
    }

    @Test
    public void runsChatsGenuinelyInParallelAndRecordsEveryStage() throws Exception {
        FakeApi api = new FakeApi();
        MemoryHistory history = new MemoryHistory();
        ConcurrencyBenchmarkService service = new ConcurrencyBenchmarkService(api, null, history);
        final List<StageResult> seen = new ArrayList<StageResult>();
        BenchmarkReport report = service.run(ENDPOINT, request(1, 4), null, new ConcurrencyBenchmarkService.Listener() {
            @Override
            public void phase(String text, double fraction) {
            }

            @Override
            public void stageFinished(StageResult stage) {
                seen.add(stage);
            }
        }, CancellationToken.none());
        assertEquals(2, report.stages().size());
        assertEquals(2, seen.size());
        assertTrue(report.complete());
        assertEquals(300, report.loadMillis());
        assertTrue("4 chats must overlap", Collections.max(api.observedConcurrency) >= 3);
        StageResult four = report.stages().get(1);
        assertEquals(4, four.parallel());
        assertEquals(4, four.requests());
        assertEquals(0, four.failures());
        assertTrue(four.isStable());
        assertTrue(four.aggregateTps() > 0);
        assertTrue(four.perChatTpsMin() <= four.perChatTpsAvg());
        assertEquals(1, history.saved.size());
        assertNull(report.serverNumParallel());
    }

    @Test
    public void stopsAtFirstStageWithErrorsAndMarksIncomplete() throws Exception {
        FakeApi api = new FakeApi();
        api.failAbove = 2;
        ConcurrencyBenchmarkService service = new ConcurrencyBenchmarkService(api, null, null);
        BenchmarkReport report = service.run(ENDPOINT, request(1, 2, 4, 8), null, NO_LISTENER,
            CancellationToken.none());
        assertEquals(3, report.stages().size());
        assertFalse(report.complete());
        StageResult failed = report.stages().get(2);
        assertTrue(failed.failures() > 0);
        assertFalse(failed.isStable());
        assertNotNull(failed.firstError());
        assertEquals(2, report.maxStable().parallel());
        assertNotNull(report.optimum());
    }

    @Test
    public void modelLeavingTheGpuIsUnstable() throws Exception {
        FakeApi api = new FakeApi();
        api.gpuShare = 0.6;
        ConcurrencyBenchmarkService service = new ConcurrencyBenchmarkService(api, null, null);
        BenchmarkReport report = service.run(ENDPOINT, request(1), null, NO_LISTENER, CancellationToken.none());
        assertFalse(report.stages().get(0).isStable());
        assertNull(report.optimum());
        api.gpuShare = -1;
        report = service.run(ENDPOINT, request(1), null, NO_LISTENER, CancellationToken.none());
        assertEquals(Double.valueOf(0), report.stages().get(0).gpuShare());
    }

    @Test
    public void cancellationKeepsFinishedStages() throws Exception {
        FakeApi api = new FakeApi();
        ConcurrencyBenchmarkService service = new ConcurrencyBenchmarkService(api, null, null);
        final CancellationToken token = new CancellationToken();
        BenchmarkReport report = service.run(ENDPOINT, request(1, 2, 4), null, new ConcurrencyBenchmarkService.Listener() {
            @Override
            public void phase(String text, double fraction) {
            }

            @Override
            public void stageFinished(StageResult stage) {
                if (stage.parallel() == 2) {
                    token.cancel();
                }
            }
        }, token);
        assertEquals(2, report.stages().size());
        assertFalse(report.complete());
    }

    @Test
    public void slowTelemetryDoesNotStretchStageTime() throws Exception {
        FakeApi api = new FakeApi();
        // Chats outlive the first telemetry interval, so a sample is in flight when they finish.
        api.sleepMillis = ConcurrencyBenchmarkService.TELEMETRY_INTERVAL_MILLIS + 100;
        GpuTelemetryPort slowTelemetry = cancellation -> {
            try {
                Thread.sleep(400);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return Collections.<GpuUsageSample>emptyList();
        };
        ConcurrencyBenchmarkService service = new ConcurrencyBenchmarkService(api, slowTelemetry, null);
        BenchmarkReport report = service.run(ENDPOINT, request(2), null, NO_LISTENER, CancellationToken.none());
        assertTrue("stage wall time must end with the last chat, was " + report.stages().get(0).wallMillis(),
            report.stages().get(0).wallMillis() < ConcurrencyBenchmarkService.TELEMETRY_INTERVAL_MILLIS + 300);
    }

    @Test
    public void summaryMath() {
        List<GenerationMetrics> results = Arrays.asList(
            new GenerationMetrics(10, 1000, 0, 5, 0, 100, 1_000_000_000L),
            new GenerationMetrics(30, 2000, 0, 5, 0, 100, 2_000_000_000L));
        StageResult stage = ConcurrencyBenchmarkService.summarize(2, results, new ArrayList<String>(), 2000, 123L,
            1.0);
        assertEquals(100, stage.aggregateTps(), 1e-9);
        assertEquals(75, stage.perChatTpsAvg(), 1e-9);
        assertEquals(50, stage.perChatTpsMin(), 1e-9);
        assertEquals(10, stage.ttftP50Millis());
        assertEquals(30, stage.ttftP95Millis());
        assertEquals(2000, stage.latencyP95Millis());
        assertEquals(Long.valueOf(123), stage.peakVramBytes());
        assertEquals(0, ConcurrencyBenchmarkService.percentile(new ArrayList<Long>(), 50));
    }

    @Test
    public void optimumIsHighestStableThroughput() {
        List<StageResult> stages = Arrays.asList(
            stage(1, 50, true), stage(2, 90, true), stage(4, 120, true), stage(8, 150, false));
        BenchmarkReport report = new BenchmarkReport("id", 0, "m", null, 50, "4", 100, stages, true);
        assertEquals(4, report.optimum().parallel());
        assertEquals(4, report.maxStable().parallel());
    }

    private static StageResult stage(int parallel, double total, boolean stable) {
        return new StageResult(parallel, parallel, stable ? 0 : 1, total / parallel, total / parallel, total, 10, 20,
            100, 200, 1000, null, 1.0, stable ? null : "boom");
    }

    @Test
    public void matchesNamesWithAndWithoutTag() {
        assertTrue(ConcurrencyBenchmarkService.matches("llama3:latest", "llama3"));
        assertTrue(ConcurrencyBenchmarkService.matches("llama3:8b", "LLAMA3:8b"));
        assertFalse(ConcurrencyBenchmarkService.matches("llama3:8b", "llama3"));
    }

    @Test
    public void measuresAnEmbeddingModelWithParallelEmbedRequests() throws Exception {
        FakeApi api = new FakeApi();
        api.capabilities = Collections.singletonList("embedding");
        MemoryHistory history = new MemoryHistory();
        ConcurrencyBenchmarkService service = new ConcurrencyBenchmarkService(api, null, history);
        final List<String> phases = new ArrayList<String>();
        BenchmarkReport report = service.run(ENDPOINT, new ConcurrencyBenchmarkRequest("m",
            Arrays.asList(1, 4), 2048, 40, 1, null, true), null, new ConcurrencyBenchmarkService.Listener() {
                @Override
                public void phase(String text, double fraction) {
                    phases.add(text);
                }

                @Override
                public void stageFinished(StageResult stage) {
                }
            }, CancellationToken.none());

        assertTrue(report.embedding());
        assertEquals(0, api.generateCalls.get());
        // One warm-up plus one request per parallel slot of each stage.
        assertEquals(1 + 1 + 4, api.embedCalls.get());
        assertEquals(40, api.embedInputs.get(1).split(" ").length);
        assertTrue(phases.toString(), phases.contains("4 embeddings at once"));
        assertEquals(2, report.stages().size());
        StageResult four = report.stages().get(1);
        assertEquals(4, four.requests());
        assertEquals(0, four.failures());
        // Four inputs of 40 tokens each went through in the stage's wall time; input tokens count, not generated.
        assertTrue(String.valueOf(four.aggregateTps()), four.aggregateTps() > 0);
        assertTrue(String.valueOf(four.perChatTpsAvg()), four.perChatTpsAvg() > 0);
        assertEquals(300, report.loadMillis());
        assertEquals(1, history.saved.size());
        assertTrue(history.saved.get(0).embedding());
    }

    @Test
    public void switchesToEmbedWhenTheServerRejectsGeneration() throws Exception {
        FakeApi api = new FakeApi();
        api.capabilities = null;
        api.generateUnsupported = true;
        ConcurrencyBenchmarkService service = new ConcurrencyBenchmarkService(api, null, null);
        BenchmarkReport report = service.run(ENDPOINT, request(2), null, NO_LISTENER, CancellationToken.none());
        assertTrue(report.embedding());
        assertEquals(1, api.generateCalls.get());
        assertEquals(1 + 2, api.embedCalls.get());
        assertEquals(2, report.stages().get(0).requests());
    }

    @Test
    public void aChatModelWithBothCapabilitiesStillChats() throws Exception {
        FakeApi api = new FakeApi();
        api.capabilities = Arrays.asList("completion", "embedding", "tools");
        ConcurrencyBenchmarkService service = new ConcurrencyBenchmarkService(api, null, null);
        BenchmarkReport report = service.run(ENDPOINT, request(1), null, NO_LISTENER, CancellationToken.none());
        assertFalse(report.embedding());
        assertEquals(0, api.embedCalls.get());
        assertEquals(2, api.generateCalls.get());
    }

    @Test
    public void loadFailureNamesTheContextOnlyWhenItCanBeTheCause() {
        ConcurrencyBenchmarkRequest withContext = new ConcurrencyBenchmarkRequest("m", Arrays.asList(1), 4096, 50, 1,
            null, true);
        String rejected = ConcurrencyBenchmarkService.loadFailure(withContext,
            new OperationException(ErrorKind.HTTP_ERROR, "HTTP 400: \"m\" does not support embeddings"));
        assertTrue(rejected, rejected.startsWith("loading m with num_ctx 4096 failed: HTTP 400"));
        assertFalse(rejected, rejected.contains("context size"));
        String memory = ConcurrencyBenchmarkService.loadFailure(withContext,
            new OperationException(ErrorKind.HTTP_ERROR, "HTTP 500: out of memory"));
        assertTrue(memory, memory.endsWith("Try a smaller context size"));
        String defaults = ConcurrencyBenchmarkService.loadFailure(request(1),
            new OperationException(ErrorKind.HTTP_ERROR, "HTTP 500: out of memory"));
        assertTrue(defaults, defaults.contains("Set a context size in the benchmark settings"));
    }

    @Test
    public void embeddingInputHasAboutTheRequestedNumberOfTokens() {
        assertEquals(128, ConcurrencyBenchmarkService.embeddingInput(128).split(" ").length);
        assertEquals(1, ConcurrencyBenchmarkService.embeddingInput(0).split(" ").length);
        assertTrue(ConcurrencyBenchmarkService.isEmbeddingOnly(Arrays.asList("embedding")));
        assertFalse(ConcurrencyBenchmarkService.isEmbeddingOnly(Arrays.asList("completion", "embedding")));
        assertFalse(ConcurrencyBenchmarkService.isEmbeddingOnly(Collections.<String>emptyList()));
        assertFalse(ConcurrencyBenchmarkService.isEmbeddingOnly(null));
    }
}
