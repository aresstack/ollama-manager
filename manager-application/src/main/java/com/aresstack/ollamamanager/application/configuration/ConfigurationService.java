package com.aresstack.ollamamanager.application.configuration;

import com.aresstack.ollamamanager.application.common.OperationException;
import com.aresstack.ollamamanager.application.port.ConfigurationRepository;
import com.aresstack.ollamamanager.domain.configuration.ManagerConfiguration;

import java.util.concurrent.locks.ReentrantLock;
import java.util.function.UnaryOperator;

/**
 * The single entry point for changing settings and profiles. Every change is a read-modify-write under one lock,
 * so concurrent edits from different use cases are applied in sequence and none is lost.
 * Use cases must not call {@link ConfigurationRepository#save} directly.
 */
public final class ConfigurationService {

    private final ConfigurationRepository repository;
    private final ReentrantLock lock = new ReentrantLock();

    public ConfigurationService(ConfigurationRepository repository) {
        if (repository == null) {
            throw new IllegalArgumentException("repository must not be null");
        }
        this.repository = repository;
    }

    /** Return the stored configuration. */
    public ManagerConfiguration current() throws OperationException {
        lock.lock();
        try {
            return repository.load();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Load the latest configuration, apply {@code change} and save the result atomically with respect to other
     * updates. Nothing is saved if {@code change} throws.
     *
     * @return the saved configuration
     */
    public ManagerConfiguration update(UnaryOperator<ManagerConfiguration> change) throws OperationException {
        if (change == null) {
            throw new IllegalArgumentException("change must not be null");
        }
        lock.lock();
        try {
            ManagerConfiguration updated = change.apply(repository.load());
            if (updated == null) {
                throw new IllegalArgumentException("change must not return null");
            }
            repository.save(updated);
            return updated;
        } finally {
            lock.unlock();
        }
    }
}
