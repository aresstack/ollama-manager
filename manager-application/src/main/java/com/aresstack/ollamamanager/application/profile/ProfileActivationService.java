package com.aresstack.ollamamanager.application.profile;

import com.aresstack.ollamamanager.application.common.ErrorKind;
import com.aresstack.ollamamanager.application.common.OperationException;
import com.aresstack.ollamamanager.application.port.ConfigurationRepository;
import com.aresstack.ollamamanager.domain.configuration.ManagerConfiguration;
import com.aresstack.ollamamanager.domain.profile.ActivationMode;
import com.aresstack.ollamamanager.domain.profile.ModelProfile;
import com.aresstack.ollamamanager.domain.profile.ProfileActivationPlan;
import com.aresstack.ollamamanager.domain.server.RuntimeOwnership;
import com.aresstack.ollamamanager.domain.server.ServerSettings;

/**
 * Switches the active profile. Server-wide settings never change silently: an owned server needs an explicit
 * restart confirmation, and an external server is reported as "cannot apply automatically".
 * <p>
 * This service only records the decision. The caller performs the confirmed restart through the server
 * lifecycle use case (#3) with {@link ProfileActivationPlan#targetSettings()}.
 */
public final class ProfileActivationService {

    private final ModelProfileRepository profiles;
    private final ConfigurationRepository configurations;

    /**
     * @param profiles the repository whose lock serializes activation with profile saves and deletions
     */
    public ProfileActivationService(ModelProfileRepository profiles) {
        if (profiles == null) {
            throw new IllegalArgumentException("profiles must not be null");
        }
        this.profiles = profiles;
        this.configurations = profiles.configurations();
    }

    /**
     * Plan activating {@code profileId} on a server running with {@code applied} under {@code ownership}.
     * Nothing is stored.
     */
    public ProfileActivationPlan plan(String profileId, ServerSettings applied, RuntimeOwnership ownership)
        throws OperationException {
        return ProfileActivationPlan.plan(requireProfile(configurations.load(), profileId), applied, ownership);
    }

    /**
     * Make {@code profileId} active and its server settings the desired settings for owned servers.
     *
     * @param restartConfirmed whether the user confirmed restarting the owned server; required when the plan
     *                         is {@link ActivationMode#RESTART_OWNED_SERVER}
     * @return the plan the caller must follow
     * @throws OperationException {@code INVALID_STATE} if a needed restart was not confirmed
     */
    public ProfileActivationPlan activate(String profileId, ServerSettings applied,
                                                       RuntimeOwnership ownership, boolean restartConfirmed)
        throws OperationException {
        ProfileActivationPlan[] plan = new ProfileActivationPlan[1];
        profiles.update(configuration -> {
            ModelProfile profile = requireProfile(configuration, profileId);
            plan[0] = ProfileActivationPlan.plan(profile, applied, ownership);
            if (plan[0].mode() == ActivationMode.RESTART_OWNED_SERVER && !restartConfirmed) {
                throw new OperationException(ErrorKind.INVALID_STATE, "profile " + profileId
                    + " changes server-wide settings; confirm a restart of the owned server first");
            }
            return configuration.withDesiredServerSettings(profile.serverSettings()).withActiveProfileId(profileId);
        });
        return plan[0];
    }

    private static ModelProfile requireProfile(ManagerConfiguration configuration, String profileId)
        throws OperationException {
        ModelProfile profile = configuration.findProfile(profileId);
        if (profile == null) {
            throw new OperationException(ErrorKind.NOT_FOUND, "unknown profile: " + profileId);
        }
        return profile;
    }
}
