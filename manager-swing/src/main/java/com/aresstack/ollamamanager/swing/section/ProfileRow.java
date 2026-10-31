package com.aresstack.ollamamanager.swing.section;

import com.aresstack.ollamamanager.domain.profile.ModelProfile;

/**
 * One profile as shown in the list: the stored profile, its validity check and whether it is active.
 */
public final class ProfileRow {

    private final ModelProfile profile;
    private final ProfileCheck check;
    private final boolean active;

    ProfileRow(ModelProfile profile, ProfileCheck check, boolean active) {
        this.profile = profile;
        this.check = check;
        this.active = active;
    }

    public ModelProfile profile() {
        return profile;
    }

    public ProfileCheck check() {
        return check;
    }

    public boolean isActive() {
        return active;
    }
}
