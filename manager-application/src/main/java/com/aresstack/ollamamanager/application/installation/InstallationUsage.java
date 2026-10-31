package com.aresstack.ollamamanager.application.installation;

import com.aresstack.ollamamanager.application.common.OperationException;
import com.aresstack.ollamamanager.domain.server.OllamaInstallation;

/**
 * Tells whether a manager-owned Ollama process currently runs from an installation. Provided by the process
 * management (issue #3); uninstall is refused while it returns {@code true}.
 */
public interface InstallationUsage {

    boolean isInUse(OllamaInstallation installation) throws OperationException;
}
