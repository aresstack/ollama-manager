package com.aresstack.ollamamanager.swing.section;

import com.aresstack.ollamamanager.application.configuration.ConfigurationService;
import com.aresstack.ollamamanager.application.port.ConfigurationRepository;
import com.aresstack.ollamamanager.domain.configuration.ManagerConfiguration;
import com.aresstack.ollamamanager.domain.hardware.GpuUsageSample;
import com.aresstack.ollamamanager.domain.hardware.HardwareSnapshot;
import com.aresstack.ollamamanager.domain.server.ServerStatus;
import com.aresstack.ollamamanager.swing.job.Job;
import com.aresstack.ollamamanager.swing.job.JobManager;
import com.aresstack.ollamamanager.swing.job.UiDispatcher;
import com.aresstack.ollamamanager.swing.shell.ConsoleServices;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class DashboardPresenterTest {

    private final JobManager jobs = new JobManager(Runnable::run, UiDispatcher.inline());
    private final RecordingView view = new RecordingView();

    @Test
    public void unwiredServicesAreShownAsMissingNotAsData() {
        DashboardPresenter presenter = new DashboardPresenter(services().build(), jobs);
        presenter.bind(view);

        presenter.refresh();

        assertNotNull(view.configuration);
        assertTrue(view.events.contains("status:null"));
        assertTrue(view.events.contains("hardware:null"));
        assertTrue(view.events.contains("gpu:null"));
        assertFalse(presenter.canStop(ServerStatus.stopped()));
    }

    @Test
    public void emptyTelemetryIsDistinctFromUnwired() {
        DashboardPresenter presenter = new DashboardPresenter(
            services().gpuTelemetry(cancellation -> Collections.<GpuUsageSample>emptyList()).build(), jobs);
        presenter.bind(view);

        presenter.refreshGpuUsage();

        assertEquals("[gpu:0]", view.events.toString());
    }

    @Test
    public void stopWithoutLifecycleNeverAsksOrRuns() {
        DashboardPresenter presenter = new DashboardPresenter(services().build(), jobs);
        presenter.bind(view);

        presenter.stopRequested();

        assertEquals(0, view.confirmations);
        assertTrue(jobs.jobs().isEmpty());
    }

    private static ConsoleServices.Builder services() {
        ConfigurationRepository repository = new ConfigurationRepository() {
            private ManagerConfiguration stored = ManagerConfiguration.defaults();

            @Override
            public ManagerConfiguration load() {
                return stored;
            }

            @Override
            public void save(ManagerConfiguration configuration) {
                stored = configuration;
            }
        };
        return ConsoleServices.builder(new ConfigurationService(repository));
    }

    private static final class RecordingView implements DashboardView {
        final List<String> events = new ArrayList<String>();
        ManagerConfiguration configuration;
        int confirmations;

        @Override
        public void showConfiguration(ManagerConfiguration value) {
            configuration = value;
        }

        @Override
        public void showServerStatus(ServerStatus status) {
            events.add("status:" + status);
        }

        @Override
        public void showHardware(HardwareSnapshot snapshot) {
            events.add("hardware:" + snapshot);
        }

        @Override
        public void showGpuUsage(List<GpuUsageSample> samples) {
            events.add("gpu:" + (samples == null ? "null" : String.valueOf(samples.size())));
        }

        @Override
        public void showFailure(String action, Exception error) {
            events.add("failure:" + action);
        }

        @Override
        public void trackJob(Job job) {
        }

        @Override
        public boolean confirmStop() {
            confirmations++;
            return true;
        }
    }
}
