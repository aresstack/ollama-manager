package com.aresstack.ollamamanager.application.port;

import com.aresstack.ollamamanager.application.common.OperationException;
import com.aresstack.ollamamanager.domain.configuration.ManagerConfiguration;

/**
 * Persists preferences atomically in the user data directory.
 */
public interface ConfigurationRepository {

    /** Return the stored configuration, or defaults if none exists yet. */
    ManagerConfiguration load() throws OperationException;

    /** Replace the stored configuration atomically. */
    void save(ManagerConfiguration configuration) throws OperationException;
}
