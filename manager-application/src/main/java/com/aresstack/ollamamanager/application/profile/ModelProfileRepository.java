package com.aresstack.ollamamanager.application.profile;

import com.aresstack.ollamamanager.application.common.ErrorKind;
import com.aresstack.ollamamanager.application.common.OperationException;
import com.aresstack.ollamamanager.application.configuration.ConfigurationService;
import com.aresstack.ollamamanager.domain.configuration.ManagerConfiguration;
import com.aresstack.ollamamanager.domain.profile.ModelProfile;
import com.aresstack.ollamamanager.domain.server.ServerSetting;
import com.aresstack.ollamamanager.domain.server.ServerSettingCatalog;
import com.aresstack.ollamamanager.domain.server.ServerSettings;

import java.util.ArrayList;
import java.util.List;

/**
 * Stores user profiles inside the persisted {@link ManagerConfiguration}. Every write is one
 * {@link ConfigurationService#update} call, so it shares the configuration lock with settings changes and
 * profile activation. Saving uses optimistic revision checks so a stale editor cannot overwrite a newer profile.
 */
public final class ModelProfileRepository {

    private final ConfigurationService configurations;

    /**
     * @param configurations the single service instance the composition root hands to every use case
     */
    public ModelProfileRepository(ConfigurationService configurations) {
        if (configurations == null) {
            throw new IllegalArgumentException("configurations must not be null");
        }
        this.configurations = configurations;
    }

    public List<ModelProfile> list() throws OperationException {
        return configurations.current().profiles();
    }

    /** Return the profile with this id, or {@code null}. */
    public ModelProfile find(String id) throws OperationException {
        return configurations.current().findProfile(id);
    }

    /** Return the active profile, or {@code null} if none is active. */
    public ModelProfile active() throws OperationException {
        ManagerConfiguration configuration = configurations.current();
        String id = configuration.activeProfileId();
        return id == null ? null : configuration.findProfile(id);
    }

    /**
     * Save a profile. A new id must have revision 1; an existing profile must be saved with exactly the
     * next revision, as produced by {@link ModelProfile#revise}.
     *
     * @throws OperationException {@code INVALID_STATE} on a revision conflict
     */
    public void save(ModelProfile profile) throws OperationException {
        configurations.update(configuration -> {
            ModelProfile existing = configuration.findProfile(profile.id());
            int expected = existing == null ? 1 : existing.revision() + 1;
            if (profile.revision() != expected) {
                throw new OperationException(ErrorKind.INVALID_STATE, "profile " + profile.id() + " has revision "
                    + profile.revision() + ", expected " + expected);
            }
            return configuration.withProfile(profile);
        });
    }

    /**
     * Remove a profile. If it was active, no profile is active afterwards and the server settings it chose are
     * unset again (see {@link #deactivate()}). Unknown ids are ignored.
     */
    public void delete(String id) throws OperationException {
        configurations.update(configuration -> {
            if (configuration.findProfile(id) == null) {
                return configuration;
            }
            ManagerConfiguration base = id.equals(configuration.activeProfileId())
                ? deactivated(configuration) : configuration;
            return without(base, id);
        });
    }

    /**
     * Make no profile active. Every server setting the active profile chose is unset again, so the server runs
     * with Ollama's defaults for them after its next (re)start; settings the profile does not touch, such as an
     * imported model folder, stay as they are. Does nothing if no profile is active.
     */
    public void deactivate() throws OperationException {
        configurations.update(ModelProfileRepository::deactivated);
    }

    private static ManagerConfiguration deactivated(ManagerConfiguration configuration) {
        String activeId = configuration.activeProfileId();
        if (activeId == null) {
            return configuration;
        }
        ServerSettings desired = configuration.desiredServerSettings();
        for (String key : configuration.findProfile(activeId).serverSettings().toEncodedMap().keySet()) {
            ServerSetting<?> setting = ServerSettingCatalog.byId(key);
            if (setting != null) {
                desired = unset(desired, setting);
            }
        }
        return configuration.withDesiredServerSettings(desired).withActiveProfileId(null);
    }

    private static <T> ServerSettings unset(ServerSettings settings, ServerSetting<T> setting) {
        return settings.with(setting, null);
    }

    private static ManagerConfiguration without(ManagerConfiguration configuration, String id) {
        List<ModelProfile> remaining = new ArrayList<ModelProfile>();
        for (ModelProfile profile : configuration.profiles()) {
            if (!profile.id().equals(id)) {
                remaining.add(profile);
            }
        }
        String activeId = id.equals(configuration.activeProfileId()) ? null : configuration.activeProfileId();
        return new ManagerConfiguration(configuration.desiredServerSettings(),
            configuration.selectedInstallationKind(), configuration.selectedBinaryDirectory(), remaining, activeId);
    }
}
