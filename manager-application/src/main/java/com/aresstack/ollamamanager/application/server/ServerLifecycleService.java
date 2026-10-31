package com.aresstack.ollamamanager.application.server;

import com.aresstack.ollamamanager.application.common.CancellationToken;
import com.aresstack.ollamamanager.application.common.ErrorKind;
import com.aresstack.ollamamanager.application.common.OperationCancelledException;
import com.aresstack.ollamamanager.application.common.OperationException;
import com.aresstack.ollamamanager.application.port.ManagedServer;
import com.aresstack.ollamamanager.application.port.OllamaRuntimePort;
import com.aresstack.ollamamanager.domain.server.InstallationKind;
import com.aresstack.ollamamanager.domain.server.OllamaInstallation;
import com.aresstack.ollamamanager.domain.server.RuntimeOwnership;
import com.aresstack.ollamamanager.domain.server.ServerSettings;
import com.aresstack.ollamamanager.domain.server.ServerState;
import com.aresstack.ollamamanager.domain.server.ServerStatus;

import java.util.Collections;
import java.util.List;

/**
 * Serializes start, stop and restart of the one server the manager works with and reports its status.
 * <p>
 * Methods block and must run off the Swing event dispatch thread. Only one transition runs at a time; a second
 * call while starting or stopping fails with {@code INVALID_STATE} instead of queueing. External servers are only
 * observed: stopping or restarting them fails with {@code NOT_OWNED}.
 */
public final class ServerLifecycleService {

    private final OllamaRuntimePort runtime;
    private final long gracePeriodMillis;

    private ServerState transition;
    private ManagedServer owned;
    private OllamaInstallation external;
    private String externalVersion;
    private ServerStatus lastFailure;

    public ServerLifecycleService(OllamaRuntimePort runtime, long gracePeriodMillis) {
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
            OllamaInstallation installation = owned != null ? owned.installation() : null;
            return new ServerStatus(transition, installation == null ? RuntimeOwnership.NONE : RuntimeOwnership.OWNED,
                installation, null, transition == ServerState.STARTING ? "starting" : "stopping");
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
                "external server; the manager does not stop it");
        }
        return lastFailure != null ? lastFailure : ServerStatus.stopped();
    }

    /**
     * Record a server found running that this manager did not start, for example after a port conflict. It is
     * shown but never controlled.
     */
    public synchronized void attachExternal(OllamaInstallation installation, String reportedVersion)
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

    /** Stop the owned server. Does nothing when no server runs; fails with {@code NOT_OWNED} for external ones. */
    public ServerStatus stop() throws OperationException {
        ManagedServer server;
        synchronized (this) {
            requireIdle();
            if (owned == null) {
                if (external != null) {
                    throw new OperationException(ErrorKind.NOT_OWNED, "the external server is not owned by the manager");
                }
                return status();
            }
            server = owned;
            transition = ServerState.STOPPING;
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

    /** Stop the owned server and start it again from the same installation with new settings. */
    public ServerStatus restart(ServerSettings settings, CancellationToken cancellation) throws OperationException {
        ManagedServer server;
        synchronized (this) {
            requireIdle();
            if (owned == null) {
                throw new OperationException(external != null ? ErrorKind.NOT_OWNED : ErrorKind.INVALID_STATE,
                    "no owned server to restart");
            }
            server = owned;
            transition = ServerState.STOPPING;
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
