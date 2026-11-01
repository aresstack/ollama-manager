package com.aresstack.ollamamanager.infrastructure.persistence;

import com.aresstack.ollamamanager.application.benchmark.BenchmarkHistoryPort;
import com.aresstack.ollamamanager.application.benchmark.BenchmarkReport;
import com.aresstack.ollamamanager.application.benchmark.StageResult;
import com.aresstack.ollamamanager.application.common.ErrorKind;
import com.aresstack.ollamamanager.application.common.OperationException;
import com.aresstack.ollamamanager.infrastructure.json.Json;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Stores each benchmark report as one JSON file in {@code <data>/benchmarks}.
 */
public final class JsonBenchmarkHistory implements BenchmarkHistoryPort {

    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9-]{1,64}");

    private final File directory;

    public JsonBenchmarkHistory(File dataDirectory) {
        this.directory = new File(dataDirectory, "benchmarks");
    }

    @Override
    public synchronized void save(BenchmarkReport report) throws OperationException {
        try {
            new AtomicFileStore(file(report.id())).write(Json.write(toMap(report)), false);
        } catch (IOException e) {
            throw new OperationException(ErrorKind.IO, "cannot save benchmark: " + e.getMessage(), e);
        }
    }

    @Override
    public synchronized List<BenchmarkReport> list() {
        List<BenchmarkReport> reports = new ArrayList<BenchmarkReport>();
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null) {
            return reports;
        }
        for (File file : files) {
            try {
                String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
                reports.add(fromMap(asMap(Json.parse(text))));
            } catch (IOException | RuntimeException e) {
                // Skip unreadable or foreign files instead of hiding all other runs.
            }
        }
        reports.sort((a, b) -> Long.compare(b.startedAtEpochMillis(), a.startedAtEpochMillis()));
        return reports;
    }

    @Override
    public synchronized void delete(String id) throws OperationException {
        File file = file(id);
        if (file.exists() && !file.delete()) {
            throw new OperationException(ErrorKind.IO, "cannot delete benchmark " + id);
        }
    }

    private File file(String id) {
        if (id == null || !SAFE_ID.matcher(id).matches()) {
            throw new IllegalArgumentException("invalid benchmark id: " + id);
        }
        return new File(directory, id + ".json");
    }

    static Map<String, Object> toMap(BenchmarkReport report) {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put("format", 1L);
        map.put("id", report.id());
        map.put("startedAt", report.startedAtEpochMillis());
        map.put("model", report.model());
        map.put("numCtx", report.numCtx() == null ? null : report.numCtx().longValue());
        map.put("maxTokens", (long) report.maxTokens());
        map.put("serverNumParallel", report.serverNumParallel());
        map.put("loadMillis", report.loadMillis());
        map.put("complete", report.complete());
        List<Object> stages = new ArrayList<Object>();
        for (StageResult stage : report.stages()) {
            Map<String, Object> s = new LinkedHashMap<String, Object>();
            s.put("parallel", (long) stage.parallel());
            s.put("requests", (long) stage.requests());
            s.put("failures", (long) stage.failures());
            s.put("perChatTpsAvg", stage.perChatTpsAvg());
            s.put("perChatTpsMin", stage.perChatTpsMin());
            s.put("aggregateTps", stage.aggregateTps());
            s.put("ttftP50", stage.ttftP50Millis());
            s.put("ttftP95", stage.ttftP95Millis());
            s.put("latencyP50", stage.latencyP50Millis());
            s.put("latencyP95", stage.latencyP95Millis());
            s.put("wallMillis", stage.wallMillis());
            s.put("peakVramBytes", stage.peakVramBytes());
            s.put("gpuShare", stage.gpuShare());
            s.put("firstError", stage.firstError());
            stages.add(s);
        }
        map.put("stages", stages);
        return map;
    }

    static BenchmarkReport fromMap(Map<String, Object> map) {
        List<StageResult> stages = new ArrayList<StageResult>();
        Object rawStages = map.get("stages");
        if (rawStages instanceof List) {
            for (Object raw : (List<?>) rawStages) {
                Map<String, Object> s = asMap(raw);
                Number vram = (Number) s.get("peakVramBytes");
                Number share = (Number) s.get("gpuShare");
                stages.add(new StageResult(num(s, "parallel").intValue(), num(s, "requests").intValue(),
                    num(s, "failures").intValue(), num(s, "perChatTpsAvg").doubleValue(),
                    num(s, "perChatTpsMin").doubleValue(), num(s, "aggregateTps").doubleValue(),
                    num(s, "ttftP50").longValue(), num(s, "ttftP95").longValue(), num(s, "latencyP50").longValue(),
                    num(s, "latencyP95").longValue(), num(s, "wallMillis").longValue(),
                    vram == null ? null : vram.longValue(), share == null ? null : share.doubleValue(),
                    (String) s.get("firstError")));
            }
        }
        Number numCtx = (Number) map.get("numCtx");
        return new BenchmarkReport((String) map.get("id"), num(map, "startedAt").longValue(),
            (String) map.get("model"), numCtx == null ? null : numCtx.intValue(), num(map, "maxTokens").intValue(),
            (String) map.get("serverNumParallel"), num(map, "loadMillis").longValue(), stages, Boolean.TRUE.equals(map.get("complete")));
    }

    private static Number num(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (!(value instanceof Number)) {
            throw new IllegalArgumentException("missing number " + key);
        }
        return (Number) value;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        if (!(value instanceof Map)) {
            throw new IllegalArgumentException("expected object");
        }
        return (Map<String, Object>) value;
    }
}
