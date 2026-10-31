package com.aresstack.ollamamanager.application.configuration;

import com.aresstack.ollamamanager.application.common.OperationException;
import com.aresstack.ollamamanager.application.port.ConfigurationRepository;
import com.aresstack.ollamamanager.domain.configuration.ManagerConfiguration;

/**
 * The single entry point for changing settings and profiles. Every change is a read-modify-write under the
 * repository's lock, so concurrent edits are applied in sequence and none is lost.
 * <p>
 * Integration rule: the composition root creates one {@link ConfigurationRepository} and one
 * {@code ConfigurationService} and hands that service to every use case. Use cases and the UI never call
 * {@link ConfigurationRepository#save} directly. Services built on the same repository instance share its
 * lock; separate repository instances for the same file are not protected and must not be created.
 */
public final class ConfigurationService {

    private final ConfigurationRepository repository;

    public ConfigurationService(ConfigurationRepository repository) {
        if (repository == null) {
            throw new IllegalArgumentException("repository must not be null");
        }
        this.repository = repository;
    }

    /** Return the stored configuration. */
    public ManagerConfiguration current() throws OperationException {
        synchronized (repository) {
            return repository.load();
        }
    }

    /**
     * Load the latest configuration, apply {@code change} and save the result. Validation inside
     * {@code change} runs under the lock. Nothing is saved if {@code change} throws.
     *
     * @return the saved configuration
     */
    public ManagerConfiguration update(ConfigurationChange change) throws OperationException {
        if (change == null) {
            throw new IllegalArgumentException("change must not be null");
        }
        synchronized (repository) {
            ManagerConfiguration updated = change.apply(repository.load());
            if (updated == null) {
                throw new IllegalArgumentException("change must not return null");
            }
            repository.save(updated);
            return updated;
        }
    }
}
