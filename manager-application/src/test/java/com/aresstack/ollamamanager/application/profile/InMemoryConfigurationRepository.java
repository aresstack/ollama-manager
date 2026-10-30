package com.aresstack.ollamamanager.application.profile;

import com.aresstack.ollamamanager.application.port.ConfigurationRepository;
import com.aresstack.ollamamanager.domain.configuration.ManagerConfiguration;

final class InMemoryConfigurationRepository implements ConfigurationRepository {

    ManagerConfiguration stored = ManagerConfiguration.defaults();
    int saves;

    @Override
    public ManagerConfiguration load() {
        return stored;
    }

    @Override
    public void save(ManagerConfiguration configuration) {
        stored = configuration;
        saves++;
    }
}
