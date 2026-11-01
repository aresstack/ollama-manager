package com.aresstack.ollamamanager.application.server;

import com.aresstack.ollamamanager.application.common.CancellationToken;
import com.aresstack.ollamamanager.application.common.ErrorKind;
import com.aresstack.ollamamanager.application.common.LifecycleLock;
import com.aresstack.ollamamanager.application.common.OperationCancelledException;
import com.aresstack.ollamamanager.application.common.OperationException;
import com.aresstack.ollamamanager.application.port.ExternalServerControlPort;
import com.aresstack.ollamamanager.application.port.ExternalServerProcess;
import com.aresstack.ollamamanager.application.port.ManagedServer;
import com.aresstack.ollamamanager.application.port.OllamaRuntimePort;
import com.aresstack.ollamamanager.domain.server.Endpoint;
import com.aresstack.ollamamanager.domain.server.InstallationKind;
import com.aresstack.ollamamanager.domain.server.OllamaInstallation;
import com.aresstack.ollamamanager.domain.server.RuntimeOwnership;
import com.aresstack.ollamamanager.domain.server.ServerSettings;
import com.aresstack.ollamamanager.domain.server.ServerState;
import com.aresstack.ollamamanager.domain.server.ServerStatus;

import java.io.File;
import java.util.Collections;
import java.util.List;

/**
 * Serializes start, stop and restart of the one server the manager works with and reports its status.
 * <p>
 * Methods block and must run off the Swing event dispatch thread. Only one transition runs at a time; a second
 * call while starting or stopping fails with {@code INVALID_STATE} instead of queueing. An attached external server
 * can be stopped, or restarted as the manager's own server from its binary, when an
 * {@link ExternalServerControlPort} is wired; without one these calls fail with {@code NOT_OWNED}. Callers must
 * ask the user before stopping or restarting a server.
 */
public final class ServerLifecycleService {

    private final OllamaRuntimePort runtime;
    private final long gracePeriodMillis;
    private final LifecycleLock lock;
    private final ExternalServerControlPort externalControl;
    private final long portReleaseMillis;

    private ServerState transition;
    private ManagedServer owned;
    private OllamaInstallation external;
    private String externalVersion;
    private ServerStatus lastFailure;

    /**
     * Create a service with its own private lock.
     *
     * @deprecated a private lock does not protect against a concurrent uninstall; pass the process-wide
     *             {@link LifecycleLock} that {@code PortableInstallationService} also uses
     */
    @Deprecated
    public ServerLifecycleService(OllamaRuntimePort runtime, long gracePeriodMillis) {
        this(runtime, gracePeriodMillis, new LifecycleLock());
    }

    /**
     * @param lock the process-wide lock shared with uninstall, so that no installation is deleted while a start,
     *             stop or restart runs
     */
    public ServerLifecycleService(OllamaRuntimePort runtime, long gracePeriodMillis, LifecycleLock lock) {
        this(runtime, gracePeriodMillis, lock, null, 0);
    }

    /**
     * @param externalControl   finds and terminates external servers; {@code null} keeps them uncontrolled
     * @param portReleaseMillis how long to wait for the port to become free after an external server was
     *                          terminated
     */
    public ServerLifecycleService(OllamaRuntimePort runtime, long gracePeriodMillis, LifecycleLock lock,
                                  ExternalServerControlPort externalControl, long portReleaseMillis) {
        this.externalControl = externalControl;
        this.portReleaseMillis = Math.max(0, portReleaseMillis);
        if (lock == null) {
            throw new IllegalArgumentException("lock must not be null");
        }
        this.lock = lock;
        if (runtime == null) {
            throw new IllegalArgumentException("runtime must not be null");
        }
        if (gracePeriodMillis < 0) {
            throw new IllegalArgumentException("gracePeriodMillis must not be negative: " + gracePeriodMillis);
        }
        this.runtime = runtime;
        this.gracePeriodMillis = gracePeriodMillis;
    }

    /** Return the current status; an owned process that exited on its own is reported as {@code FAILED}. */
    public synchronized ServerStatus status() {
        if (transition != null) {
            String message = transition == ServerState.STARTING ? "starting" : "stopping";
            if (owned == null && external != null) {
                return new ServerStatus(transition, RuntimeOwnership.EXTERNAL, external, null, message);
            }
            OllamaInstallation installation = owned != null ? owned.installation() : null;
            return new ServerStatus(transition, installation == null ? RuntimeOwnership.NONE : RuntimeOwnership.OWNED,
                installation, null, message);
        }
        if (owned != null) {
            if (owned.isAlive()) {
                return new ServerStatus(ServerState.RUNNING, RuntimeOwnership.OWNED, owned.installation(),
                    owned.installation().version(), "");
            }
            return new ServerStatus(ServerState.FAILED, RuntimeOwnership.OWNED, owned.installation(), null,
                "Ollama exited unexpectedly with code " + owned.exitCode());
        }
        if (external != null) {
            return new ServerStatus(ServerState.RUNNING, RuntimeOwnership.EXTERNAL, external, externalVersion,
                "external server, not started by the manager");
        }
        return lastFailure != null ? lastFailure : ServerStatus.stopped();
    }

