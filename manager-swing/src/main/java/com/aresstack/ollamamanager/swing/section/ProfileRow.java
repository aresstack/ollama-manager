package com.aresstack.ollamamanager.swing.section;

import com.aresstack.ollamamanager.application.profile.ProfileValidity;
import com.aresstack.ollamamanager.domain.profile.ModelProfile;

/**
 * One profile as shown in the list: the stored profile, its validity evaluation and whether it is active.
 */
public final class ProfileRow {

    private final ModelProfile profile;
    private final ProfileValidity validity;
    private final boolean active;

    ProfileRow(ModelProfile profile, ProfileValidity validity, boolean active) {
        this.profile = profile;
        this.validity = validity;
        this.active = active;
    }

    public ModelProfile profile() {
        return profile;
    }

    /** @return the application layer's validity evaluation, or {@code null} when profiles are not wired */
    public ProfileValidity validity() {
        return validity;
    }

    public boolean isActive() {
        return active;
    }
}
