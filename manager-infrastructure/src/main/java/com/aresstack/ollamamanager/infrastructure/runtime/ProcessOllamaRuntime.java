package com.aresstack.ollamamanager.infrastructure.runtime;

import com.aresstack.ollamamanager.application.common.CancellationToken;
import com.aresstack.ollamamanager.application.common.ErrorKind;
import com.aresstack.ollamamanager.application.common.OperationCancelledException;
import com.aresstack.ollamamanager.application.common.OperationException;
import com.aresstack.ollamamanager.application.installation.InstallationUsage;
import com.aresstack.ollamamanager.application.port.ManagedServer;
import com.aresstack.ollamamanager.application.port.OllamaRuntimePort;
import com.aresstack.ollamamanager.domain.server.Endpoint;
import com.aresstack.ollamamanager.domain.server.InstallationKind;
import com.aresstack.ollamamanager.domain.server.OllamaInstallation;
import com.aresstack.ollamamanager.domain.server.ServerSettingCatalog;
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
public final class ProcessOllamaRuntime implements OllamaRuntimePort, InstallationUsage {

    static final long VERSION_TIMEOUT_MILLIS = 10000;
    static final long FORCE_KILL_WAIT_MILLIS = 5000;
    private static final Pattern CLIENT_VERSION = Pattern.compile("(?i)client version is\\s+v?([0-9][^\\s]*)");
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

    /**
     * {@inheritDoc}
     * <p>
     * The endpoint of a discovered installation is only a placeholder ({@link Endpoint#DEFAULT}); the endpoint a
     * started server really uses comes from {@code ServerSettings.bind()}.
     */
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
        running.removeIf(previous -> !previous.isAlive());
        running.add(server);

        // Whoever flips this first wins: a cancel kills the process, a successful start keeps it.
        AtomicBoolean handedOver = new AtomicBoolean();
        cancellation.onCancel(() -> {
            if (handedOver.compareAndSet(false, true)) {
                process.destroyForcibly();
            }
        });
        boolean ready = false;
        try {
            awaitReady(server, endpoint, pump, cancellation);
            if (!handedOver.compareAndSet(false, true)) {
                throw new OperationCancelledException("server start cancelled");
            }
            ready = true;
            return server;
        } finally {
            if (!ready) {
                handedOver.set(true);
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

    /**
     * Report whether a live server started by this instance runs from the installation's binary directory,
     * regardless of which version is marked active.
     */
    @Override
    public boolean isInUse(OllamaInstallation installation) {
        File directory = installation.binaryDirectory();
        if (directory == null) {
            return false;
        }
        File wanted = BinaryLocator.canonical(directory);
        for (OwnedServer server : running) {
            File used = server.installation().binaryDirectory();
            if (server.isAlive() && used != null && BinaryLocator.canonical(used).equals(wanted)) {
                return true;
            }
        }
        return false;
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
                for (OwnedServer server : running) {
                    if (server.isAlive() && server.installation().endpoint().equals(endpoint)) {
                        throw new OperationException(ErrorKind.PORT_IN_USE,
                            "this manager already runs an Ollama server at " + endpoint);
                    }
                }
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

    /**
     * Request termination, force it after the grace period, and return whether the process is gone. On Windows,
     * Java 8's {@link Process#destroy()} already calls {@code TerminateProcess}, so the grace period has no effect
     * there and the exit code is 1.
     */
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

    /** Compare canonical paths, because Windows may report the same folder by its 8.3 short name. */
    private InstallationKind kindOf(File executable) {
        if (portableRoot != null) {
            for (File parent = BinaryLocator.canonical(executable).getParentFile(); parent != null; parent = parent.getParentFile()) {
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
        // Point the CLI at a closed port so it never reports the version of a server that happens to be running.
        ChildEnvironment.apply(builder.environment(), ServerSettings.defaults()
            .with(ServerSettingCatalog.BIND, new Endpoint("127.0.0.1", 1)));
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

    /** Prefer the client version, because with a reachable server the CLI prints the server's version first. */
    static String parseVersion(List<String> lines) {
        for (String line : lines) {
            Matcher matcher = CLIENT_VERSION.matcher(line);
            if (matcher.find()) {
                return matcher.group(1).toLowerCase(Locale.ROOT);
            }
        }
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
