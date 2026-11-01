package com.aresstack.ollamamanager.swing.section;

import com.aresstack.ollamamanager.application.common.ErrorKind;
import com.aresstack.ollamamanager.application.common.OperationCancelledException;
import com.aresstack.ollamamanager.application.common.OperationException;
import com.aresstack.ollamamanager.application.configuration.ExistingSettingsImport;
import com.aresstack.ollamamanager.application.configuration.PermanentEnvironmentService;
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
import java.util.Map;
import java.util.Set;

/**
 * Loads and saves the desired server settings. Every write goes through {@code ConfigurationService.update},
 * and a save is rejected when the settings changed since they were loaded, so concurrent edits are not lost.
 */
public final class ServerSettingsPresenter {

    static final String SAVED = "Saved.";
    static final String SAVED_RESTART = "Saved. Restart the server to apply the changes; running requests are"
        + " aborted on restart.";
    static final String SAVED_PERMANENT = "Saved and written to the user environment variables. The Ollama app"
        + " uses them the next time it starts.";
    static final String RESTART_HINT = "Restart the server to apply the changes; running requests are aborted on"
        + " restart.";
    static final String NOTHING_TO_PERSIST = "No setting is set, so nothing is written to the environment variables.";
    static final String RUNNING_OWNED = "The right column shows what the running server started by the manager uses.";
    static final String NO_OWNED_SERVER = "No server started by the manager is running; stored values apply on"
        + " the next start.";
    static final String EXTERNAL_UNKNOWN = "External server: its running values are unknown. Stored values apply"
        + " when the manager starts a server; use Restart on the dashboard to restart this installation with them.";
    static final String SERVER_FAILED = "The server started by the manager is not running (it exited"
        + " unexpectedly); stored values apply on the next start.";
    static final String SERVER_TRANSITION = "The server is starting or stopping; its running values are shown once"
        + " it runs.";
    static final String CHANGED_MEANWHILE = "The server settings were changed elsewhere since they were loaded."
        + " Reload and apply your changes again.";

    private final ConsoleServices services;
    private final JobManager jobs;
    private ServerSettingsView view;
    private ServerSettings loaded;
    private ExistingSettingsImport.Detected detected;
    private String detectedSource;
    private boolean busy;

    public ServerSettingsPresenter(ConsoleServices services, JobManager jobs) {
        this.services = services;
        this.jobs = jobs;
    }

    public void bind(ServerSettingsView boundView) {
        this.view = boundView;
        PermanentEnvironmentService permanent = services.permanentEnvironment();
        view.setPermanentTarget(permanent != null && permanent.isSupported() ? permanent.targetDescription() : null);
    }

