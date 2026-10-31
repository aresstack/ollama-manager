package com.aresstack.ollamamanager.swing.section;

import com.aresstack.ollamamanager.application.profile.ProfileActivationService;
import com.aresstack.ollamamanager.application.profile.ProfileValidity;
import com.aresstack.ollamamanager.application.profile.ValidityApproval;
import com.aresstack.ollamamanager.application.server.ServerLifecycleService;
import com.aresstack.ollamamanager.domain.configuration.ManagerConfiguration;
import com.aresstack.ollamamanager.domain.profile.ActivationMode;
import com.aresstack.ollamamanager.domain.profile.ModelProfile;
import com.aresstack.ollamamanager.domain.profile.ProfileActivationPlan;
import com.aresstack.ollamamanager.domain.server.RuntimeOwnership;
import com.aresstack.ollamamanager.domain.server.ServerSettings;
import com.aresstack.ollamamanager.domain.server.ServerStatus;
import com.aresstack.ollamamanager.swing.job.Job;
import com.aresstack.ollamamanager.swing.job.JobContext;
import com.aresstack.ollamamanager.swing.job.JobState;
import com.aresstack.ollamamanager.swing.job.JobManager;
import com.aresstack.ollamamanager.swing.shell.ConsoleServices;

import java.util.ArrayList;
import java.util.List;

/**
 * Lists profiles with the application layer's validity evaluation (model digest, GPU fingerprint, Ollama
 * version) and activates them through {@link ProfileActivationService}. Outdated or unverifiable profiles are
 * activated only after an explicit confirmation that lists every finding; the confirmation is passed to the
 * service as a {@link ValidityApproval}, which re-checks it at activation time. The owned server is restarted only after the
 * user confirmed it; an external server's lifecycle is never touched.
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
        final ServerStatus status = currentStatus();
        final ServerSettings applied = appliedSettings(status);
        final RuntimeOwnership ownership = status == null ? RuntimeOwnership.NONE : status.ownership();
        setBusy(true);
        jobs.submit("Plan profile " + row.profile().name(), false,
            context -> profiles.plan(profileId, applied, ownership), plan -> {
                boolean restart = plan.mode() == ActivationMode.RESTART_OWNED_SERVER;
                if (restart && !view.confirmRestart(row, plan)) {
                    setBusy(false);
                    view.showNotice("Not activated: the profile needs a server restart, which was not confirmed.");
                    return;
                }
                store(row, applied, ownership, restart, confirmed);
            }, error -> {
                setBusy(false);
                view.showFailure("Plan profile", error);
            });
    }

    private void store(ProfileRow row, ServerSettings applied, RuntimeOwnership ownership, boolean restartConfirmed,
                       ValidityApproval approval) {
        final String id = row.profile().id();
        jobs.submit("Activate profile " + row.profile().name(), false,
            context -> services.profiles().activate(id, applied, ownership, restartConfirmed, approval,
                context.cancellation()), plan -> {
                if (plan.mode() == ActivationMode.RESTART_OWNED_SERVER) {
                    restart(row, plan);
                } else {
                    setBusy(false);
                    // Applied settings of an external server are unknown; never report them as matching.
                    view.showNotice(ownership == RuntimeOwnership.EXTERNAL ? EXTERNAL_NOTICE : noticeFor(plan.mode()));
                    refresh();
                }
            }, error -> {
                setBusy(false);
                view.showFailure("Activate profile", error);
                refresh();
            });
    }

    private void restart(ProfileRow row, ProfileActivationPlan plan) {
        final ServerLifecycleService lifecycle = services.serverLifecycle();
        Job job = jobs.submit("Restart server for " + row.profile().name(), true,
            (JobContext context) -> lifecycle.restart(plan.targetSettings(), context.cancellation()), status -> {
                setBusy(false);
                view.showNotice("Profile active. The server was restarted with the profile's settings.");
                refresh();
            }, error -> {
                setBusy(false);
                view.showNotice(RESTART_FAILED);
                view.showFailure("Restart server", error);
                refresh();
            });
        job.addListener(current -> {
            if (current.state() == JobState.CANCELLED && busy) {
                setBusy(false);
                view.showNotice(RESTART_CANCELLED);
                refresh();
            }
        });
        view.trackJob(job);
    }

    static final String RESTART_FAILED = "Profile stored as active, but the restart failed. The server does not"
        + " use the profile's settings yet.";
    static final String RESTART_CANCELLED = "Restart cancelled. The profile is stored as active, but the server"
        + " may be stopped and does not use the profile's settings. Check the dashboard.";
    static final String EXTERNAL_NOTICE = "Profile stored as active. The external server is not changed, and its"
        + " actual settings are unknown, so the manager cannot apply or verify the profile's server settings.";

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

    /** Ask the application layer for each profile's validity; the UI does not judge validity itself. */
    private List<ProfileRow> loadRows(JobContext context) throws Exception {
        ManagerConfiguration configuration = services.configuration().current();
        ProfileActivationService profiles = services.profiles();
        List<ProfileRow> result = new ArrayList<ProfileRow>();
        for (ModelProfile profile : configuration.profiles()) {
            context.cancellation().throwIfCancelled();
            ProfileValidity validity = profiles == null ? null
                : profiles.validity(profile.id(), context.cancellation());
            result.add(new ProfileRow(profile, validity, profile.id().equals(configuration.activeProfileId())));
        }
        return result;
    }

    private ServerStatus currentStatus() {
        return services.serverLifecycle() == null ? null : services.serverLifecycle().status();
    }

    /**
     * Settings the plan compares against: those of the running owned server. For an external or no server the
     * applied settings are unknown, so Ollama's defaults stand in; the plan mode for those cases never restarts.
     */
    private ServerSettings appliedSettings(ServerStatus status) {
        ServerLifecycleService lifecycle = services.serverLifecycle();
        ServerSettings applied = lifecycle == null ? null : lifecycle.appliedSettings();
        return status != null && status.ownership() == RuntimeOwnership.OWNED && applied != null
            ? applied : ServerSettings.defaults();
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
