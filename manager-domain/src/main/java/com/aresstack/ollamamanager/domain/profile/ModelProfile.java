package com.aresstack.ollamamanager.domain.profile;

import com.aresstack.ollamamanager.domain.common.Preconditions;
import com.aresstack.ollamamanager.domain.model.ModelOptions;
import com.aresstack.ollamamanager.domain.server.ServerSettings;

/**
 * User optimization profile for one model scope. Separates server-wide settings, which need a restart,
 * from per-request model options, which do not.
 */
public final class ModelProfile {

    private final String id;
    private final String name;
    private final int revision;
    private final ProfileScope scope;
    private final ServerSettings serverSettings;
    private final ModelOptions modelOptions;

    public ModelProfile(String id, String name, int revision, ProfileScope scope, ServerSettings serverSettings,
                        ModelOptions modelOptions) {
        this.id = Preconditions.requireNonBlank(id, "id");
        this.name = Preconditions.requireNonBlank(name, "name");
        this.revision = Preconditions.requireInRange(revision, 1, Integer.MAX_VALUE, "revision");
        this.scope = Preconditions.requireNonNull(scope, "scope");
        this.serverSettings = Preconditions.requireNonNull(serverSettings, "serverSettings");
        this.modelOptions = Preconditions.requireNonNull(modelOptions, "modelOptions");
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    /** Return the revision, incremented on every saved change. */
    public int revision() {
        return revision;
    }

    public ProfileScope scope() {
        return scope;
    }

    public ServerSettings serverSettings() {
        return serverSettings;
    }

    public ModelOptions modelOptions() {
        return modelOptions;
    }

    /** Return a copy with new settings and the next revision. */
    public ModelProfile revise(ServerSettings newServerSettings, ModelOptions newModelOptions) {
        return new ModelProfile(id, name, revision + 1, scope, newServerSettings, newModelOptions);
    }

    @Override
    public String toString() {
        return "ModelProfile{" + id + " r" + revision + ", " + name + "}";
    }
}
