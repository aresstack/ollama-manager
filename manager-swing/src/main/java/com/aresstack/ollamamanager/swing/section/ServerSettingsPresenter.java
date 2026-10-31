package com.aresstack.ollamamanager.swing.section;

import com.aresstack.ollamamanager.application.common.ErrorKind;
import com.aresstack.ollamamanager.application.common.OperationException;
import com.aresstack.ollamamanager.application.server.ServerLifecycleService;
import com.aresstack.ollamamanager.domain.configuration.ManagerConfiguration;
import com.aresstack.ollamamanager.domain.server.RuntimeOwnership;
import com.aresstack.ollamamanager.domain.server.ServerSetting;
import com.aresstack.ollamamanager.domain.server.ServerSettings;
import com.aresstack.ollamamanager.domain.server.ServerStatus;
import com.aresstack.ollamamanager.swing.job.JobManager;
import com.aresstack.ollamamanager.swing.shell.ConsoleServices;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Loads and saves the desired server settings. Every write goes through {@code ConfigurationService.update},
 * and a save is rejected when the settings changed since they were loaded, so concurrent edits are not lost.
 */
public final class ServerSettingsPresenter {

    static final String SAVED = "Saved.";
    static final String SAVED_RESTART = "Saved. Restart the server to apply the changes; running requests are"
        + " aborted on restart.";
    static final String RUNNING_OWNED = "The right column shows what the running server started by the manager uses.";
    static final String NO_OWNED_SERVER = "No server started by the manager is running; stored values apply on"
        + " the next start.";
    static final String EXTERNAL_UNKNOWN = "External server: its actual settings are unknown and are not changed"
        + " by the manager.";
    static final String CHANGED_MEANWHILE = "The server settings were changed elsewhere since they were loaded."
        + " Reload and apply your changes again.";

    private final ConsoleServices services;
    private final JobManager jobs;
    private ServerSettingsView view;
    private ServerSettings loaded;
    private boolean busy;

    public ServerSettingsPresenter(ConsoleServices services, JobManager jobs) {
        this.services = services;
        this.jobs = jobs;
    }

    public void bind(ServerSettingsView boundView) {
        this.view = boundView;
    }

    public void load() {
        if (busy) {
            return;
        }
        setBusy(true);
        jobs.submit("Load server settings", false, context -> services.configuration().current(),
            configuration -> {
                setBusy(false);
                show(configuration.desiredServerSettings());
                view.showNotice(null);
            }, error -> {
                setBusy(false);
                view.showFailure("Load server settings", error);
            });
    }

    /** Validate the form, then save through the configuration service. */
    public void save() {
        if (busy || loaded == null) {
            return;
        }
        SettingsDraft draft = SettingsDraft.parse(view.editedValues());
        view.showErrors(draft.errors());
        if (!draft.isValid()) {
            view.showNotice("Fix the marked fields before saving.");
            return;
        }
        final ServerSettings base = loaded;
        final ServerSettings desired = draft.settings();
        setBusy(true);
        jobs.submit("Save server settings", false, context -> services.configuration().update(current -> {
            if (!current.desiredServerSettings().equals(base)) {
                throw new OperationException(ErrorKind.INVALID_STATE, CHANGED_MEANWHILE);
            }
            return current.withDesiredServerSettings(desired);
        }), (ManagerConfiguration saved) -> {
            setBusy(false);
            show(saved.desiredServerSettings());
            ServerLifecycleService lifecycle = services.serverLifecycle();
            boolean restart = lifecycle != null && lifecycle.restartRequired(saved.desiredServerSettings());
            view.showNotice(restart ? SAVED_RESTART : SAVED);
        }, error -> {
            setBusy(false);
            view.showFailure("Save server settings", error);
        });
    }

    /** Discard edits and show the last loaded values again. */
    public void revert() {
        if (loaded != null && !busy) {
            show(loaded);
            view.showNotice(null);
        }
    }

    private void show(ServerSettings settings) {
        loaded = settings;
        view.showErrors(Collections.emptyMap());
        view.showValues(SettingsDraft.texts(settings));
        showApplied(settings);
    }

    /** Keep stored and running values apart; a stored change that needs a restart is never shown as active. */
    private void showApplied(ServerSettings stored) {
        ServerLifecycleService lifecycle = services.serverLifecycle();
        ServerStatus status = lifecycle == null ? null : lifecycle.status();
        ServerSettings applied = lifecycle == null ? null : lifecycle.appliedSettings();
        if (status == null || status.ownership() != RuntimeOwnership.OWNED || applied == null) {
            String source = status != null && status.ownership() == RuntimeOwnership.EXTERNAL
                ? EXTERNAL_UNKNOWN : NO_OWNED_SERVER;
            view.showApplied(null, Collections.<ServerSetting<?>>emptySet(), source);
            return;
        }
        Set<ServerSetting<?>> pending = new LinkedHashSet<ServerSetting<?>>();
        for (ServerSetting<?> setting : stored.differencesTo(applied)) {
            if (setting.isRestartRequired()) {
                pending.add(setting);
            }
        }
        view.showApplied(SettingsDraft.texts(applied), pending, RUNNING_OWNED);
    }

    private void setBusy(boolean value) {
        busy = value;
        view.setBusy(value);
    }
}
