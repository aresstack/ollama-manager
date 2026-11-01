package com.aresstack.ollamamanager.infrastructure.ollamaapi;

import com.aresstack.ollamamanager.application.common.CancellationToken;
import com.aresstack.ollamamanager.application.common.OperationException;
import com.aresstack.ollamamanager.domain.benchmark.GenerationMetrics;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class GenerateStreamTest {

    @Test
    public void parsesFinalStatisticsLine() throws Exception {
        String stream = "{\"model\":\"m\",\"response\":\"Hel\",\"done\":false}\n"
            + "{\"model\":\"m\",\"response\":\"lo\",\"done\":false}\n"
            + "{\"model\":\"m\",\"response\":\"\",\"done\":true,\"load_duration\":2000000,\"prompt_eval_count\":7,"
            + "\"prompt_eval_duration\":70000000,\"eval_count\":20,\"eval_duration\":500000000}\n";
        GenerationMetrics metrics = HttpOllamaApiClient.readGenerateStream(
            new ByteArrayInputStream(stream.getBytes(StandardCharsets.UTF_8)), System.nanoTime(),
            CancellationToken.none());
        assertEquals(2, metrics.loadMillis());
        assertEquals(7, metrics.promptTokens());
        assertEquals(20, metrics.evalTokens());
        assertEquals(40, metrics.generationTokensPerSecond(), 1e-9);
        assertEquals(100, metrics.promptTokensPerSecond(), 1e-9);
        assertTrue(metrics.ttftMillis() <= metrics.wallMillis());
    }

    @Test(expected = OperationException.class)
    public void failsOnServerError() throws Exception {
        HttpOllamaApiClient.readGenerateStream(new ByteArrayInputStream(
            "{\"error\":\"model not found\"}\n".getBytes(StandardCharsets.UTF_8)), System.nanoTime(),
            CancellationToken.none());
    }

    @Test(expected = OperationException.class)
    public void failsWhenStreamEndsEarly() throws Exception {
        HttpOllamaApiClient.readGenerateStream(new ByteArrayInputStream(
            "{\"response\":\"x\",\"done\":false}\n".getBytes(StandardCharsets.UTF_8)), System.nanoTime(),
            CancellationToken.none());
    }
}
