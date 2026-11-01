package com.aresstack.ollamamanager.infrastructure.runtime;

import com.aresstack.ollamamanager.application.common.CancellationToken;
import com.aresstack.ollamamanager.infrastructure.hardware.CommandResult;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

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

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private static final String OLD_START = "time=2026-10-30T10:00:00.000+01:00 level=INFO source=routes.go:1300"
        + " msg=\"server config\" env=\"map[CUDA_VISIBLE_DEVICES: OLLAMA_HOST:http://127.0.0.1:11434"
        + " OLLAMA_MODELS:C:\\\\Users\\\\angel\\\\.ollama\\\\models OLLAMA_NUM_PARALLEL:1]\"";
    private static final String NEW_START = "time=2026-11-01T03:00:00.000+01:00 level=INFO source=routes.go:1300"
        + " msg=\"server config\" env=\"map[CUDA_VISIBLE_DEVICES: OLLAMA_FLASH_ATTENTION:true"
        + " OLLAMA_MODELS:X:\\\\Ollama Models\\\\models OLLAMA_NUM_PARALLEL:1"
        + " OLLAMA_ORIGINS:[http://localhost https://localhost] ROCR_VISIBLE_DEVICES:]\"";

    @Test
    public void takesModelFolderFromTheLatestServerStartInTheAppLog() throws Exception {
        File log = folder.newFile("server.log");
        Files.write(log.toPath(), (OLD_START + "\nsome other line\n" + NEW_START + "\n"
            + "time=... msg=\"listening\"\n").getBytes(StandardCharsets.UTF_8));
        SystemInstalledEnvironment environment = new SystemInstalledEnvironment(
            (command, timeout, cancellation) -> CommandResult.completed(0, "{\"OLLAMA_KV_CACHE_TYPE\":\"q8_0\"}"),
            true, Collections.<String, String>emptyMap(), log, new File("C:\\Users\\angel\\.ollama\\models"));

        Map<String, String> variables = environment.ollamaVariables(CancellationToken.none());

        assertEquals("X:\\Ollama Models\\models", variables.get("OLLAMA_MODELS"));
        assertEquals("q8_0", variables.get("OLLAMA_KV_CACHE_TYPE"));
        assertFalse("logged defaults are never taken over", variables.containsKey("OLLAMA_NUM_PARALLEL"));
    }

    @Test
    public void defaultFolderInTheLogOrAConfiguredVariableWins() throws Exception {
        File log = folder.newFile("server.log");
        Files.write(log.toPath(), OLD_START.getBytes(StandardCharsets.UTF_8));
        File defaultFolder = new File("C:\\Users\\angel\\.ollama\\models");
        SystemInstalledEnvironment logOnly = new SystemInstalledEnvironment(
            (command, timeout, cancellation) -> CommandResult.completed(0, "{}"), true,
            Collections.<String, String>emptyMap(), log, defaultFolder);
        assertNull(logOnly.ollamaVariables(CancellationToken.none()).get("OLLAMA_MODELS"));

        Files.write(log.toPath(), NEW_START.getBytes(StandardCharsets.UTF_8));
        SystemInstalledEnvironment configured = new SystemInstalledEnvironment(
            (command, timeout, cancellation) -> CommandResult.completed(0, "{\"OLLAMA_MODELS\":\"D:\\\\m\"}"), true,
            Collections.<String, String>emptyMap(), log, defaultFolder);
        assertEquals("D:\\m", configured.ollamaVariables(CancellationToken.none()).get("OLLAMA_MODELS"));
    }

    @Test
    public void parsesGoConfigMapWithSpacesInValues() {
        Map<String, String> values = SystemInstalledEnvironment.parseConfigMap(
            "map[A: OLLAMA_MODELS:X:\\My Models OLLAMA_ORIGINS:[http://x https://y] Z_1:2]");
        assertEquals("", values.get("A"));
        assertEquals("X:\\My Models", values.get("OLLAMA_MODELS"));
        assertEquals("[http://x https://y]", values.get("OLLAMA_ORIGINS"));
        assertEquals("2", values.get("Z_1"));
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
