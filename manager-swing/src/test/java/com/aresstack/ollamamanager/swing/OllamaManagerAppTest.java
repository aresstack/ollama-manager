package com.aresstack.ollamamanager.swing;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class OllamaManagerAppTest {

    @Test
    public void smokeReportShowsLoopbackDefault() {
        String report = OllamaManagerApp.smokeTestReport();

        assertTrue(report.startsWith("Ollama Manager smoke test OK"));
        assertTrue(report.contains("OLLAMA_HOST=127.0.0.1:11434"));
    }
}
