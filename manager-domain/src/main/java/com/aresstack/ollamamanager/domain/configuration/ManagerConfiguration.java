package com.aresstack.ollamamanager.domain.configuration;

import com.aresstack.ollamamanager.domain.common.Preconditions;
import com.aresstack.ollamamanager.domain.profile.ModelProfile;
import com.aresstack.ollamamanager.domain.server.InstallationKind;
import com.aresstack.ollamamanager.domain.server.ServerSettings;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Persisted user preferences. Contains no credentials. Schema version is {@link #FORMAT_VERSION}.
 */
public final class ManagerConfiguration {

    public static final int FORMAT_VERSION = 1;

    private static final ManagerConfiguration DEFAULTS = new ManagerConfiguration(ServerSettings.defaults(), null,
        null, Collections.<ModelProfile>emptyList(), null);

    private final ServerSettings desiredServerSettings;
    private final InstallationKind selectedInstallationKind;
    private final File selectedBinaryDirectory;
    private final List<ModelProfile> profiles;
    private final String activeProfileId;

    public ManagerConfiguration(ServerSettings desiredServerSettings, InstallationKind selectedInstallationKind,
                                File selectedBinaryDirectory, List<ModelProfile> profiles, String activeProfileId) {
        this.desiredServerSettings = Preconditions.requireNonNull(desiredServerSettings, "desiredServerSettings");
        if (selectedInstallationKind == InstallationKind.EXTERNAL_ATTACHED) {
            throw new IllegalArgumentException("an external server cannot be the selected installation");
        }
        if ((selectedInstallationKind == null) != (selectedBinaryDirectory == null)) {
            throw new IllegalArgumentException("installation kind and binary directory must be set together");
        }
        this.selectedInstallationKind = selectedInstallationKind;
        this.selectedBinaryDirectory = selectedBinaryDirectory;
        this.profiles = Collections.unmodifiableList(new ArrayList<ModelProfile>(
            Preconditions.requireNonNull(profiles, "profiles")));
        if (activeProfileId != null && findProfile(activeProfileId) == null) {
            throw new IllegalArgumentException("active profile does not exist: " + activeProfileId);
        }
        this.activeProfileId = activeProfileId;
    }

    public static ManagerConfiguration defaults() {
        return DEFAULTS;
    }

    public ServerSettings desiredServerSettings() {
        return desiredServerSettings;
    }

    /** Return the kind of the selected managed installation, or {@code null} if none is selected. */
    public InstallationKind selectedInstallationKind() {
        return selectedInstallationKind;
    }

    /** Return the selected binary directory, or {@code null} if none is selected. */
    public File selectedBinaryDirectory() {
        return selectedBinaryDirectory;
    }

    public List<ModelProfile> profiles() {
        return profiles;
    }

    /** Return the active profile id, or {@code null}. */
    public String activeProfileId() {
        return activeProfileId;
    }

    /** Return the profile with this id, or {@code null}. */
    public ModelProfile findProfile(String id) {
        for (ModelProfile profile : profiles) {
            if (profile.id().equals(id)) {
                return profile;
            }
        }
        return null;
    }

    public ManagerConfiguration withDesiredServerSettings(ServerSettings settings) {
        return new ManagerConfiguration(settings, selectedInstallationKind, selectedBinaryDirectory, profiles,
            activeProfileId);
    }

    public ManagerConfiguration withSelectedInstallation(InstallationKind kind, File binaryDirectory) {
        return new ManagerConfiguration(desiredServerSettings, kind, binaryDirectory, profiles, activeProfileId);
    }

    /** Return a copy where {@code profile} replaces the profile with the same id or is appended. */
    public ManagerConfiguration withProfile(ModelProfile profile) {
        List<ModelProfile> copy = new ArrayList<ModelProfile>();
        boolean replaced = false;
        for (ModelProfile existing : profiles) {
            if (existing.id().equals(profile.id())) {
                copy.add(profile);
                replaced = true;
            } else {
                copy.add(existing);
            }
        }
        if (!replaced) {
            copy.add(profile);
        }
        return new ManagerConfiguration(desiredServerSettings, selectedInstallationKind, selectedBinaryDirectory,
            copy, activeProfileId);
    }

    public ManagerConfiguration withActiveProfileId(String id) {
        return new ManagerConfiguration(desiredServerSettings, selectedInstallationKind, selectedBinaryDirectory,
            profiles, id);
    }
}
