package com.aresstack.ollamamanager.infrastructure.persistence;

import com.aresstack.ollamamanager.application.benchmark.BenchmarkReport;
import com.aresstack.ollamamanager.application.benchmark.StageResult;
import com.aresstack.ollamamanager.domain.server.ServerSettingCatalog;
import com.aresstack.ollamamanager.domain.server.ServerSettings;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class JsonBenchmarkHistoryTest {

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void roundTripsReportsNewestFirstAndSkipsBrokenFiles() throws Exception {
        JsonBenchmarkHistory history = new JsonBenchmarkHistory(folder.getRoot());
        ServerSettings measured = ServerSettings.defaults().with(ServerSettingCatalog.PARALLELISM, 4)
            .with(ServerSettingCatalog.FLASH_ATTENTION, Boolean.TRUE);
        BenchmarkReport older = new BenchmarkReport("a1", 1000, "llama3:8b", 4096, 128, "4", measured, 900,
            Arrays.asList(new StageResult(1, 1, 0, 40.5, 40.5, 40.5, 50, 60, 900, 1000, 1200, 8_000_000_000L, 1.0, null),
                new StageResult(4, 4, 1, 20, 15, 70, 80, 300, 1800, 2000, 2500, null, 0.5, "HTTP 503")), false);
        BenchmarkReport newer = new BenchmarkReport("b2", 2000, "phi", null, 64, null, 10,
            Arrays.<StageResult>asList(), true);
        history.save(older);
        history.save(newer);
        Files.write(new File(new File(folder.getRoot(), "benchmarks"), "junk.json").toPath(),
            "{not json".getBytes(StandardCharsets.UTF_8));

        List<BenchmarkReport> loaded = history.list();
        assertEquals(2, loaded.size());
        assertEquals("b2", loaded.get(0).id());
        BenchmarkReport read = loaded.get(1);
        assertEquals("llama3:8b", read.model());
        assertEquals(Integer.valueOf(4096), read.numCtx());
        assertEquals("4", read.serverNumParallel());
        assertEquals(measured, read.serverSettings());
        assertEquals(Boolean.TRUE, read.serverSettings().flashAttention());
        assertNull(loaded.get(0).serverSettings());
        assertEquals(900, read.loadMillis());
        assertEquals(2, read.stages().size());
        StageResult four = read.stages().get(1);
        assertEquals(4, four.parallel());
        assertEquals(1, four.failures());
        assertEquals(70, four.aggregateTps(), 1e-9);
        assertEquals(300, four.ttftP95Millis());
        assertEquals(2000, four.latencyP95Millis());
        assertEquals(2500, four.wallMillis());
        assertNull(four.peakVramBytes());
        assertEquals(Double.valueOf(0.5), four.gpuShare());
        assertEquals("HTTP 503", four.firstError());
        assertEquals(Long.valueOf(8_000_000_000L), read.stages().get(0).peakVramBytes());
        assertNull(loaded.get(0).numCtx());
        assertTrue(loaded.get(0).complete());

        history.delete("a1");
        assertEquals(1, history.list().size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsUnsafeIds() throws Exception {
        new JsonBenchmarkHistory(folder.getRoot()).delete("../x");
    }
}