    /** @return whether an attached external server can be stopped or restarted by this service */
    public boolean canControlExternal() {
        return externalControl != null;
    }

    /**
     * @return whether the server in {@code status} can be stopped: an owned one, or an external one when
     *         external control is wired
     */
    public boolean canStop(ServerStatus status) {
        if (status == null) {
            return false;
        }
        if (status.ownership() == RuntimeOwnership.EXTERNAL) {
            return externalControl != null && status.state() == ServerState.RUNNING;
        }
        return status.canBeStoppedByManager();
    }

    /**
     * Record a server found running that this manager did not start, for example after a port conflict. It is
     * used through its API; stopping or restarting it needs an explicit call after the user confirmed it.
     */
    public void attachExternal(OllamaInstallation installation, String reportedVersion) throws OperationException {
        lock.run("attach an external server", () -> {
            attachUnderLock(installation, reportedVersion);
            return null;
        });
    }

    private synchronized void attachUnderLock(OllamaInstallation installation, String reportedVersion)
        throws OperationException {
        if (installation.kind() != InstallationKind.EXTERNAL_ATTACHED) {
            throw new IllegalArgumentException("only EXTERNAL_ATTACHED installations can be attached: " + installation);
        }
        requireIdle();
        if (owned != null && owned.isAlive()) {
            throw new OperationException(ErrorKind.INVALID_STATE, "an owned server is running");
        }
        owned = null;
        external = installation;
        externalVersion = reportedVersion;
        lastFailure = null;
    }

    /** Forget an attached external server without touching it. */
    public synchronized void detachExternal() {
        external = null;
        externalVersion = null;
    }

    /** Start an owned server; fails if a server is already running or a transition is in progress. */
    public ServerStatus start(OllamaInstallation installation, ServerSettings settings, CancellationToken cancellation)
        throws OperationException {
        return lock.run("start the server", () -> startUnderLock(installation, settings, cancellation));
    }

    private ServerStatus startUnderLock(OllamaInstallation installation, ServerSettings settings,
                                        CancellationToken cancellation) throws OperationException {
        if (!installation.kind().isStartableByManager()) {
            throw new OperationException(ErrorKind.NOT_OWNED, "external servers cannot be started by the manager");
        }
        synchronized (this) {
            requireIdle();
            if (owned != null && owned.isAlive()) {
                throw new OperationException(ErrorKind.INVALID_STATE, "the managed server is already running");
            }
            if (external != null) {
                throw new OperationException(ErrorKind.PORT_IN_USE,
                    "an external server is attached; detach it before starting an own server");
            }
            owned = null;
            lastFailure = null;
            transition = ServerState.STARTING;
        }
        return doStart(installation, settings, cancellation);
    }

    /**
     * Stop the owned server, or the attached external one when external control is wired. Does nothing when no
     * server runs; fails with {@code NOT_OWNED} for an external server without external control.
     */
    public ServerStatus stop() throws OperationException {
        return lock.run("stop the server", () -> stopUnderLock());
    }

    private ServerStatus stopUnderLock() throws OperationException {
        ManagedServer server;
        synchronized (this) {
            requireIdle();
            if (owned == null) {
                if (external == null) {
                    return status();
                }
                if (externalControl == null) {
                    throw new OperationException(ErrorKind.NOT_OWNED, "the external server is not owned by the manager");
                }
                server = null;
            } else {
                server = owned;
            }
            transition = ServerState.STOPPING;
        }
        if (server == null) {
            try {
                stopExternal(CancellationToken.none());
                synchronized (this) {
                    external = null;
                    externalVersion = null;
                }
            } finally {
                synchronized (this) {
                    transition = null;
                }
            }
            return status();
        }
        try {
            runtime.stop(server, gracePeriodMillis);
            synchronized (this) {
                owned = null;
            }
        } finally {
            synchronized (this) {
                transition = null;
            }
        }
        return status();
    }

    /**
     * Stop the owned server and start it again from the same installation with new settings. An attached external
     * server is stopped and replaced by an own server started from the external server's binary, when external
     * control is wired and the binary is known.
     */
    public ServerStatus restart(ServerSettings settings, CancellationToken cancellation) throws OperationException {
        return lock.run("restart the server", () -> restartUnderLock(settings, cancellation));
    }

    private ServerStatus restartUnderLock(ServerSettings settings, CancellationToken cancellation)
        throws OperationException {
        ManagedServer server;
        synchronized (this) {
            requireIdle();
            if (owned == null && external != null && externalControl != null) {
                transition = ServerState.STOPPING;
                server = null;
            } else if (owned == null) {
                throw new OperationException(external != null ? ErrorKind.NOT_OWNED : ErrorKind.INVALID_STATE,
                    "no owned server to restart");
            } else {
                server = owned;
                transition = ServerState.STOPPING;
            }
        }
        if (server == null) {
            return takeOverExternal(settings, cancellation);
        }
        boolean stopped = false;
        try {
            runtime.stop(server, gracePeriodMillis);
            stopped = true;
        } finally {
            if (!stopped) {
                // Keep the owned handle so the caller can retry, but never stay stuck in STOPPING.
                synchronized (this) {
                    transition = null;
                }
            }
        }
        synchronized (this) {
            owned = null;
            transition = ServerState.STARTING;
        }
        return doStart(server.installation(), settings, cancellation);
    }

