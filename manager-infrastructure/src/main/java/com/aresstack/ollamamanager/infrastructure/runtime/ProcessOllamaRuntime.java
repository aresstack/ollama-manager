package com.aresstack.ollamamanager.infrastructure.runtime;

import com.aresstack.ollamamanager.application.common.CancellationToken;
import com.aresstack.ollamamanager.application.common.ErrorKind;
import com.aresstack.ollamamanager.application.common.OperationCancelledException;
import com.aresstack.ollamamanager.application.common.OperationException;
import com.aresstack.ollamamanager.application.port.ManagedServer;
import com.aresstack.ollamamanager.application.port.OllamaRuntimePort;
import com.aresstack.ollamamanager.domain.server.Endpoint;
import com.aresstack.ollamamanager.domain.server.InstallationKind;
import com.aresstack.ollamamanager.domain.server.OllamaInstallation;
import com.aresstack.ollamamanager.domain.server.ServerSettings;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Discovers Ollama executables and runs {@code ollama serve} as a child process of the manager.
 * <p>
 * Safety rules: processes are started from an argument vector (never a shell string), the environment is edited
 * only on the child's {@link ProcessBuilder}, and only {@link Process} objects this instance created are ever
 * terminated. Nothing is stopped by name, PID or port.
 */
public final class ProcessOllamaRuntime implements OllamaRuntimePort {

    static final long VERSION_TIMEOUT_MILLIS = 10000;
    static final long FORCE_KILL_WAIT_MILLIS = 5000;
    private static final Pattern VERSION = Pattern.compile("(?i)version is\\s+v?([0-9][^\\s]*)");

    private final File portableRoot;
    private final ProcessStarter starter;
    private final EndpointProbe probe;
    private final Ticker ticker;
    private final BinaryLocator locator;
    private final long startupTimeoutMillis;
    private final long pollIntervalMillis;
    private final int probeTimeoutMillis;
    private final int logLines;
    private final Set<OwnedServer> running = Collections.newSetFromMap(new ConcurrentHashMap<OwnedServer, Boolean>());

    /**
     * @param portableRoot directory holding portable installations; executables below it are
     *                     {@code MANAGED_PORTABLE}, all others {@code MANAGED_INSTALLED}. May be {@code null}.
     */
    public ProcessOllamaRuntime(File portableRoot) {
        this(portableRoot, ProcessStarter.system(), new HttpEndpointProbe(), Ticker.system(), System.getenv(),
            BinaryLocator.isWindows(System.getProperty("os.name")), 60000, 250, 1000, BoundedLog.DEFAULT_MAX_LINES);
    }

    ProcessOllamaRuntime(File portableRoot, ProcessStarter starter, EndpointProbe probe, Ticker ticker,
                         Map<String, String> environment, boolean windows, long startupTimeoutMillis,
                         long pollIntervalMillis, int probeTimeoutMillis, int logLines) {
        this.portableRoot = portableRoot == null ? null : BinaryLocator.canonical(portableRoot);
        this.starter = starter;
        this.probe = probe;
        this.ticker = ticker;
        this.locator = new BinaryLocator(windows, environment);
        this.startupTimeoutMillis = startupTimeoutMillis;
        this.pollIntervalMillis = pollIntervalMillis;
        this.probeTimeoutMillis = probeTimeoutMillis;
        this.logLines = logLines;
    }

    @Override
    public List<OllamaInstallation> discover(List<File> additionalDirectories, CancellationToken cancellation)
        throws OperationException {
        List<OllamaInstallation> result = new ArrayList<OllamaInstallation>();
        for (File executable : locator.candidates(additionalDirectories)) {
            cancellation.throwIfCancelled();
            String version = readVersion(executable, cancellation);
            if (version != null) {
                result.add(new OllamaInstallation(kindOf(executable), version, executable.getParentFile(),
                    Endpoint.DEFAULT));
            }
        }
        return result;
    }

    @Override
    public ManagedServer start(OllamaInstallation installation, ServerSettings settings,
                               CancellationToken cancellation) throws OperationException {
        if (!installation.kind().isStartableByManager()) {
            throw new IllegalArgumentException("external installations cannot be started: " + installation);
        }
        cancellation.throwIfCancelled();
        Endpoint endpoint = settings.bind();
        ensureEndpointFree(endpoint);
        File executable = locator.executableIn(installation.binaryDirectory());
        if (!executable.isFile()) {
            throw new OperationException(ErrorKind.NOT_FOUND, "Ollama executable not found: " + executable);
        }

        ProcessBuilder builder = new ProcessBuilder(executable.getAbsolutePath(), "serve")
            .directory(installation.binaryDirectory())
            .redirectErrorStream(true);
        ChildEnvironment.apply(builder.environment(), settings);
        Process process;
        try {
            process = starter.start(builder);
        } catch (IOException e) {
            throw new OperationException(ErrorKind.PROCESS_FAILED, "cannot start " + executable + ": " + e.getMessage(), e);
        }
        BoundedLog log = new BoundedLog(logLines);
        Thread pump = OutputPump.start(process.getInputStream(), log, "ollama-serve-output " + endpoint);
        OwnedServer server = new OwnedServer(this, installation.withEndpoint(endpoint), settings, process, log);
        running.add(server);

        AtomicBoolean startupFinished = new AtomicBoolean();
        cancellation.onCancel(() -> {
            if (!startupFinished.get()) {
                process.destroyForcibly();
            }
        });
        boolean ready = false;
        try {
            awaitReady(server, endpoint, pump, cancellation);
            ready = true;
            return server;
        } finally {
            startupFinished.set(true);
            if (!ready) {
                terminate(server, 0);
            }
        }
    }

