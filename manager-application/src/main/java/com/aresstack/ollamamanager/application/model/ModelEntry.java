package com.aresstack.ollamamanager.application.model;

import com.aresstack.ollamamanager.domain.model.InstalledModel;
import com.aresstack.ollamamanager.domain.model.LoadedModel;
import com.aresstack.ollamamanager.domain.model.ModelIdentity;

/**
 * One row of the model inventory: a model on disk, in memory, or both.
 */
public final class ModelEntry {

    private final InstalledModel installed;
    private final LoadedModel loaded;

    ModelEntry(InstalledModel installed, LoadedModel loaded) {
        if (installed == null && loaded == null) {
            throw new IllegalArgumentException("entry needs an installed or a loaded model");
        }
        this.installed = installed;
        this.loaded = loaded;
    }

    /** Return the identity from {@code /api/tags}, falling back to {@code /api/ps} for models not on disk. */
    public ModelIdentity identity() {
        return installed != null ? installed.identity() : loaded.identity();
    }

    /** Return the model on disk, or {@code null} if {@code /api/tags} does not list it. */
    public InstalledModel installed() {
        return installed;
    }

    /** Return the model in memory, or {@code null} if it is not loaded. */
    public LoadedModel loaded() {
        return loaded;
    }

    public boolean isInstalled() {
        return installed != null;
    }

    public boolean isLoaded() {
        return loaded != null;
    }

    /** Return the reported placement, or {@code null} if the model is not loaded. */
    public ModelPlacement placement() {
        return loaded == null ? null : ModelPlacement.of(loaded);
    }

    @Override
    public String toString() {
        return "ModelEntry{" + identity() + (isInstalled() ? " installed" : "")
            + (isLoaded() ? " loaded " + placement() : "") + "}";
    }
}