    private ServerStatus takeOverExternal(ServerSettings settings, CancellationToken cancellation)
        throws OperationException {
        OllamaInstallation replacement;
        try {
            ExternalServerProcess process = requireOllamaProcess(cancellation);
            File executable = process.executable();
            if (executable == null || executable.getParentFile() == null) {
                throw new OperationException(ErrorKind.NOT_FOUND, "the binary of the external server " + process
                    + " is unknown; stop it and start an installation from Setup instead");
            }
            String version;
            synchronized (this) {
                version = externalVersion;
            }
            replacement = new OllamaInstallation(InstallationKind.MANAGED_INSTALLED,
                version == null ? OllamaInstallation.UNKNOWN_VERSION : version, executable.getParentFile(),
                Endpoint.DEFAULT);
            terminateAndAwaitRelease(process, cancellation);
        } catch (OperationException | RuntimeException e) {
            synchronized (this) {
                transition = null;
            }
            throw e;
        }
        synchronized (this) {
            external = null;
            externalVersion = null;
            transition = ServerState.STARTING;
        }
        return doStart(replacement, settings, cancellation);
    }

    private void stopExternal(CancellationToken cancellation) throws OperationException {
        terminateAndAwaitRelease(requireOllamaProcess(cancellation), cancellation);
    }

    private ExternalServerProcess requireOllamaProcess(CancellationToken cancellation) throws OperationException {
        Endpoint endpoint;
        synchronized (this) {
            endpoint = external.endpoint();
        }
        ExternalServerProcess process = externalControl.find(endpoint, cancellation);
        if (process == null) {
            throw new OperationException(ErrorKind.NOT_FOUND, "no process listening on " + endpoint
                + " was found; it may run as another user or as a service");
        }
        if (!process.isOllama()) {
            throw new OperationException(ErrorKind.NOT_OLLAMA, "the process listening on " + endpoint + " is "
                + process + ", not Ollama; it is not stopped");
        }
        return process;
    }

    /** Terminate and wait until no process listens on the port any more, or a restarted one is detected. */
    private void terminateAndAwaitRelease(ExternalServerProcess process, CancellationToken cancellation)
        throws OperationException {
        Endpoint endpoint;
        synchronized (this) {
            endpoint = external.endpoint();
        }
        externalControl.terminate(process, gracePeriodMillis, cancellation);
        long deadline = System.currentTimeMillis() + portReleaseMillis;
        while (true) {
            cancellation.throwIfCancelled();
            ExternalServerProcess listener = externalControl.find(endpoint, cancellation);
            if (listener == null) {
                return;
            }
            if (listener.pid() != process.pid()) {
                throw new OperationException(ErrorKind.PROCESS_FAILED, "after stopping " + process + ", "
                    + listener + " listens on " + endpoint + " again; it is probably restarted by a service or"
                    + " the Ollama app. Quit it there first.");
            }
            if (System.currentTimeMillis() >= deadline) {
                throw new OperationException(ErrorKind.TIMEOUT, process + " still listens on " + endpoint);
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new OperationCancelledException("interrupted while waiting for " + endpoint);
            }
        }
    }

    /**
     * Return whether applying {@code desired} needs a restart of the owned server. Always {@code false} without an
     * owned server, because nothing is disrupted then.
     */
    public synchronized boolean restartRequired(ServerSettings desired) {
        return owned != null && owned.isAlive() && desired.requiresRestartComparedTo(owned.appliedSettings());
    }

    /** Return the settings of the running owned server, or {@code null}. */
    public synchronized ServerSettings appliedSettings() {
        return owned == null ? null : owned.appliedSettings();
    }

    /** Return recent output of the owned server, or an empty list. */
    public List<String> recentLog() {
        ManagedServer server;
        synchronized (this) {
            server = owned;
        }
        return server == null ? Collections.<String>emptyList() : runtime.recentLog(server);
    }

    private ServerStatus doStart(OllamaInstallation installation, ServerSettings settings,
                                 CancellationToken cancellation) throws OperationException {
        try {
            ManagedServer server = runtime.start(installation, settings, cancellation);
            synchronized (this) {
                owned = server;
                transition = null;
            }
            return status();
        } catch (OperationCancelledException e) {
            synchronized (this) {
                transition = null;
            }
            throw e;
        } catch (OperationException | RuntimeException e) {
            synchronized (this) {
                transition = null;
                lastFailure = new ServerStatus(ServerState.FAILED, RuntimeOwnership.NONE, null, null,
                    String.valueOf(e.getMessage()));
            }
            throw e;
        }
    }

    private void requireIdle() throws OperationException {
        if (transition != null) {
            throw new OperationException(ErrorKind.INVALID_STATE, "server is " + transition.name().toLowerCase(java.util.Locale.ROOT));
        }
    }
}
