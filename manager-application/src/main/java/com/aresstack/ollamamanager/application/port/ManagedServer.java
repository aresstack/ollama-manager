package com.aresstack.ollamamanager.application.port;

import com.aresstack.ollamamanager.domain.server.OllamaInstallation;
import com.aresstack.ollamamanager.domain.server.ServerSettings;

/**
 * Handle to a server process this manager started. Holding the handle is the only proof of ownership;
 * a PID or an open port never is.
 */
public interface ManagedServer {

    OllamaInstallation installation();

    /** Return the settings the process was started with. */
    ServerSettings appliedSettings();

    boolean isAlive();

    /** Return the exit code, or {@code null} while the process runs. */
    Integer exitCode();
}