    public void load() {
        if (busy) {
            return;
        }
        setBusy(true);
        jobs.submit("Load server settings", false, context -> {
            ExistingSettingsImport existing = services.existingSettings();
            ExistingSettingsImport.Detected detected = null;
            if (existing != null) {
                try {
                    existing.importUnchosen(context.cancellation());
                    detected = existing.detect(context.cancellation());
                } catch (OperationCancelledException e) {
                    throw e;
                } catch (OperationException e) {
                    // Reading the existing installation is optional; the stored settings are shown anyway.
                    detected = null;
                }
            }
            return new Loaded(services.configuration().current(), detected,
                existing == null ? null : existing.sourceDescription());
        }, result -> {
                setBusy(false);
                this.detected = result.detected;
                this.detectedSource = result.source;
                show(result.configuration.desiredServerSettings());
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

    /**
     * Save like {@link #save()}, then write every set value as an {@code OLLAMA_*} variable into the user
     * environment, overwriting existing values, so an Ollama started without the manager uses them too. Unset
     * settings write nothing and leave an existing variable alone.
     */
    public void savePermanently() {
        final PermanentEnvironmentService permanent = services.permanentEnvironment();
        if (busy || loaded == null || permanent == null || !permanent.isSupported()) {
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
        Map<String, String> variables = desired.toEnvironment();
        if (variables.isEmpty()) {
            view.showNotice(NOTHING_TO_PERSIST);
            return;
        }
        if (!view.confirmPermanent(confirmText(variables, permanent.targetDescription()))) {
            return;
        }
        setBusy(true);
        jobs.submit("Save server settings permanently", false, context -> {
            ManagerConfiguration saved = services.configuration().update(current -> {
                if (!current.desiredServerSettings().equals(base)) {
                    throw new OperationException(ErrorKind.INVALID_STATE, CHANGED_MEANWHILE);
                }
                return current.withDesiredServerSettings(desired);
            });
            permanent.persist(saved.desiredServerSettings(), context.cancellation());
            return saved;
        }, (ManagerConfiguration saved) -> {
                setBusy(false);
                show(saved.desiredServerSettings());
                ServerLifecycleService lifecycle = services.serverLifecycle();
                boolean restart = lifecycle != null && lifecycle.restartRequired(saved.desiredServerSettings());
                view.showNotice(SAVED_PERMANENT + (restart ? " " + RESTART_HINT : ""));
            }, error -> {
                setBusy(false);
                view.showFailure("Save server settings permanently", error);
            });
    }

    static String confirmText(Map<String, String> variables, String target) {
        StringBuilder text = new StringBuilder("Write these values into the ").append(target)
            .append("?\nExisting values are overwritten. Unset settings are not written.\n\n");
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            text.append(entry.getKey()).append(" = ").append(entry.getValue()).append('\n');
        }
        return text.append("\nThe Ollama app and other programs use them the next time they start.").toString();
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
        showImported(settings);
    }

    /** Mark stored values that equal what the existing installation is configured with. */
    private void showImported(ServerSettings settings) {
        Set<ServerSetting<?>> imported = new LinkedHashSet<ServerSetting<?>>();
        if (detected != null) {
            for (ServerSetting<?> setting : detected.values().keySet()) {
                if (detected.matches(settings, setting)) {
                    imported.add(setting);
                }
            }
        }
        view.showImported(imported, detectedSource);
    }

    private static final class Loaded {
        final ManagerConfiguration configuration;
        final ExistingSettingsImport.Detected detected;
        final String source;

        Loaded(ManagerConfiguration configuration, ExistingSettingsImport.Detected detected, String source) {
            this.configuration = configuration;
            this.detected = detected;
            this.source = source;
        }
    }

    /** Keep stored and running values apart; a stored change that needs a restart is never shown as active. */
    private void showApplied(ServerSettings stored) {
        ServerLifecycleService lifecycle = services.serverLifecycle();
        ServerStatus status = lifecycle == null ? null : lifecycle.status();
        ServerSettings applied = lifecycle == null ? null : lifecycle.appliedSettings();
        String source = sourceFor(status, applied);
        if (source != RUNNING_OWNED) {
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

    /**
     * Running values are shown only for a RUNNING owned server. A FAILED process keeps its ownership and last
     * settings, but nothing runs with them any more.
     */
    private static String sourceFor(ServerStatus status, ServerSettings applied) {
        if (status == null) {
            return NO_OWNED_SERVER;
        }
        switch (status.state()) {
            case STARTING:
            case STOPPING:
                return SERVER_TRANSITION;
            case FAILED:
                return status.ownership() == RuntimeOwnership.OWNED ? SERVER_FAILED : NO_OWNED_SERVER;
            case RUNNING:
                if (status.ownership() == RuntimeOwnership.EXTERNAL) {
                    return EXTERNAL_UNKNOWN;
                }
                return status.ownership() == RuntimeOwnership.OWNED && applied != null
                    ? RUNNING_OWNED : NO_OWNED_SERVER;
            default:
                return NO_OWNED_SERVER;
        }
    }

    private void setBusy(boolean value) {
        busy = value;
        view.setBusy(value);
    }
}
