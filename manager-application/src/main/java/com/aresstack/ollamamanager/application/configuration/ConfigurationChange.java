package com.aresstack.ollamamanager.application.configuration;

import com.aresstack.ollamamanager.application.common.OperationException;
import com.aresstack.ollamamanager.domain.configuration.ManagerConfiguration;

/**
 * A change applied to the latest configuration under the configuration lock. It may reject the change with an
 * {@link OperationException} (for example {@code INVALID_STATE} on a revision conflict); nothing is saved then.
 */
@FunctionalInterface
public interface ConfigurationChange {

    ManagerConfiguration apply(ManagerConfiguration current) throws OperationException;
}
