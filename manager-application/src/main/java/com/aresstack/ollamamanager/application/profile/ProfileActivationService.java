package com.aresstack.ollamamanager.application.profile;

import com.aresstack.ollamamanager.application.common.CancellationToken;
import com.aresstack.ollamamanager.application.common.ErrorKind;
import com.aresstack.ollamamanager.application.common.LifecycleLock;
import com.aresstack.ollamamanager.application.common.OperationException;
import com.aresstack.ollamamanager.application.configuration.ConfigurationService;
import com.aresstack.ollamamanager.application.server.ServerLifecycleService;
import com.aresstack.ollamamanager.domain.configuration.ManagerConfiguration;
import com.aresstack.ollamamanager.domain.profile.ActivationMode;
import com.aresstack.ollamamanager.domain.profile.ModelProfile;
import com.aresstack.ollamamanager.domain.profile.ProfileActivationPlan;
import com.aresstack.ollamamanager.domain.server.RuntimeOwnership;
import com.aresstack.ollamamanager.domain.server.ServerSettings;
import com.aresstack.ollamamanager.domain.server.ServerStatus;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Switches the active profile. Server-wide settings never change silently: an owned server needs an explicit
 * restart confirmation, and an external server is reported as "cannot apply automatically".
 * <p>
 * The service reads the server state itself; callers never pass ownership or applied settings. Activation runs
 * under the shared {@link LifecycleLock}, so the server cannot start, stop or restart between the check and the
 * commit, and a confirmed restart of the owned server runs inside the same lock section.
 */
public final class ProfileActivationService {

    private final ConfigurationService configurations;
    private final ProfileEnvironment environment;
    private final ServerLifecycleService lifecycle;
    private final LifecycleLock lock;

    /**
     * @param configurations the single service instance the composition root hands to every use case
     * @param environment    captures GPU fingerprint, Ollama version and installed models for the validity check
     * @param lifecycle      the server lifecycle whose state decides the activation mode
     * @param lock           the process-wide lock shared with the lifecycle and uninstall use cases
     */
    public ProfileActivationService(ConfigurationService configurations, ProfileEnvironment environment,
                                    ServerLifecycleService lifecycle, LifecycleLock lock) {
        if (configurations == null || environment == null || lifecycle == null || lock == null) {
            throw new IllegalArgumentException("configurations, environment, lifecycle and lock must not be null");
        }
        this.configurations = configurations;
        this.environment = environment;
        this.lifecycle = lifecycle;
        this.lock = lock;
    }

    /** Evaluate whether {@code profileId} still matches the current environment. Nothing is stored. */
    public ProfileValidity validity(String profileId, CancellationToken cancellation) throws OperationException {
        ProfileEnvironment.Snapshot snapshot = environment.capture(cancellation);
        return ProfileValidity.evaluate(requireProfile(configurations.current(), profileId), snapshot);
    }

    /** Evaluate every stored profile against one capture of the environment. Nothing is stored. */
    public Map<String, ProfileValidity> validities(CancellationToken cancellation) throws OperationException {
        ProfileEnvironment.Snapshot snapshot = environment.capture(cancellation);
        Map<String, ProfileValidity> result = new LinkedHashMap<String, ProfileValidity>();
        for (ModelProfile profile : configurations.current().profiles()) {
            result.put(profile.id(), ProfileValidity.evaluate(profile, snapshot));
        }
        return Collections.unmodifiableMap(result);
    }

    /**
     * Plan activating {@code profileId} on the server as it is now. Nothing is stored.
     *
     * @throws OperationException {@code INVALID_STATE} while the server is starting or stopping
     */
    public ProfileActivationPlan plan(String profileId) throws OperationException {
        return planFor(requireProfile(configurations.current(), profileId), lifecycle.status(),
            lifecycle.appliedSettings());
    }

    /**
     * Make {@code profileId} active. Server state, applied settings and validity are all read again here, under
     * the lifecycle lock; a plan or validity shown earlier in the UI is never trusted.
     *
     * @param expectedMode     the mode of the plan the user confirmed; activation fails if the server state
     *                         changed so that the mode is different now
     * @param restartConfirmed whether the user confirmed restarting the owned server; required when the mode is
     *                         {@link ActivationMode#RESTART_OWNED_SERVER}, in which case the restart runs here
     * @param approval         the user's confirmation for an outdated or unverified profile, or {@code null};
     *                         accepted only if it equals the evaluation made now
     * @return the plan that was carried out
     * @throws OperationException {@code INVALID_STATE} if the server is starting or stopping, another lifecycle
     *                            operation runs, the mode differs from {@code expectedMode}, the profile is not
     *                            valid and not approved, or a needed restart was not confirmed
     */
    public ProfileActivationPlan activate(String profileId, ActivationMode expectedMode, boolean restartConfirmed,
                                          ValidityApproval approval, CancellationToken cancellation)
        throws OperationException {
        if (expectedMode == null) {
            throw new IllegalArgumentException("expectedMode must not be null");
        }
        ProfileEnvironment.Snapshot snapshot = environment.capture(cancellation);
        return lock.run("activate profile " + profileId, () -> {
            ServerStatus status = lifecycle.status();
            ServerSettings applied = lifecycle.appliedSettings();
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
                plan[0] = planFor(profile, status, applied);
                if (plan[0].mode() != expectedMode) {
                    throw new OperationException(ErrorKind.INVALID_STATE, "the server state changed: activation"
                        + " now means " + plan[0].mode() + " instead of the confirmed " + expectedMode);
                }
                if (plan[0].mode() == ActivationMode.RESTART_OWNED_SERVER && !restartConfirmed) {
                    throw new OperationException(ErrorKind.INVALID_STATE, "profile " + profileId
                        + " changes server-wide settings; confirm a restart of the owned server first");
                }
                return configuration.withDesiredServerSettings(profile.serverSettings())
                    .withActiveProfileId(profileId);
            });
            if (plan[0].mode() == ActivationMode.RESTART_OWNED_SERVER) {
                // The profile is stored first, so a failed restart leaves it to apply on the next start.
                lifecycle.restart(plan[0].targetSettings(), cancellation);
            }
            return plan[0];
        });
    }

    /**
     * Derive the plan from the server state. A failed owned process counts as not running even though its last
     * settings are still known; an external server's applied settings are unknown.
     */
    private static ProfileActivationPlan planFor(ModelProfile profile, ServerStatus status, ServerSettings applied)
        throws OperationException {
        switch (status.state()) {
            case STARTING:
            case STOPPING:
                throw new OperationException(ErrorKind.INVALID_STATE,
                    "the server is " + status.state().name().toLowerCase(Locale.ROOT)
                        + "; try again when it is done");
            case RUNNING:
                if (status.ownership() == RuntimeOwnership.EXTERNAL) {
                    return ProfileActivationPlan.planForExternal(profile);
                }
                if (status.ownership() == RuntimeOwnership.OWNED) {
                    return ProfileActivationPlan.plan(profile,
                        applied == null ? ServerSettings.defaults() : applied, RuntimeOwnership.OWNED);
                }
                return ProfileActivationPlan.plan(profile, ServerSettings.defaults(), RuntimeOwnership.NONE);
            default:
                return ProfileActivationPlan.plan(profile, ServerSettings.defaults(), RuntimeOwnership.NONE);
        }
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
