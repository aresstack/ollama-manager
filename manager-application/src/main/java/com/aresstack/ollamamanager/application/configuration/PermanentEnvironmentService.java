package com.aresstack.ollamamanager.application.configuration;

import com.aresstack.ollamamanager.application.common.CancellationToken;
import com.aresstack.ollamamanager.application.common.ErrorKind;
import com.aresstack.ollamamanager.application.common.OperationException;
import com.aresstack.ollamamanager.application.port.UserEnvironmentPort;
import com.aresstack.ollamamanager.domain.common.Preconditions;
import com.aresstack.ollamamanager.domain.server.ServerSettings;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import com.aresstack.ollamamanager.domain.server.ServerSettingCatalog;

/**
 * Writes the explicitly chosen server settings as {@code OLLAMA_*} variables into the user environment.
 * <p>
 * Chosen settings are written; for every unset setting the variable is deleted from the user environment, so the
 * user environment mirrors the manager's settings exactly.
 */
public final class PermanentEnvironmentService {

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

    /** @return the variables deleted from the user environment for {@code settings}: those of unset settings */
    public static Set<String> removals(ServerSettings settings) {
        Set<String> remove = new TreeSet<String>(ServerSettingCatalog.environmentVariables());
        remove.removeAll(settings.toEnvironment().keySet());
        return remove;
    }

    /** @return the variables that were written */
    public Map<String, String> persist(ServerSettings settings, CancellationToken cancellation)
        throws OperationException {
        if (!environment.isSupported()) {
            throw new OperationException(ErrorKind.UNSUPPORTED_PLATFORM,
                "Saving settings permanently is not supported on this operating system yet.");
        }
        Map<String, String> variables = settings.toEnvironment();
        environment.write(variables, removals(settings), cancellation);
        return variables;
    }
}
