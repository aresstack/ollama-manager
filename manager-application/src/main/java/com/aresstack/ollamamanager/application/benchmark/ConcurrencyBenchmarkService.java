package com.aresstack.ollamamanager.application.benchmark;

import com.aresstack.ollamamanager.application.common.CancellationToken;
import com.aresstack.ollamamanager.application.common.OperationCancelledException;
import com.aresstack.ollamamanager.application.common.OperationException;
import com.aresstack.ollamamanager.application.port.GpuTelemetryPort;
import com.aresstack.ollamamanager.application.port.OllamaApiPort;
import com.aresstack.ollamamanager.domain.benchmark.GenerationMetrics;
import com.aresstack.ollamamanager.domain.hardware.GpuUsageSample;
import com.aresstack.ollamamanager.domain.model.LoadedModel;
import com.aresstack.ollamamanager.domain.model.ModelOptions;
import com.aresstack.ollamamanager.domain.server.Endpoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Finds out how many chats one model can serve at the same time. For each level it starts that many chats
 * simultaneously, measures per-chat speed, total throughput, time to first token, latency, errors, VRAM and
 * whether the model stays on the GPU. The optimum is the stage with the highest total throughput.
 */
public final class ConcurrencyBenchmarkService {

    /** Keep the model loaded between stages so that load time is measured once. */
    static final String KEEP_ALIVE = "10m";
    static final long TELEMETRY_INTERVAL_MILLIS = 500;

    /** Receives progress on the calling job's thread or a worker thread. */
    public interface Listener {
        void phase(String text, double fraction);

        void stageFinished(StageResult stage);
    }

    private final OllamaApiPort api;
    private final GpuTelemetryPort telemetry;
    private final BenchmarkHistoryPort history;

    /**
     * @param telemetry may be {@code null}; VRAM is then reported as unknown
     * @param history   may be {@code null}; reports are then not stored
     */
    public ConcurrencyBenchmarkService(OllamaApiPort api, GpuTelemetryPort telemetry, BenchmarkHistoryPort history) {
        if (api == null) {
            throw new IllegalArgumentException("api must not be null");
        }
        this.api = api;
        this.telemetry = telemetry;
        this.history = history;
    }

    /**
     * Run the sweep. A cancelled run still returns and stores the stages finished so far, marked incomplete;
     * it throws {@link OperationCancelledException} only when no stage finished.
     */
    public BenchmarkReport run(Endpoint endpoint, ConcurrencyBenchmarkRequest request, String serverNumParallel,
                               Listener listener, CancellationToken cancellation) throws OperationException {
        long started = System.currentTimeMillis();
        ModelOptions options = new ModelOptions(request.numCtx(), request.maxTokens(), null, null);
        int totalSteps = request.levels().size() + 1;

        listener.phase("Loading " + request.model(), 0);
        GenerationMetrics warmup = api.generate(endpoint, request.model(), "Hi",
            new ModelOptions(request.numCtx(), 1, null, null), KEEP_ALIVE, cancellation);

        List<StageResult> stages = new ArrayList<StageResult>();
        boolean complete = true;
        int step = 1;
        for (int level : request.levels()) {
            if (cancellation.isCancelled()) {
                complete = false;
                break;
            }
            listener.phase(level + (level == 1 ? " chat" : " chats at once"), (double) step / totalSteps);
            StageResult stage = runStage(endpoint, request, options, level, cancellation);
            if (cancellation.isCancelled()) {
                complete = false;
                break;
            }
            stages.add(stage);
            listener.stageFinished(stage);
            step++;
            if (request.stopWhenUnstable() && !stage.isStable()) {
                complete = false;
                break;
            }
        }
        if (stages.isEmpty() && cancellation.isCancelled()) {
            throw new OperationCancelledException("benchmark cancelled");
        }
        BenchmarkReport report = new BenchmarkReport(UUID.randomUUID().toString(), started, request.model(),
            request.numCtx(), request.maxTokens(), serverNumParallel, warmup.loadMillis(), stages,
            complete);
        if (history != null) {
            history.save(report);
        }
        listener.phase("Done", 1);
        return report;
    }

