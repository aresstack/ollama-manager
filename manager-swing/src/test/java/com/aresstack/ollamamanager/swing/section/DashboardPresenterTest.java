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
import com.aresstack.ollamamanager.application.common.CancellationToken;
import com.aresstack.ollamamanager.application.port.ManagedServer;
import com.aresstack.ollamamanager.application.port.OllamaRuntimePort;
import com.aresstack.ollamamanager.application.server.ServerLifecycleService;
import com.aresstack.ollamamanager.domain.server.InstallationKind;
import com.aresstack.ollamamanager.domain.server.OllamaInstallation;
import com.aresstack.ollamamanager.domain.server.ServerSettings;
import com.aresstack.ollamamanager.domain.server.ServerState;
import org.junit.Test;

import java.io.File;

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

    @Test
    public void stopDisablesButtonAtOnceAndIgnoresSecondClick() throws Exception {
        List<Runnable> pending = new ArrayList<Runnable>();
        JobManager deferred = new JobManager(pending::add, UiDispatcher.inline());
        ServerLifecycleService lifecycle = new ServerLifecycleService(new FakeRuntime(), 0);
        lifecycle.start(new OllamaInstallation(InstallationKind.MANAGED_PORTABLE, "0.1.0", new File("ollama").getAbsoluteFile(),
            ServerSettings.defaults().bind()), ServerSettings.defaults(), CancellationToken.none());
        DashboardPresenter presenter = new DashboardPresenter(services().serverLifecycle(lifecycle).build(), deferred);
        presenter.bind(view);
        assertTrue(presenter.canStop(lifecycle.status()));

        presenter.stopRequested();
        assertEquals("[stop:false]", view.events.toString());
        assertFalse(presenter.canStop(lifecycle.status()));

        presenter.stopRequested();
        assertEquals(1, view.confirmations);
        assertEquals(1, pending.size());

        pending.get(0).run();
        assertEquals(ServerState.STOPPED, lifecycle.status().state());
        assertTrue(view.events.get(view.events.size() - 1).startsWith("status:"));
        assertFalse(view.events.contains("failure:Stop server"));
        assertFalse(presenter.canStop(lifecycle.status()));
    }

    private static final class FakeRuntime implements OllamaRuntimePort {
        @Override
        public List<OllamaInstallation> discover(List<File> directories, CancellationToken cancellation) {
            return Collections.emptyList();
        }

        @Override
        public ManagedServer start(OllamaInstallation installation, ServerSettings settings,
                                   CancellationToken cancellation) {
            return new FakeServer(installation, settings);
        }

        @Override
        public void stop(ManagedServer server, long gracePeriodMillis) {
            ((FakeServer) server).alive = false;
        }

        @Override
        public List<String> recentLog(ManagedServer server) {
            return Collections.emptyList();
        }
    }

    private static final class FakeServer implements ManagedServer {
        private final OllamaInstallation installation;
        private final ServerSettings settings;
        private volatile boolean alive = true;

        FakeServer(OllamaInstallation installation, ServerSettings settings) {
            this.installation = installation;
            this.settings = settings;
        }

        @Override
        public OllamaInstallation installation() {
            return installation;
        }

        @Override
        public ServerSettings appliedSettings() {
            return settings;
        }

        @Override
        public boolean isAlive() {
            return alive;
        }

        @Override
        public Integer exitCode() {
            return alive ? null : 0;
        }
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
        public void setStopEnabled(boolean enabled) {
            events.add("stop:" + enabled);
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
