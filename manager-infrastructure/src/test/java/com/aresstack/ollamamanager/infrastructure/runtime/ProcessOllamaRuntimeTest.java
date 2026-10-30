package com.aresstack.ollamamanager.infrastructure.runtime;

import com.aresstack.ollamamanager.application.common.CancellationToken;
import com.aresstack.ollamamanager.application.common.ErrorKind;
import com.aresstack.ollamamanager.application.common.OperationException;
import com.aresstack.ollamamanager.application.port.ManagedServer;
import com.aresstack.ollamamanager.domain.server.Endpoint;
import com.aresstack.ollamamanager.domain.server.InstallationKind;
import com.aresstack.ollamamanager.domain.server.OllamaInstallation;
import com.aresstack.ollamamanager.domain.server.ServerSettingCatalog;
import com.aresstack.ollamamanager.domain.server.ServerSettings;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ProcessOllamaRuntimeTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private final Deque<ProbeResult> probes = new ArrayDeque<ProbeResult>();
    private final List<ProcessBuilder> launched = new ArrayList<ProcessBuilder>();
    private final Deque<FakeProcess> processes = new ArrayDeque<FakeProcess>();
    private final FakeTicker ticker = new FakeTicker();
    private ProbeResult lastProbe = ProbeResult.free();
    private Runnable onProbe = () -> { };
    private File binaryDirectory;
    private OllamaInstallation installation;
    private ProcessOllamaRuntime runtime;

    @Before
    public void setUp() throws IOException {
        binaryDirectory = folder.newFolder("bin");
        assertTrue(new File(binaryDirectory, "ollama").createNewFile());
        installation = new OllamaInstallation(InstallationKind.MANAGED_INSTALLED, "0.12.3", binaryDirectory,
            Endpoint.DEFAULT);
        runtime = runtime(null, Collections.<String, String>emptyMap());
    }

    private ProcessOllamaRuntime runtime(File portableRoot, Map<String, String> environment) {
        EndpointProbe probe = (endpoint, timeout) -> {
            onProbe.run();
            if (!probes.isEmpty()) {
                lastProbe = probes.removeFirst();
            }
            return lastProbe;
        };
        ProcessStarter starter = builder -> {
            launched.add(builder);
            if (processes.isEmpty()) {
                throw new IOException("no fake process left");
            }
            return processes.removeFirst();
        };
        return new ProcessOllamaRuntime(portableRoot, starter, probe, ticker, environment, false, 5000, 100, 50, 5);
    }

    @Test
    public void startsServeWithArgumentVectorAndManagedEnvironment() throws Exception {
        FakeProcess process = new FakeProcess(false);
        processes.add(process);
        probes.addAll(Arrays.asList(ProbeResult.free(), ProbeResult.free(), ProbeResult.ollama("0.12.3")));
        ServerSettings settings = ServerSettings.defaults().with(ServerSettingCatalog.PARALLELISM, 2);

        ManagedServer server = runtime.start(installation, settings, CancellationToken.none());

        ProcessBuilder builder = launched.get(0);
        assertEquals(Arrays.asList(new File(binaryDirectory, "ollama").getAbsolutePath(), "serve"), builder.command());
        assertEquals(binaryDirectory, builder.directory());
        assertEquals("2", builder.environment().get("OLLAMA_NUM_PARALLEL"));
        assertEquals("127.0.0.1:11434", builder.environment().get("OLLAMA_HOST"));
        assertTrue(server.isAlive());
        assertEquals(settings, server.appliedSettings());
        assertEquals(1, runtime.runningCount());
        process.exit(0);
    }

    @Test
    public void refusesOccupiedEndpointWithoutStartingAProcess() {
        probes.add(ProbeResult.ollama("0.11.0"));
        assertStartFails(ErrorKind.PORT_IN_USE);
        probes.add(ProbeResult.foreign("HTTP 404"));
        assertStartFails(ErrorKind.PORT_IN_USE);
        assertTrue(launched.isEmpty());
    }

    @Test
    public void killsProcessWhenStartupTimesOut() {
        FakeProcess process = new FakeProcess(false);
        processes.add(process);

        assertStartFails(ErrorKind.TIMEOUT);

        assertFalse(process.isAlive());
        assertEquals(0, runtime.runningCount());
    }

    @Test
    public void reportsBindConflictWhenProcessExitsDuringStartup() {
        FakeProcess process = new FakeProcess(false);
        processes.add(process);
        process.print("Error: listen tcp 127.0.0.1:11434: bind: address already in use");
        onProbe = () -> {
            if (launched.size() == 1) {
                process.exit(1);
            }
        };

        assertStartFails(ErrorKind.PORT_IN_USE);
        assertEquals(0, runtime.runningCount());
    }

    @Test
    public void reportsCrashDuringStartupWithExitCodeAndLog() {
        FakeProcess process = new FakeProcess(false);
        processes.add(process);
        process.print("panic: unsupported GPU");
        onProbe = () -> {
            if (launched.size() == 1) {
                process.exit(2);
            }
        };

        OperationException failure = assertStartFails(ErrorKind.PROCESS_FAILED);

        assertTrue(failure.getMessage(), failure.getMessage().contains("code 2"));
        assertTrue(failure.getMessage(), failure.getMessage().contains("panic: unsupported GPU"));
    }

    @Test
    public void cancellationDuringStartupDestroysProcess() {
        FakeProcess process = new FakeProcess(true);
        processes.add(process);
        CancellationToken token = new CancellationToken();
        onProbe = () -> {
            if (launched.size() == 1) {
                token.cancel();
            }
        };

        try {
            runtime.start(installation, ServerSettings.defaults(), token);
            fail("expected cancellation");
        } catch (OperationException e) {
            assertEquals(ErrorKind.CANCELLED, e.kind());
        }
        assertFalse(process.isAlive());
        assertEquals(0, runtime.runningCount());
    }

    @Test
    public void cancellationDuringSuccessfulProbeStillCancels() {
        FakeProcess process = new FakeProcess(true) {
            @Override
            public Process destroyForcibly() {
                forcedDestroyCalls++;
                return this;
            }
        };
        processes.add(process);
        probes.addAll(Arrays.asList(ProbeResult.free(), ProbeResult.ollama("0.12.3")));
        CancellationToken token = new CancellationToken();
        onProbe = () -> {
            if (launched.size() == 1) {
                token.cancel();
            }
        };

        try {
            runtime.start(installation, ServerSettings.defaults(), token);
            fail("expected cancellation");
        } catch (OperationException e) {
            assertEquals(ErrorKind.CANCELLED, e.kind());
        }
        assertTrue(process.forcedDestroyCalls > 0);
        process.exit(137);
    }

    @Test
    public void laterCancellationDoesNotKillAStartedServer() throws Exception {
        FakeProcess process = new FakeProcess(false);
        processes.add(process);
        probes.addAll(Arrays.asList(ProbeResult.free(), ProbeResult.ollama("0.12.3")));
        CancellationToken token = new CancellationToken();

        ManagedServer server = runtime.start(installation, ServerSettings.defaults(), token);
        token.cancel();

        assertTrue(server.isAlive());
        process.exit(0);
    }

    @Test
    public void stopsGracefullyAndForcesWhenGraceExpires() throws Exception {
        FakeProcess graceful = startedProcess(false);
        ManagedServer first = runtime.start(installation, ServerSettings.defaults(), CancellationToken.none());
        runtime.stop(first, 1000);
        assertEquals(0, graceful.forcedDestroyCalls);
        assertEquals(Integer.valueOf(143), first.exitCode());

        FakeProcess stubborn = startedProcess(true);
        ManagedServer second = runtime.start(installation, ServerSettings.defaults(), CancellationToken.none());
        runtime.stop(second, 10);
        assertEquals(1, stubborn.destroyCalls);
        assertEquals(1, stubborn.forcedDestroyCalls);
        assertFalse(second.isAlive());
        assertEquals(0, runtime.runningCount());

        runtime.stop(second, 10);
        assertEquals(1, stubborn.forcedDestroyCalls);
    }

    @Test
    public void refusesToStopHandlesItDidNotCreate() throws Exception {
        startedProcess(false);
        ManagedServer server = runtime.start(installation, ServerSettings.defaults(), CancellationToken.none());
        ProcessOllamaRuntime otherManager = runtime(null, Collections.<String, String>emptyMap());
        ManagedServer forged = new ManagedServer() {
            @Override
            public OllamaInstallation installation() {
                return installation;
            }

            @Override
            public ServerSettings appliedSettings() {
                return ServerSettings.defaults();
            }

            @Override
            public boolean isAlive() {
                return true;
            }

            @Override
            public Integer exitCode() {
                return null;
            }
        };

        assertStopRefused(otherManager, server);
        assertStopRefused(runtime, forged);
        assertTrue(server.isAlive());
        assertTrue(otherManager.recentLog(server).isEmpty());
        runtime.stopAll(10);
        assertFalse(server.isAlive());
    }

    @Test
    public void forgetsCrashedServersWhenStartingAgain() throws Exception {
        FakeProcess crashed = startedProcess(false);
        runtime.start(installation, ServerSettings.defaults(), CancellationToken.none());
        crashed.exit(1);
        FakeProcess replacement = startedProcess(false);

        runtime.start(installation, ServerSettings.defaults(), CancellationToken.none());

        assertEquals(1, runtime.runningCount());
        replacement.exit(0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void refusesToStartExternalInstallation() throws Exception {
        runtime.start(OllamaInstallation.external("0.12.3", Endpoint.DEFAULT), ServerSettings.defaults(),
            CancellationToken.none());
    }

    @Test
    public void keepsOnlyRecentLogLines() throws Exception {
        FakeProcess process = startedProcess(false);
        ManagedServer server = runtime.start(installation, ServerSettings.defaults(), CancellationToken.none());
        for (int i = 0; i < 20; i++) {
            process.print("line " + i);
        }
        process.exit(0);
        long deadline = System.currentTimeMillis() + 5000;
        while (!runtime.recentLog(server).contains("line 19") && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }

        assertEquals(Arrays.asList("line 15", "line 16", "line 17", "line 18", "line 19"), runtime.recentLog(server));
    }

    @Test
    public void discoversValidatedExecutablesAndClassifiesPortableOnes() throws Exception {
        File portableRoot = folder.newFolder("portable");
        File portableBin = new File(portableRoot, "0.12.3");
        assertTrue(portableBin.mkdirs());
        assertTrue(new File(portableBin, "ollama").createNewFile());
        File broken = folder.newFolder("broken");
        assertTrue(new File(broken, "ollama").createNewFile());
        ProcessOllamaRuntime discovering = runtime(portableRoot,
            Collections.singletonMap("path", binaryDirectory.getPath()));
        processes.add(versionProcess("ollama version is 0.12.3"));
        processes.add(versionProcess("not ollama"));
        processes.add(versionProcess("Warning: could not connect to a running Ollama instance",
            "Warning: client version is 0.11.10"));

        List<OllamaInstallation> found = discovering.discover(Arrays.asList(portableBin, broken, binaryDirectory),
            CancellationToken.none());

        assertEquals(2, found.size());
        assertEquals(InstallationKind.MANAGED_PORTABLE, found.get(0).kind());
        assertEquals("0.12.3", found.get(0).version());
        assertEquals(InstallationKind.MANAGED_INSTALLED, found.get(1).kind());
        assertEquals("0.11.10", found.get(1).version());
        assertEquals(Arrays.asList(new File(portableBin, "ollama").getAbsolutePath(), "--version"),
            launched.get(0).command());
        assertEquals(3, launched.size());
    }

    @Test
    public void keepsSymlinkPathThatStartCanLaunch() throws Exception {
        File target = new File(folder.newFolder("versions"), "ollama-v1");
        assertTrue(target.createNewFile());
        File linkDirectory = folder.newFolder("linked");
        try {
            java.nio.file.Files.createSymbolicLink(new File(linkDirectory, "ollama").toPath(), target.toPath());
        } catch (UnsupportedOperationException | IOException e) {
            org.junit.Assume.assumeNoException(e);
        }
        processes.add(versionProcess("ollama version is 0.12.3"));

        List<OllamaInstallation> found = runtime.discover(Collections.singletonList(linkDirectory),
            CancellationToken.none());

        assertEquals(linkDirectory.getAbsoluteFile(), found.get(0).binaryDirectory());
    }

    @Test
    public void reportsInstallationInUseOnlyWhileOwnedServerRuns() throws Exception {
        File otherDirectory = folder.newFolder("other");
        OllamaInstallation other = new OllamaInstallation(InstallationKind.MANAGED_INSTALLED, "0.12.4",
            otherDirectory, Endpoint.DEFAULT);
        assertFalse(runtime.isInUse(installation));

        startedProcess(false);
        ManagedServer server = runtime.start(installation, ServerSettings.defaults(), CancellationToken.none());

        assertTrue(runtime.isInUse(installation));
        assertFalse(runtime.isInUse(other));
        runtime.stop(server, 0);
        assertFalse(runtime.isInUse(installation));
    }

    @Test
    public void parsesVersionOutput() {
        assertEquals("0.12.3", ProcessOllamaRuntime.parseVersion(Collections.singletonList("ollama version is 0.12.3")));
        assertNull(ProcessOllamaRuntime.parseVersion(Collections.singletonList("command not found")));
    }

    private FakeProcess startedProcess(boolean ignoresGracefulStop) {
        FakeProcess process = new FakeProcess(ignoresGracefulStop);
        processes.add(process);
        probes.addAll(Arrays.asList(ProbeResult.free(), ProbeResult.ollama("0.12.3")));
        return process;
    }

    private static FakeProcess versionProcess(String... lines) {
        FakeProcess process = new FakeProcess(false);
        for (String line : lines) {
            process.print(line);
        }
        process.exit(0);
        return process;
    }

    private OperationException assertStartFails(ErrorKind expected) {
        try {
            runtime.start(installation, ServerSettings.defaults(), CancellationToken.none());
            fail("expected " + expected);
            return null;
        } catch (OperationException e) {
            assertEquals(e.getMessage(), expected, e.kind());
            return e;
        }
    }

    private static void assertStopRefused(ProcessOllamaRuntime runtime, ManagedServer server) {
        try {
            runtime.stop(server, 10);
            fail("expected NOT_OWNED");
        } catch (OperationException e) {
            assertEquals(ErrorKind.NOT_OWNED, e.kind());
        }
    }

    private static final class FakeTicker implements Ticker {
        private long now;

        @Override
        public long nowMillis() {
            return now;
        }

        @Override
        public void sleep(long millis) {
            now += millis;
        }
    }
}