    @Override
    public void stop(ManagedServer server, long gracePeriodMillis) throws OperationException {
        OwnedServer owned = requireOwned(server);
        if (!terminate(owned, Math.max(0, gracePeriodMillis))) {
            throw new OperationException(ErrorKind.PROCESS_FAILED, "Ollama process did not exit after being killed");
        }
    }

    @Override
    public List<String> recentLog(ManagedServer server) {
        if (server instanceof OwnedServer && ((OwnedServer) server).isOwnedBy(this)) {
            return ((OwnedServer) server).log().snapshot();
        }
        return Collections.emptyList();
    }

    /** Stop every server this instance still runs, for example when the application exits. */
    public void stopAll(long gracePeriodMillis) {
        for (OwnedServer server : new ArrayList<OwnedServer>(running)) {
            terminate(server, gracePeriodMillis);
        }
    }

    int runningCount() {
        return running.size();
    }

    private void ensureEndpointFree(Endpoint endpoint) throws OperationException {
        ProbeResult result = probe.probe(endpoint, probeTimeoutMillis);
        switch (result.kind()) {
            case FREE:
                return;
            case OLLAMA:
                throw new OperationException(ErrorKind.PORT_IN_USE,
                    "an Ollama server (version " + result.version() + ") already runs at " + endpoint
                        + "; it is not owned by this manager and will not be stopped");
            default:
                throw new OperationException(ErrorKind.PORT_IN_USE,
                    "another service uses " + endpoint + " (" + result.detail() + ")");
        }
    }

    private void awaitReady(OwnedServer server, Endpoint endpoint, Thread pump, CancellationToken cancellation)
        throws OperationException {
        long deadline = ticker.nowMillis() + startupTimeoutMillis;
        while (true) {
            if (cancellation.isCancelled()) {
                throw new OperationCancelledException("server start cancelled");
            }
            if (!server.process().isAlive()) {
                joinQuietly(pump);
                throw exitedDuringStartup(server);
            }
            ProbeResult result = probe.probe(endpoint, probeTimeoutMillis);
            if (result.kind() == ProbeResult.Kind.OLLAMA && server.process().isAlive()) {
                return;
            }
            if (ticker.nowMillis() >= deadline) {
                throw new OperationException(ErrorKind.TIMEOUT, "Ollama did not answer at " + endpoint + " within "
                    + startupTimeoutMillis + " ms" + server.log().tail(5));
            }
            try {
                ticker.sleep(pollIntervalMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new OperationCancelledException("server start interrupted");
            }
        }
    }

    private static OperationException exitedDuringStartup(OwnedServer server) {
        BoundedLog log = server.log();
        String message = "Ollama exited during startup with code " + server.exitCode() + log.tail(5);
        if (log.containsIgnoreCase("address already in use") || log.containsIgnoreCase("only one usage of each socket")
            || log.containsIgnoreCase("bind:")) {
            return new OperationException(ErrorKind.PORT_IN_USE, message);
        }
        return new OperationException(ErrorKind.PROCESS_FAILED, message);
    }

    /** Request termination, force it after the grace period, and return whether the process is gone. */
    private boolean terminate(OwnedServer server, long gracePeriodMillis) {
        Process process = server.process();
        try {
            if (process.isAlive()) {
                process.destroy();
                if (!waitFor(process, gracePeriodMillis)) {
                    process.destroyForcibly();
                    waitFor(process, FORCE_KILL_WAIT_MILLIS);
                }
            }
            return !process.isAlive();
        } finally {
            if (!process.isAlive()) {
                running.remove(server);
            }
        }
    }

    private static boolean waitFor(Process process, long millis) {
        try {
            return process.waitFor(millis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return !process.isAlive();
        }
    }

    private OwnedServer requireOwned(ManagedServer server) throws OperationException {
        if (server instanceof OwnedServer && ((OwnedServer) server).isOwnedBy(this)) {
            return (OwnedServer) server;
        }
        throw new OperationException(ErrorKind.NOT_OWNED, "server was not started by this manager: " + server);
    }

    private InstallationKind kindOf(File executable) {
        if (portableRoot != null) {
            for (File parent = executable.getParentFile(); parent != null; parent = parent.getParentFile()) {
                if (parent.equals(portableRoot)) {
                    return InstallationKind.MANAGED_PORTABLE;
                }
            }
        }
        return InstallationKind.MANAGED_INSTALLED;
    }

    /** Run {@code ollama --version}; return the version, or {@code null} if the file is not a working Ollama. */
    private String readVersion(File executable, CancellationToken cancellation) throws OperationException {
        ProcessBuilder builder = new ProcessBuilder(Arrays.asList(executable.getPath(), "--version"))
            .directory(executable.getParentFile())
            .redirectErrorStream(true);
        Process process;
        try {
            process = starter.start(builder);
        } catch (IOException e) {
            return null;
        }
        BoundedLog output = new BoundedLog(20);
        Thread pump = OutputPump.start(process.getInputStream(), output, "ollama-version " + executable);
        cancellation.onCancel(process::destroyForcibly);
        try {
            if (!waitFor(process, VERSION_TIMEOUT_MILLIS)) {
                return null;
            }
            joinQuietly(pump);
            cancellation.throwIfCancelled();
            return parseVersion(output.snapshot());
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    static String parseVersion(List<String> lines) {
        for (String line : lines) {
            Matcher matcher = VERSION.matcher(line);
            if (matcher.find()) {
                return matcher.group(1).toLowerCase(Locale.ROOT);
            }
        }
        return null;
    }

    private static void joinQuietly(Thread thread) {
        try {
            thread.join(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
