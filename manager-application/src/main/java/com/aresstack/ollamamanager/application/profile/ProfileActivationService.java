package com.aresstack.ollamamanager.application.profile;

import com.aresstack.ollamamanager.application.common.CancellationToken;
import com.aresstack.ollamamanager.application.common.ErrorKind;
import com.aresstack.ollamamanager.application.common.OperationException;
import com.aresstack.ollamamanager.application.configuration.ConfigurationService;
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

    private final ConfigurationService configurations;
    private final ProfileEnvironment environment;

    /**
     * @param configurations the single service instance the composition root hands to every use case
     * @param environment    captures GPU fingerprint, Ollama version and installed models for the validity check
     */
    public ProfileActivationService(ConfigurationService configurations, ProfileEnvironment environment) {
        if (configurations == null || environment == null) {
            throw new IllegalArgumentException("configurations and environment must not be null");
        }
        this.configurations = configurations;
        this.environment = environment;
    }

    /** Evaluate whether {@code profileId} still matches the current environment. Nothing is stored. */
    public ProfileValidity validity(String profileId, CancellationToken cancellation) throws OperationException {
        ProfileEnvironment.Snapshot snapshot = environment.capture(cancellation);
        return ProfileValidity.evaluate(requireProfile(configurations.current(), profileId), snapshot);
    }

    /**
     * Plan activating {@code profileId} on a server running with {@code applied} under {@code ownership}.
     * Nothing is stored.
     */
    public ProfileActivationPlan plan(String profileId, ServerSettings applied, RuntimeOwnership ownership)
        throws OperationException {
        return ProfileActivationPlan.plan(requireProfile(configurations.current(), profileId), applied, ownership);
    }

    /**
     * Make {@code profileId} active and its server settings the desired settings for owned servers. Validity is
     * evaluated again here, against the profile revision stored at this moment; a result shown earlier in the UI
     * is never trusted.
     *
     * @param restartConfirmed whether the user confirmed restarting the owned server; required when the plan
     *                         is {@link ActivationMode#RESTART_OWNED_SERVER}
     * @param approval         the user's confirmation for an outdated or unverified profile, or {@code null};
     *                         accepted only if it equals the evaluation made now
     * @return the plan the caller must follow
     * @throws OperationException {@code INVALID_STATE} if the profile is not valid and not approved, or if a
     *                            needed restart was not confirmed
     */
    public ProfileActivationPlan activate(String profileId, ServerSettings applied, RuntimeOwnership ownership,
                                          boolean restartConfirmed, ValidityApproval approval,
                                          CancellationToken cancellation) throws OperationException {
        ProfileEnvironment.Snapshot snapshot = environment.capture(cancellation);
        ProfileActivationPlan[] plan = new ProfileActivationPlan[1];
        configurations.update(configuration -> {
            ModelProfile profile = requireProfile(configuration, profileId);
            ProfileValidity validity = ProfileValidity.evaluate(profile, snapshot);
            if (validity.result() != ProfileValidity.Result.VALID
                && (approval == null || !approval.covers(profileId, profile.revision(), validity))) {
                throw new OperationException(ErrorKind.INVALID_STATE, "profile " + profileId + " revision "
                    + profile.revision() + " is " + validity.result().label() + " " + validity.findings()
                    + "; confirm exactly these findings to activate it anyway");
            }
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
