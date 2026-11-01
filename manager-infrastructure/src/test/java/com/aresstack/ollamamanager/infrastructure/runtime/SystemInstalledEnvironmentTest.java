package com.aresstack.ollamamanager.infrastructure.runtime;

import com.aresstack.ollamamanager.application.common.CancellationToken;
import com.aresstack.ollamamanager.infrastructure.hardware.CommandResult;
import org.junit.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class SystemInstalledEnvironmentTest {

    @Test
    public void windowsUserAndSystemValuesWinOverTheProcessEnvironment() throws Exception {
        Map<String, String> process = new LinkedHashMap<String, String>();
        process.put("OLLAMA_MODELS", "C:\\old");
        process.put("Ollama_Debug", "1");
        process.put("PATH", "C:\\Windows");
        SystemInstalledEnvironment environment = new SystemInstalledEnvironment(
            (command, timeout, cancellation) -> CommandResult.completed(0,
                "{\"OLLAMA_MODELS\":\"X:\\\\ollama\\\\models\",\"OLLAMA_NUM_PARALLEL\":\"2\"}"), true, process);

        Map<String, String> variables = environment.ollamaVariables(CancellationToken.none());

        assertEquals("X:\\ollama\\models", variables.get("OLLAMA_MODELS"));
        assertEquals("2", variables.get("OLLAMA_NUM_PARALLEL"));
        assertEquals("1", variables.get("OLLAMA_DEBUG"));
        assertFalse(variables.containsKey("PATH"));
    }

    @Test
    public void unreadableWindowsEnvironmentFallsBackToTheProcess() throws Exception {
        SystemInstalledEnvironment environment = new SystemInstalledEnvironment(
            (command, timeout, cancellation) -> CommandResult.notFound(), true,
            Collections.singletonMap("OLLAMA_MODELS", "D:\\m"));

        assertEquals("D:\\m", environment.ollamaVariables(CancellationToken.none()).get("OLLAMA_MODELS"));
    }

    @Test
    public void parsesSystemdEnvironmentWithQuotes() {
        Map<String, String> values = SystemInstalledEnvironment.parseSystemdEnvironment(
            "PATH=/usr/bin OLLAMA_HOST=0.0.0.0 \"OLLAMA_MODELS=/data/my models\"\n");

        assertEquals("0.0.0.0", values.get("OLLAMA_HOST"));
        assertEquals("/data/my models", values.get("OLLAMA_MODELS"));
        assertEquals(2, values.size());
    }
}
