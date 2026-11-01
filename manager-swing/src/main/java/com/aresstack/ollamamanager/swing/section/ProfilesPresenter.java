package com.aresstack.ollamamanager.swing.section;

import com.aresstack.ollamamanager.application.common.OperationException;
import com.aresstack.ollamamanager.application.profile.ProfileActivationService;
import com.aresstack.ollamamanager.application.profile.ProfileValidity;
import com.aresstack.ollamamanager.application.profile.ValidityApproval;
import com.aresstack.ollamamanager.domain.configuration.ManagerConfiguration;
import com.aresstack.ollamamanager.domain.profile.ActivationMode;
import com.aresstack.ollamamanager.domain.profile.ModelProfile;
import com.aresstack.ollamamanager.swing.job.Job;
import com.aresstack.ollamamanager.swing.job.JobContext;
import com.aresstack.ollamamanager.swing.job.JobState;
import com.aresstack.ollamamanager.swing.job.JobManager;
import com.aresstack.ollamamanager.swing.shell.ConsoleServices;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Lists profiles with the application layer's validity evaluation (model digest, GPU fingerprint, Ollama
 * version) and activates them through {@link ProfileActivationService}. Outdated or unverifiable profiles are
 * activated only after an explicit confirmation that lists every finding; the confirmation is passed to the
 * service as a {@link ValidityApproval}, which re-checks it at activation time. The service reads the server
 * state itself and restarts the owned server, under the lifecycle lock, only after the user confirmed it; an
 * external server's lifecycle is never touched.
 */
public final class ProfilesPresenter {

    static final String NOT_WIRED = "Profiles are not connected in this build.";

    private final ConsoleServices services;
    private final JobManager jobs;
    private ProfilesView view;
    private List<ProfileRow> rows = new ArrayList<ProfileRow>();
    private boolean busy;
    private long refreshGeneration;

    public ProfilesPresenter(ConsoleServices services, JobManager jobs) {
        this.services = services;
        this.jobs = jobs;
    }

    public void bind(ProfilesView boundView) {
        this.view = boundView;
    }

    public void refresh() {
        final long generation = ++refreshGeneration;
        jobs.submit("Check profiles", true, this::loadRows, loaded -> {
            if (generation == refreshGeneration) {
                rows = loaded;
                view.showProfiles(loaded);
            }
        }, error -> {
            if (generation == refreshGeneration) {
                view.showFailure("Check profiles", error);
            }
        });
    }

    /** Activate the profile with {@code profileId} after all required confirmations. */
    public void activate(String profileId) {
        final ProfileActivationService profiles = services.profiles();
        if (busy || profiles == null) {
            if (profiles == null) {
                view.showNotice(NOT_WIRED);
            }
            return;
        }
        final ProfileRow row = find(profileId);
        if (row == null) {
            return;
        }
        ValidityApproval approval = null;
        ProfileValidity validity = row.validity();
        if (validity == null || validity.result() != ProfileValidity.Result.VALID) {
            if (validity == null || !view.confirmUnverifiedActivation(row)) {
                return;
            }
            // The service re-evaluates at activation time and accepts this only if nothing changed meanwhile.
            approval = new ValidityApproval(row.profile().id(), row.profile().revision(), validity.result(),
                validity.findings());
        }
        final ValidityApproval confirmed = approval;
        setBusy(true);
        // The service reads the server state itself; STARTING or STOPPING fails here with INVALID_STATE.
        jobs.submit("Plan profile " + row.profile().name(), false, context -> profiles.plan(profileId), plan -> {
            boolean restart = plan.mode() == ActivationMode.RESTART_OWNED_SERVER;
            if (restart && !view.confirmRestart(row, plan)) {
                setBusy(false);
                view.showNotice("Not activated: the profile needs a server restart, which was not confirmed.");
                return;
            }
            commit(row, plan.mode(), restart, confirmed);
        }, error -> {
            setBusy(false);
            view.showFailure("Plan profile", error);
        });
    }

