package com.aresstack.ollamamanager.acceptance;

import com.aresstack.ollamamanager.application.common.CancellationToken;
import com.aresstack.ollamamanager.application.common.OperationException;
import com.aresstack.ollamamanager.application.port.ManagedServer;
import com.aresstack.ollamamanager.application.port.OllamaRuntimePort;
import com.aresstack.ollamamanager.domain.server.OllamaInstallation;
import com.aresstack.ollamamanager.domain.server.ServerSettings;

/**
 * The only way the acceptance run starts a server. Every start passes {@link AcceptanceGuard} first, so no
 * caller can start a process on the default or reference port or with a model root outside the work directory.
 */
final class GuardedRuntime {

    private final OllamaRuntimePort runtime;
    private final AcceptanceGuard guard;

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
        return runtime.start(installation, settings, cancellation);
    }
}
