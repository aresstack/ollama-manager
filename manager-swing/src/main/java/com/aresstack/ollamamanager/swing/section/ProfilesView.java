package com.aresstack.ollamamanager.swing.section;

import com.aresstack.ollamamanager.domain.profile.ProfileActivationPlan;
import com.aresstack.ollamamanager.swing.job.Job;

import java.util.List;

/**
 * What the profiles presenter needs from its panel. All calls happen on the UI thread.
 */
public interface ProfilesView {

    void showProfiles(List<ProfileRow> rows);

    void setBusy(boolean busy);

    void showNotice(String notice);

    void showFailure(String action, Exception error);

    void trackJob(Job job);

    /** Ask whether to activate a profile that is outdated or cannot be verified; lists every finding. */
    boolean confirmUnverifiedActivation(ProfileRow row);

    /** Ask whether to restart the owned server for this plan; warns that running requests are aborted. */
    boolean confirmRestart(ProfileRow row, ProfileActivationPlan plan);
}