    private StageResult runStage(final Endpoint endpoint, final ConcurrencyBenchmarkRequest request,
                                 final ModelOptions options, int level, final CancellationToken cancellation) {
        final List<GenerationMetrics> results = Collections.synchronizedList(new ArrayList<GenerationMetrics>());
        final List<String> errors = Collections.synchronizedList(new ArrayList<String>());
        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(level);
        final AtomicLong peakVram = new AtomicLong(-1);
        // Stage end is the moment the last chat finished, independent of how long a telemetry sample blocks.
        final AtomicLong lastFinishNanos = new AtomicLong(0);
        List<Thread> threads = new ArrayList<Thread>();
        for (int i = 0; i < level; i++) {
            Thread thread = new Thread(() -> {
                try {
                    start.await();
                    for (int round = 0; round < request.roundsPerChat() && !cancellation.isCancelled(); round++) {
                        results.add(api.generate(endpoint, request.model(), request.prompt(), options, KEEP_ALIVE,
                            cancellation));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (OperationCancelledException e) {
                    // Cancelled runs report no error.
                } catch (Exception e) {
                    if (!cancellation.isCancelled()) {
                        errors.add(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
                    }
                } finally {
                    long now = System.nanoTime();
                    long seen;
                    do {
                        seen = lastFinishNanos.get();
                    } while (now - seen > 0 && !lastFinishNanos.compareAndSet(seen, now));
                    done.countDown();
                }
            }, "benchmark-chat-" + i);
            thread.setDaemon(true);
            threads.add(thread);
            thread.start();
        }
        long begin = System.nanoTime();
        start.countDown();
        try {
            while (!done.await(TELEMETRY_INTERVAL_MILLIS, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                samplePeak(peakVram, cancellation);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            cancellation.cancel();
        }
        long end = lastFinishNanos.get() == 0 ? System.nanoTime() : lastFinishNanos.get();
        long wallMillis = Math.max(0, (end - begin) / 1_000_000L);
        samplePeak(peakVram, cancellation);
        Double gpuShare = gpuShare(endpoint, request.model(), cancellation);
        return summarize(level, results, errors, wallMillis, peakVram.get() < 0 ? null : peakVram.get(), gpuShare);
    }

    static StageResult summarize(int level, List<GenerationMetrics> results, List<String> errors, long wallMillis,
                                 Long peakVram, Double gpuShare) {
        List<GenerationMetrics> copy;
        synchronized (results) {
            copy = new ArrayList<GenerationMetrics>(results);
        }
        double sum = 0;
        double min = copy.isEmpty() ? 0 : Double.MAX_VALUE;
        long tokens = 0;
        List<Long> ttft = new ArrayList<Long>();
        List<Long> latency = new ArrayList<Long>();
        for (GenerationMetrics metrics : copy) {
            double tps = metrics.generationTokensPerSecond();
            sum += tps;
            min = Math.min(min, tps);
            tokens += metrics.evalTokens();
            ttft.add(metrics.ttftMillis());
            latency.add(metrics.wallMillis());
        }
        Collections.sort(ttft);
        Collections.sort(latency);
        double aggregate = wallMillis <= 0 ? 0 : tokens * 1000.0 / wallMillis;
        String firstError;
        synchronized (errors) {
            firstError = errors.isEmpty() ? null : errors.get(0);
        }
        return new StageResult(level, copy.size() + errors.size(), errors.size(),
            copy.isEmpty() ? 0 : sum / copy.size(), min, aggregate, percentile(ttft, 50), percentile(ttft, 95),
            percentile(latency, 50), percentile(latency, 95), wallMillis, peakVram, gpuShare, firstError);
    }

    static long percentile(List<Long> sorted, int percent) {
        if (sorted.isEmpty()) {
            return 0;
        }
        int index = (int) Math.ceil(percent / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(sorted.size() - 1, index)));
    }

    private void samplePeak(AtomicLong peak, CancellationToken cancellation) {
        if (telemetry == null) {
            return;
        }
        try {
            long used = 0;
            boolean known = false;
            for (GpuUsageSample sample : telemetry.sample(cancellation)) {
                if (sample.usedVramBytes() != null) {
                    used += sample.usedVramBytes();
                    known = true;
                }
            }
            if (known && used > peak.get()) {
                peak.set(used);
            }
        } catch (OperationException e) {
            // Telemetry is optional; a failed sample leaves VRAM unknown.
        }
    }

    private Double gpuShare(Endpoint endpoint, String model, CancellationToken cancellation) {
        try {
            for (LoadedModel loaded : api.loadedModels(endpoint, cancellation)) {
                if (matches(loaded.identity().name(), model)) {
                    return loaded.gpuShare();
                }
            }
            // The model was unloaded during the stage: it no longer fits.
            return 0d;
        } catch (OperationException e) {
            return null;
        }
    }

    static boolean matches(String loaded, String requested) {
        if (loaded == null) {
            return false;
        }
        String a = loaded.contains(":") ? loaded : loaded + ":latest";
        String b = requested.contains(":") ? requested : requested + ":latest";
        return a.equalsIgnoreCase(b);
    }
}
