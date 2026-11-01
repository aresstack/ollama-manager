package com.aresstack.ollamamanager.swing.section;

import com.aresstack.ollamamanager.application.model.ModelEntry;
import com.aresstack.ollamamanager.domain.configuration.ManagerConfiguration;
import com.aresstack.ollamamanager.domain.hardware.GpuUsageSample;
import com.aresstack.ollamamanager.domain.hardware.HardwareSnapshot;
import com.aresstack.ollamamanager.domain.server.ServerStatus;
import com.aresstack.ollamamanager.swing.job.Job;

import java.util.List;

/**
 * What the dashboard presenter needs from its panel. All calls happen on the UI thread.
 */
public interface DashboardView {

    void showConfiguration(ManagerConfiguration configuration);

    /** @param status current status; {@code null} when no lifecycle service is wired */
    void showServerStatus(ServerStatus status);

    /** @param snapshot hardware inventory; {@code null} when no probe is wired */
    void showHardware(HardwareSnapshot snapshot);

    /** @param samples GPU usage; {@code null} when no telemetry port is wired, empty when no tool reports data */
    void showGpuUsage(List<GpuUsageSample> samples);

    /**
     * Show the models currently loaded on the running server.
     *
     * @param loaded loaded models, possibly empty; {@code null} when model management is not wired
     * @param note   why the list is empty or missing, or {@code null} when the list speaks for itself
     */
    default void showRunningModels(List<ModelEntry> loaded, String note) {
    }

    /** Decorate the running model cards with capability tags, keyed by model name. */
    default void showRunningModelCapabilities(java.util.Map<String, List<String>> capabilities) {
    }

    /** Enable or disable the Unload actions on the running model cards. */
    default void setModelActionsEnabled(boolean enabled) {
    }

    /** Enable or disable the Stop action; called immediately when a stop starts and when it ends. */
    void setStopEnabled(boolean enabled);

    /** Enable or disable the Restart action; called immediately when a stop or restart starts and ends. */
    void setRestartEnabled(boolean enabled);

    void showFailure(String action, Exception error);

    void trackJob(Job job);

    /**
     * Ask the user to confirm the stop; see {@code Dialogs.STOP_WARNING} and {@code Dialogs.EXTERNAL_STOP_WARNING}.
     *
     * @param external whether the server was not started by the manager
     */
    boolean confirmStop(boolean external);

    /**
     * Ask the user to confirm a restart with the stored settings; for an external server the restart replaces it
     * with an own server from the same installation.
     */
    boolean confirmRestart(boolean external);
}
