package com.aresstack.ollamamanager.application.port;

import com.aresstack.ollamamanager.application.common.CancellationToken;
import com.aresstack.ollamamanager.application.common.OperationException;

import java.util.Map;
import java.util.Set;

/**
 * Writes variables permanently into the current user's environment, so that an Ollama started without the manager
 * (for example the Ollama app) uses them too. On Windows this is {@code HKCU\Environment}.
 */
public interface UserEnvironmentPort {

    /** @return whether this operating system supports writing the user environment */
    boolean isSupported();

    /** @return short description of the target, for example "Windows user environment variables" */
    String targetDescription();

    /**
     * Set every given variable in the user environment, overwriting existing values, delete every variable in
     * {@code remove}, and notify running programs. Other variables are left untouched.
     */
    void write(Map<String, String> variables, Set<String> remove, CancellationToken cancellation)
        throws OperationException;
}
