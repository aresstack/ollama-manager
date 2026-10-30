package com.aresstack.ollamamanager.domain.server;

import com.aresstack.ollamamanager.domain.common.Preconditions;

/**
 * Snapshot of a server's state as reported by the runtime and API adapters.
 */
public final class ServerStatus {

    private static final ServerStatus STOPPED = new ServerStatus(ServerState.STOPPED, RuntimeOwnership.NONE, null, null, "");

    private final ServerState state;
    private final RuntimeOwnership ownership;
    private final OllamaInstallation installation;
    private final String reportedVersion;
    private final String message;

    public ServerStatus(ServerState state, RuntimeOwnership ownership, OllamaInstallation installation,
                        String reportedVersion, String message) {
        this.state = Preconditions.requireNonNull(state, "state");
        this.ownership = Preconditions.requireNonNull(ownership, "ownership");
        if (ownership != RuntimeOwnership.NONE) {
            Preconditions.requireNonNull(installation, "installation");
        }
        if (ownership == RuntimeOwnership.EXTERNAL && installation.kind() != InstallationKind.EXTERNAL_ATTACHED) {
            throw new IllegalArgumentException("external ownership requires an EXTERNAL_ATTACHED installation");
        }
        if (ownership == RuntimeOwnership.OWNED && !installation.kind().isStartableByManager()) {
            throw new IllegalArgumentException("owned servers require a managed installation");
        }
        this.installation = installation;
        this.reportedVersion = reportedVersion;
        this.message = message == null ? "" : message;
    }

    public static ServerStatus stopped() {
        return STOPPED;
    }

    public ServerState state() {
        return state;
    }

    public RuntimeOwnership ownership() {
        return ownership;
    }

    /**
     * Return the installation the status refers to, or {@code null} when nothing is running.
     */
    public OllamaInstallation installation() {
        return installation;
    }

    /**
     * Return the version reported by {@code GET /api/version}, or {@code null} if not verified.
     */
    public String reportedVersion() {
        return reportedVersion;
    }

    public String message() {
        return message;
    }

    public boolean canBeStoppedByManager() {
        return ownership.permitsLifecycleControl()
            && (state == ServerState.RUNNING || state == ServerState.STARTING);
    }

    @Override
    public String toString() {
        return "ServerStatus{" + state + ", " + ownership + ", " + reportedVersion + ", " + message + "}";
    }
}
