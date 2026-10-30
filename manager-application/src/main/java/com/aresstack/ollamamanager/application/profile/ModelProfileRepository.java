package com.aresstack.ollamamanager.application.profile;

import com.aresstack.ollamamanager.application.common.ErrorKind;
import com.aresstack.ollamamanager.application.common.OperationException;
import com.aresstack.ollamamanager.application.port.ConfigurationRepository;
import com.aresstack.ollamamanager.domain.configuration.ManagerConfiguration;
import com.aresstack.ollamamanager.domain.profile.ModelProfile;

import java.util.ArrayList;
import java.util.List;

/**
 * Stores user profiles inside the persisted {@link ManagerConfiguration}. Saving uses optimistic revision
 * checks so a stale editor cannot overwrite a newer profile.
 */
public final class ModelProfileRepository {

    private final ConfigurationRepository configurations;

    public ModelProfileRepository(ConfigurationRepository configurations) {
        if (configurations == null) {
            throw new IllegalArgumentException("configurations must not be null");
        }
        this.configurations = configurations;
    }

    ConfigurationRepository configurations() {
        return configurations;
    }

    public List<ModelProfile> list() throws OperationException {
        return configurations.load().profiles();
    }

    /** Return the profile with this id, or {@code null}. */
    public ModelProfile find(String id) throws OperationException {
        return configurations.load().findProfile(id);
    }

    /** Return the active profile, or {@code null} if none is active. */
    public ModelProfile active() throws OperationException {
        ManagerConfiguration configuration = configurations.load();
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
        update(configuration -> {
            ModelProfile existing = configuration.findProfile(profile.id());
            int expected = existing == null ? 1 : existing.revision() + 1;
            if (profile.revision() != expected) {
                throw new OperationException(ErrorKind.INVALID_STATE, "profile " + profile.id() + " has revision "
                    + profile.revision() + ", expected " + expected);
            }
            return configuration.withProfile(profile);
        });
    }

    /** Remove a profile; if it was active, no profile is active afterwards. Unknown ids are ignored. */
    public void delete(String id) throws OperationException {
        update(configuration -> configuration.findProfile(id) == null ? configuration : without(configuration, id));
    }

    /**
     * Run one read-modify-write of the configuration under this repository's lock. Every profile change,
     * including activation, goes through here so none restores a stale snapshot. Nothing is saved if
     * {@code change} returns the loaded instance unchanged.
     */
    synchronized void update(Change change) throws OperationException {
        ManagerConfiguration loaded = configurations.load();
        ManagerConfiguration changed = change.apply(loaded);
        if (changed != loaded) {
            configurations.save(changed);
        }
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

    interface Change {
        ManagerConfiguration apply(ManagerConfiguration current) throws OperationException;
    }
}
