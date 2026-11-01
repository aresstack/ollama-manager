package com.aresstack.ollamamanager.application.configuration;

import com.aresstack.ollamamanager.application.common.CancellationToken;
import com.aresstack.ollamamanager.application.common.ErrorKind;
import com.aresstack.ollamamanager.application.common.OperationException;
import com.aresstack.ollamamanager.application.port.UserEnvironmentPort;
import com.aresstack.ollamamanager.domain.common.Preconditions;
import com.aresstack.ollamamanager.domain.server.ServerSettings;

import java.util.Map;

/**
 * Writes the explicitly chosen server settings as {@code OLLAMA_*} variables into the user environment.
 * <p>
 * Only chosen settings are written; an unset setting never sets a variable. A variable already present in the user
 * environment for an unset setting is left alone, because the manager cannot tell whether the user wants it gone.
 */
public final class PermanentEnvironmentService {

    static final String NOTHING_SET = "No setting is set, so there is nothing to save permanently.";

    private final UserEnvironmentPort environment;

    public PermanentEnvironmentService(UserEnvironmentPort environment) {
        this.environment = Preconditions.requireNonNull(environment, "environment");
    }

    public boolean isSupported() {
        return environment.isSupported();
    }

    public String targetDescription() {
        return environment.targetDescription();
    }

    /** @return the variables that were written */
    public Map<String, String> persist(ServerSettings settings, CancellationToken cancellation)
        throws OperationException {
        if (!environment.isSupported()) {
            throw new OperationException(ErrorKind.UNSUPPORTED_PLATFORM,
                "Saving settings permanently is not supported on this operating system yet.");
        }
        Map<String, String> variables = settings.toEnvironment();
        if (variables.isEmpty()) {
            throw new OperationException(ErrorKind.INVALID_STATE, NOTHING_SET);
        }
        environment.write(variables, cancellation);
        return variables;
    }
}
