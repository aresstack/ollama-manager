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
    public synchronized void save(ModelProfile profile) throws OperationException {
        ManagerConfiguration configuration = configurations.load();
        ModelProfile existing = configuration.findProfile(profile.id());
        int expected = existing == null ? 1 : existing.revision() + 1;
        if (profile.revision() != expected) {
            throw new OperationException(ErrorKind.INVALID_STATE, "profile " + profile.id() + " has revision "
                + profile.revision() + ", expected " + expected);
        }
        configurations.save(configuration.withProfile(profile));
    }

    /** Remove a profile; if it was active, no profile is active afterwards. Unknown ids are ignored. */
    public synchronized void delete(String id) throws OperationException {
        ManagerConfiguration configuration = configurations.load();
        if (configuration.findProfile(id) == null) {
            return;
        }
        List<ModelProfile> remaining = new ArrayList<ModelProfile>();
        for (ModelProfile profile : configuration.profiles()) {
            if (!profile.id().equals(id)) {
                remaining.add(profile);
            }
        }
        String activeId = id.equals(configuration.activeProfileId()) ? null : configuration.activeProfileId();
        configurations.save(new ManagerConfiguration(configuration.desiredServerSettings(),
            configuration.selectedInstallationKind(), configuration.selectedBinaryDirectory(), remaining, activeId));
    }
}
