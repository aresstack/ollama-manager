package com.aresstack.ollamamanager.swing.section;

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

    void showFailure(String action, Exception error);

    void trackJob(Job job);

    /** Ask the user to confirm the hard stop; see {@code Dialogs.STOP_WARNING}. */
    boolean confirmStop();
}
