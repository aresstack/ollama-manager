package com.aresstack.ollamamanager.acceptance;

import com.aresstack.ollamamanager.application.common.CancellationToken;
import com.aresstack.ollamamanager.application.common.OperationException;
import com.aresstack.ollamamanager.application.installation.InstallationUsage;
import com.aresstack.ollamamanager.application.port.ManagedServer;
import com.aresstack.ollamamanager.application.port.OllamaRuntimePort;
import com.aresstack.ollamamanager.domain.server.OllamaInstallation;
import com.aresstack.ollamamanager.domain.server.ServerSettings;

import java.io.File;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The only way the acceptance run starts a server. Every start passes {@link AcceptanceGuard} first, so no
 * caller can start a process on the default or reference port or with a model root outside the work directory.
 * It also reports an installation as in use while a server it started from that binary directory is alive.
 */
final class GuardedRuntime implements InstallationUsage {

    private final OllamaRuntimePort runtime;
    private final AcceptanceGuard guard;
    private final List<ManagedServer> started = new CopyOnWriteArrayList<ManagedServer>();

    GuardedRuntime(OllamaRuntimePort runtime, AcceptanceGuard guard) {
        this.runtime = runtime;
        this.guard = guard;
    }

    ManagedServer start(OllamaInstallation installation, ServerSettings settings, CancellationToken cancellation)
        throws OperationException {
        guard.requireWritableEndpoint(settings.bind());
        if (settings.modelRoot() == null) {
            throw new IllegalStateException("test servers need an explicit model root below the work directory");
        }
        guard.requireInsideWorkDir(settings.modelRoot());
        ManagedServer server = runtime.start(installation, settings, cancellation);
        started.add(server);
        return server;
    }

    @Override
    public boolean isInUse(OllamaInstallation installation) {
        File directory = installation.binaryDirectory();
        if (directory == null) {
            return false;
        }
        File wanted = AcceptanceGuard.canonical(directory);
        for (ManagedServer server : started) {
            File running = server.installation().binaryDirectory();
            if (server.isAlive() && running != null && AcceptanceGuard.canonical(running).equals(wanted)) {
                return true;
            }
        }
        return false;
    }
}
