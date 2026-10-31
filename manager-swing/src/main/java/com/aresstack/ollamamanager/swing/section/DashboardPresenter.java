package com.aresstack.ollamamanager.swing.section;

import com.aresstack.ollamamanager.application.server.ServerLifecycleService;
import com.aresstack.ollamamanager.domain.server.ServerStatus;
import com.aresstack.ollamamanager.swing.job.Job;
import com.aresstack.ollamamanager.swing.job.JobManager;
import com.aresstack.ollamamanager.swing.shell.ConsoleServices;

/**
 * Loads dashboard data through the application services and runs the stop use case.
 */
public final class DashboardPresenter {

    private final ConsoleServices services;
    private final JobManager jobs;
    private DashboardView view;

    public DashboardPresenter(ConsoleServices services, JobManager jobs) {
        this.services = services;
        this.jobs = jobs;
    }

    public void bind(DashboardView boundView) {
        this.view = boundView;
    }

    /** Reload everything; each part loads independently so one failure does not hide the rest. */
    public void refresh() {
        jobs.submit("Load configuration", false, context -> services.configuration().current(),
            view::showConfiguration, error -> view.showFailure("Load configuration", error));

        ServerLifecycleService lifecycle = services.serverLifecycle();
        view.showServerStatus(lifecycle == null ? null : lifecycle.status());

        if (services.hardwareProbe() == null) {
            view.showHardware(null);
        } else {
            view.trackJob(jobs.submit("Detect hardware", true,
                context -> services.hardwareProbe().probe(context.cancellation()),
                view::showHardware, error -> view.showFailure("Detect hardware", error)));
        }
        refreshGpuUsage();
    }

    /** Sample GPU usage via the telemetry port only. */
    public void refreshGpuUsage() {
        if (services.gpuTelemetry() == null) {
            view.showGpuUsage(null);
            return;
        }
        jobs.submit("Read GPU usage", true, context -> services.gpuTelemetry().sample(context.cancellation()),
            view::showGpuUsage, error -> view.showFailure("Read GPU usage", error));
    }

    /** Whether the Stop action may be offered for {@code status}. */
    public boolean canStop(ServerStatus status) {
        return services.serverLifecycle() != null && status != null && status.canBeStoppedByManager();
    }

    /** Stop the owned server after the user confirmed the hard-kill warning. */
    public void stopRequested() {
        final ServerLifecycleService lifecycle = services.serverLifecycle();
        if (lifecycle == null || !canStop(lifecycle.status()) || !view.confirmStop()) {
            return;
        }
        Job job = jobs.submit("Stop server", false, context -> lifecycle.stop(),
            view::showServerStatus, error -> {
                view.showFailure("Stop server", error);
                view.showServerStatus(lifecycle.status());
            });
        view.trackJob(job);
    }
}
