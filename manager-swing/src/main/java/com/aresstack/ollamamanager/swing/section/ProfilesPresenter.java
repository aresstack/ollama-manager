package com.aresstack.ollamamanager.swing.section;

import com.aresstack.ollamamanager.application.model.ModelEntry;
import com.aresstack.ollamamanager.application.profile.ProfileActivationService;
import com.aresstack.ollamamanager.application.server.ServerLifecycleService;
import com.aresstack.ollamamanager.domain.configuration.ManagerConfiguration;
import com.aresstack.ollamamanager.domain.hardware.GpuFingerprint;
import com.aresstack.ollamamanager.domain.model.ModelIdentity;
import com.aresstack.ollamamanager.domain.profile.ActivationMode;
import com.aresstack.ollamamanager.domain.profile.ModelProfile;
import com.aresstack.ollamamanager.domain.profile.ProfileActivationPlan;
import com.aresstack.ollamamanager.domain.server.RuntimeOwnership;
import com.aresstack.ollamamanager.domain.server.ServerSettings;
import com.aresstack.ollamamanager.domain.server.ServerState;
import com.aresstack.ollamamanager.domain.server.ServerStatus;
import com.aresstack.ollamamanager.swing.job.JobContext;
import com.aresstack.ollamamanager.swing.job.JobManager;
import com.aresstack.ollamamanager.swing.shell.ConsoleServices;

import java.util.ArrayList;
import java.util.List;

/**
 * Lists profiles with a validity check against the current model digest, GPU fingerprint and Ollama version,
 * and activates them through {@link ProfileActivationService}. Outdated or unverifiable profiles are activated
 * only after an explicit confirmation that lists every finding. The owned server is restarted only after the
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
        if (row.check().result() != ProfileCheck.Result.VALID && !view.confirmUnverifiedActivation(row)) {
            return;
        }
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
                store(row, applied, ownership, restart);
            }, error -> {
                setBusy(false);
                view.showFailure("Plan profile", error);
            });
    }

    private void store(ProfileRow row, ServerSettings applied, RuntimeOwnership ownership, boolean restartConfirmed) {
        final String id = row.profile().id();
        jobs.submit("Activate profile " + row.profile().name(), false,
            context -> services.profiles().activate(id, applied, ownership, restartConfirmed), plan -> {
                if (plan.mode() == ActivationMode.RESTART_OWNED_SERVER) {
                    restart(row, plan);
                } else {
                    setBusy(false);
                    view.showNotice(noticeFor(plan.mode()));
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
        view.trackJob(jobs.submit("Restart server for " + row.profile().name(), true,
            (JobContext context) -> lifecycle.restart(plan.targetSettings(), context.cancellation()), status -> {
                setBusy(false);
                view.showNotice("Profile active. The server was restarted with the profile's settings.");
                refresh();
            }, error -> {
                setBusy(false);
                view.showNotice("Profile stored as active, but the restart failed. The server does not use the"
                    + " profile's settings yet.");
                view.showFailure("Restart server", error);
                refresh();
            }));
    }

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

    private List<ProfileRow> loadRows(JobContext context) throws Exception {
        ManagerConfiguration configuration = services.configuration().current();
        context.cancellation().throwIfCancelled();
        GpuFingerprint fingerprint = services.hardwareProbe() == null ? null
            : GpuFingerprint.of(services.hardwareProbe().probe(context.cancellation()).gpus());
        ServerStatus status = currentStatus();
        boolean running = status != null && status.state() == ServerState.RUNNING && status.installation() != null;
        String version = running ? status.reportedVersion() : null;
        List<ModelIdentity> installed = null;
        if (running && services.models() != null) {
            installed = new ArrayList<ModelIdentity>();
            for (ModelEntry entry : services.models().inventory(status.installation().endpoint(),
                context.cancellation())) {
                if (entry.isInstalled()) {
                    installed.add(entry.identity());
                }
            }
        }
        List<ProfileRow> result = new ArrayList<ProfileRow>();
        for (ModelProfile profile : configuration.profiles()) {
            result.add(new ProfileRow(profile, ProfileCheck.of(profile, fingerprint, version, installed),
                profile.id().equals(configuration.activeProfileId())));
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
