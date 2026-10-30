package com.aresstack.ollamamanager.infrastructure.runtime;

import com.aresstack.ollamamanager.application.port.ManagedServer;
import com.aresstack.ollamamanager.domain.server.OllamaInstallation;
import com.aresstack.ollamamanager.domain.server.ServerSettings;

/**
 * Handle to a process started by one {@link ProcessOllamaRuntime}. The {@link Process} object never leaves this
 * class, so ownership cannot be forged from a PID or an open port.
 */
final class OwnedServer implements ManagedServer {

    private final ProcessOllamaRuntime owner;
    private final OllamaInstallation installation;
    private final ServerSettings appliedSettings;
    private final Process process;
    private final BoundedLog log;

    OwnedServer(ProcessOllamaRuntime owner, OllamaInstallation installation, ServerSettings appliedSettings,
                Process process, BoundedLog log) {
        this.owner = owner;
        this.installation = installation;
        this.appliedSettings = appliedSettings;
        this.process = process;
        this.log = log;
    }

    boolean isOwnedBy(ProcessOllamaRuntime runtime) {
        return owner == runtime;
    }

    Process process() {
        return process;
    }

    BoundedLog log() {
        return log;
    }

    @Override
    public OllamaInstallation installation() {
        return installation;
    }

    @Override
    public ServerSettings appliedSettings() {
        return appliedSettings;
    }

    @Override
    public boolean isAlive() {
        return process.isAlive();
    }

    @Override
    public Integer exitCode() {
        try {
            return process.exitValue();
        } catch (IllegalThreadStateException e) {
            return null;
        }
    }

    @Override
    public String toString() {
        return "OwnedServer{" + installation + ", alive=" + isAlive() + "}";
    }
}
