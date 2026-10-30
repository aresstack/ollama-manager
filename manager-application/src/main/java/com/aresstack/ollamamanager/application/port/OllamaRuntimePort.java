package com.aresstack.ollamamanager.application.port;

import com.aresstack.ollamamanager.application.common.CancellationToken;
import com.aresstack.ollamamanager.application.common.OperationException;
import com.aresstack.ollamamanager.domain.server.OllamaInstallation;
import com.aresstack.ollamamanager.domain.server.ServerSettings;

import java.io.File;
import java.util.List;

/**
 * Discovers Ollama binaries and controls processes the manager starts. Implemented in issue #3.
 */
public interface OllamaRuntimePort {

    /**
     * Find managed-capable installations in the given directories, the default installation folder and
     * {@code PATH}. Each candidate is validated with {@code ollama --version}. Never returns external servers.
     */
    List<OllamaInstallation> discover(List<File> additionalDirectories, CancellationToken cancellation)
        throws OperationException;

    /**
     * Start {@code ollama serve} from the installation's binary directory and wait until the API answers.
     * The child environment is the inherited one with every variable in
     * {@code ServerSettingCatalog.environmentVariables()} removed and {@code settings.toEnvironment()} applied. Fails with {@code PORT_IN_USE} if the endpoint is already served.
     *
     * @throws IllegalArgumentException if the installation is {@code EXTERNAL_ATTACHED}
     */
    ManagedServer start(OllamaInstallation installation, ServerSettings settings, CancellationToken cancellation)
        throws OperationException;

    /**
     * Stop a server started by this port: request termination, then force it after the grace period.
     * Fails with {@code NOT_OWNED} for a handle this port did not create.
     */
    void stop(ManagedServer server, long gracePeriodMillis) throws OperationException;

    /** Return recent combined stdout/stderr lines of an owned server, oldest first, bounded in size. */
    List<String> recentLog(ManagedServer server);
}
