package com.aresstack.ollamamanager.domain.profile;

import com.aresstack.ollamamanager.domain.common.Preconditions;
import com.aresstack.ollamamanager.domain.model.ModelOptions;
import com.aresstack.ollamamanager.domain.server.RuntimeOwnership;
import com.aresstack.ollamamanager.domain.server.ServerSetting;
import com.aresstack.ollamamanager.domain.server.ServerSettings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Result of comparing a profile with the settings a server currently runs with.
 */
public final class ProfileActivationPlan {

    private final ActivationMode mode;
    private final ServerSettings targetSettings;
    private final List<ServerSetting<?>> restartRequiredChanges;
    private final ModelOptions requestOptions;

    private ProfileActivationPlan(ActivationMode mode, ServerSettings targetSettings,
                                  List<ServerSetting<?>> restartRequiredChanges, ModelOptions requestOptions) {
        this.mode = mode;
        this.targetSettings = targetSettings;
        this.restartRequiredChanges = Collections.unmodifiableList(restartRequiredChanges);
        this.requestOptions = requestOptions;
    }

    /**
     * Plan the activation of {@code profile} on a server running with {@code applied} under {@code ownership}.
     */
    public static ProfileActivationPlan plan(ModelProfile profile, ServerSettings applied,
                                             RuntimeOwnership ownership) {
        Preconditions.requireNonNull(profile, "profile");
        Preconditions.requireNonNull(applied, "applied");
        Preconditions.requireNonNull(ownership, "ownership");
        List<ServerSetting<?>> changes = new ArrayList<ServerSetting<?>>();
        for (ServerSetting<?> setting : profile.serverSettings().differencesTo(applied)) {
            if (setting.isRestartRequired()) {
                changes.add(setting);
            }
        }
        return new ProfileActivationPlan(modeFor(changes.isEmpty(), ownership), profile.serverSettings(), changes,
            profile.modelOptions());
    }

    private static ActivationMode modeFor(boolean serverUnchanged, RuntimeOwnership ownership) {
        if (ownership == RuntimeOwnership.NONE) {
            return ActivationMode.APPLY_ON_NEXT_START;
        }
        if (serverUnchanged) {
            return ActivationMode.REQUEST_OPTIONS_ONLY;
        }
        return ownership == RuntimeOwnership.OWNED
            ? ActivationMode.RESTART_OWNED_SERVER
            : ActivationMode.CANNOT_APPLY_TO_EXTERNAL;
    }

    public ActivationMode mode() {
        return mode;
    }

    public ServerSettings targetSettings() {
        return targetSettings;
    }

    public List<ServerSetting<?>> restartRequiredChanges() {
        return restartRequiredChanges;
    }

    /** Return options the manager sends with its own requests; they do not affect other clients. */
    public ModelOptions requestOptions() {
        return requestOptions;
    }
}