    /**
     * Store the profile and, for a confirmed restart, restart the owned server, all in one application call under
     * the lifecycle lock. It fails with INVALID_STATE if the server state no longer yields the confirmed mode.
     */
    private void commit(ProfileRow row, ActivationMode confirmedMode, boolean restartConfirmed,
                        ValidityApproval approval) {
        final String id = row.profile().id();
        // The user confirmed this revision; a profile revised since then is rejected by the service.
        final int revision = row.profile().revision();
        // Set in the background when activation ends without success; read on the UI thread afterwards.
        final AtomicBoolean storedBeforeFailure = new AtomicBoolean();
        Job job = jobs.submit("Activate profile " + row.profile().name(), restartConfirmed, context -> {
            try {
                return services.profiles().activate(id, revision, confirmedMode, restartConfirmed, approval,
                    context.cancellation());
            } catch (Exception e) {
                storedBeforeFailure.set(isActive(id));
                throw e;
            }
        }, plan -> {
                setBusy(false);
                view.showNotice(outcomeNotice(plan.mode()));
                refresh();
            }, error -> {
                setBusy(false);
                if (restartConfirmed && storedBeforeFailure.get()) {
                    // The profile is stored before the restart, so only the restart failed.
                    view.showNotice(RESTART_FAILED);
                    view.showFailure("Restart server", error);
                } else {
                    view.showFailure("Activate profile", error);
                }
                refresh();
            });
        if (restartConfirmed) {
            job.addListener(current -> {
                if (current.state() == JobState.CANCELLED && busy) {
                    setBusy(false);
                    view.showNotice(storedBeforeFailure.get() ? RESTART_CANCELLED : ACTIVATION_CANCELLED);
                    refresh();
                }
            });
            view.trackJob(job);
        }
    }

    /** Read in the background only; an unreadable configuration counts as not stored. */
    private boolean isActive(String profileId) {
        try {
            return profileId.equals(services.configuration().current().activeProfileId());
        } catch (OperationException e) {
            return false;
        }
    }

    static String outcomeNotice(ActivationMode mode) {
        if (mode == ActivationMode.RESTART_OWNED_SERVER) {
            return "Profile active. The server was restarted with the profile's settings.";
        }
        // Applied settings of an external server are unknown; never report them as matching.
        return mode == ActivationMode.CANNOT_APPLY_TO_EXTERNAL ? EXTERNAL_NOTICE : noticeFor(mode);
    }

    static final String RESTART_FAILED = "Profile stored as active, but the restart failed. The server does not"
        + " use the profile's settings yet.";
    static final String RESTART_CANCELLED = "Restart cancelled. The profile is stored as active, but the server"
        + " may be stopped and does not use the profile's settings. Check the dashboard.";
    static final String ACTIVATION_CANCELLED = "Activation cancelled. Nothing was stored.";
    static final String EXTERNAL_NOTICE = "Profile stored as active. Its server settings apply when the manager"
        + " starts or restarts the server; use Restart in Setup or on the dashboard to apply them to this server.";

    static String noticeFor(ActivationMode mode) {
        switch (mode) {
            case REQUEST_OPTIONS_ONLY:
                return "Profile active. The server already runs with matching settings.";
            case CANNOT_APPLY_TO_EXTERNAL:
                return "Profile stored as active. The external server is not changed; its server-wide settings"
                    + " apply only to the manager's own server.";
            case APPLY_ON_NEXT_START:
                return "Profile stored as active. Its settings apply when the server is started next.";
            default:
                return "Profile active.";
        }
    }

    /** Ask the application layer for all validities against one environment capture; the UI does not judge. */
    private List<ProfileRow> loadRows(JobContext context) throws Exception {
        ProfileActivationService profiles = services.profiles();
        Map<String, ProfileValidity> validities = profiles == null
            ? Collections.<String, ProfileValidity>emptyMap() : profiles.validities(context.cancellation());
        ManagerConfiguration configuration = services.configuration().current();
        List<ProfileRow> result = new ArrayList<ProfileRow>();
        for (ModelProfile profile : configuration.profiles()) {
            result.add(new ProfileRow(profile, validities.get(profile.id()),
                profile.id().equals(configuration.activeProfileId())));
        }
        return result;
    }

    private ProfileRow find(String profileId) {
        for (ProfileRow row : rows) {
            if (row.profile().id().equals(profileId)) {
                return row;
            }
        }
        return null;
    }

    boolean isBusy() {
        return busy;
    }

    private void setBusy(boolean value) {
        busy = value;
        view.setBusy(value);
    }
}
