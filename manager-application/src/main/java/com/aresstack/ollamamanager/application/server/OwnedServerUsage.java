package com.aresstack.ollamamanager.application.server;

import com.aresstack.ollamamanager.application.installation.InstallationUsage;
import com.aresstack.ollamamanager.domain.server.OllamaInstallation;
import com.aresstack.ollamamanager.domain.server.ServerState;
import com.aresstack.ollamamanager.domain.server.ServerStatus;

/**
 * Treats an installation as in use while the manager's own server runs from its binary directory. While a start
 * or stop is in progress the running binary is not reliably known, so every installation counts as in use until
 * the transition has finished. Callers that delete files must also hold the shared {@link LifecycleLock}, so
 * that no start can begin between this check and the deletion.
 */
public final class OwnedServerUsage implements InstallationUsage {

    private final ServerLifecycleService lifecycle;

    public OwnedServerUsage(ServerLifecycleService lifecycle) {
        if (lifecycle == null) {
            throw new IllegalArgumentException("lifecycle must not be null");
        }
        this.lifecycle = lifecycle;
    }

    @Override
    public boolean isInUse(OllamaInstallation installation) {
        ServerStatus status = lifecycle.status();
        if (status.state() == ServerState.STARTING || status.state() == ServerState.STOPPING) {
            return true;
        }
        OllamaInstallation running = status.installation();
        return status.canBeStoppedByManager() && running != null && running.binaryDirectory() != null
            && running.binaryDirectory().equals(installation.binaryDirectory());
    }
}
