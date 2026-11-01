package com.aresstack.ollamamanager.swing.section;

import com.aresstack.ollamamanager.application.common.ErrorKind;
import com.aresstack.ollamamanager.application.common.LifecycleLock;
import com.aresstack.ollamamanager.application.common.OperationException;
import com.aresstack.ollamamanager.application.hardware.GpuCompatibilityRules;
import com.aresstack.ollamamanager.application.hardware.PackageAdvisor;
import com.aresstack.ollamamanager.application.hardware.PackageRecommendation;
import com.aresstack.ollamamanager.application.installation.PortableInstallationService;
import com.aresstack.ollamamanager.application.port.DistributionInstallerPort;
import com.aresstack.ollamamanager.application.port.HardwareProbePort;
import com.aresstack.ollamamanager.application.port.ManagedServer;
import com.aresstack.ollamamanager.application.port.OllamaApiPort;
import com.aresstack.ollamamanager.application.port.OllamaRuntimePort;
import com.aresstack.ollamamanager.application.port.ReleaseCatalogPort;
import com.aresstack.ollamamanager.application.server.ServerLifecycleService;
import com.aresstack.ollamamanager.application.setup.SetupService;
import com.aresstack.ollamamanager.domain.distribution.DistributionPackage;
import com.aresstack.ollamamanager.domain.server.Endpoint;
import com.aresstack.ollamamanager.domain.server.InstallationKind;
import com.aresstack.ollamamanager.domain.server.OllamaInstallation;
import com.aresstack.ollamamanager.domain.server.RuntimeOwnership;
import com.aresstack.ollamamanager.domain.server.ServerSettings;
import com.aresstack.ollamamanager.domain.server.ServerState;
import com.aresstack.ollamamanager.swing.job.Job;
import com.aresstack.ollamamanager.swing.job.JobManager;
import com.aresstack.ollamamanager.swing.job.UiDispatcher;
import com.aresstack.ollamamanager.swing.shell.ConsoleServices;
import org.junit.Test;

import java.io.File;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SetupPresenterTest {

    private static final OllamaInstallation INSTALLED = new OllamaInstallation(InstallationKind.MANAGED_INSTALLED,
        "0.12.0", new File("installed").getAbsoluteFile(), Endpoint.DEFAULT);
    private static final OllamaInstallation PORTABLE = new OllamaInstallation(InstallationKind.MANAGED_PORTABLE,
        "0.12.1", new File("portable/0.12.1").getAbsoluteFile(), Endpoint.DEFAULT);

    private final LifecycleLock lock = new LifecycleLock();
    private final List<String> calls = new ArrayList<String>();
    private final RecordingView view = new RecordingView();
    private String runningVersion;
    private boolean portableInUse;

    private final OllamaApiPort api = stub(OllamaApiPort.class, (method, args) -> {
        if (runningVersion == null) {
            throw new OperationException(ErrorKind.CONNECTION_FAILED, "refused");
        }
        return runningVersion;
    });
    private final OllamaRuntimePort runtime = stub(OllamaRuntimePort.class, (method, args) -> {
        switch (method) {
            case "discover":
                return Collections.singletonList(INSTALLED);
            case "start":
                calls.add("start");
                return new RunningServer((OllamaInstallation) args[0], (ServerSettings) args[1]);
            default:
                return null;
        }
    });
    private final DistributionInstallerPort installer = stub(DistributionInstallerPort.class, (method, args) -> {
        switch (method) {
            case "installedVersions":
                return Collections.singletonList(PORTABLE);
            case "install":
                calls.add("install " + args[1]);
                return PORTABLE;
            case "uninstall":
                calls.add("uninstall");
                return null;
            default:
                return null;
        }
    });
    private final ServerLifecycleService lifecycle = new ServerLifecycleService(runtime, 0, lock);
    private final PortableInstallationService installations = new PortableInstallationService(
        stub(ReleaseCatalogPort.class, (method, args) -> null), installer, new File("portable").getAbsoluteFile(),
        installation -> portableInUse, lock);
    private final ConsoleServices services = TestServices.builder().serverLifecycle(lifecycle)
        .installations(installations)
        .setup(new SetupService(runtime, api, lifecycle, stub(HardwareProbePort.class, (method, args) -> {
            throw new OperationException(ErrorKind.SERVICE_UNAVAILABLE, "no probe in tests");
        }), new PackageAdvisor(new GpuCompatibilityRules()), installations))
        .build();

    @Test
    public void detectionListsRunningServerAndInstallations() {
        runningVersion = "0.40.13";
        SetupPresenter presenter = presenter(inline());

        presenter.detect();

        assertTrue(view.detection.isServerRunning());
        assertEquals(Endpoint.DEFAULT, view.detection.endpoint());
        assertEquals(java.util.Arrays.asList(PORTABLE, INSTALLED), view.detection.installations());
        assertNull(view.detection.selected());
    }

    @Test
    public void useRunningServerAttachesItReadOnly() throws Exception {
        runningVersion = "0.40.13";
        SetupPresenter presenter = presenter(inline());
        presenter.detect();

        presenter.useRunningServer();

        assertEquals(RuntimeOwnership.EXTERNAL, lifecycle.status().ownership());
        assertNull(services.configuration().current().selectedInstallationKind());
        assertTrue(view.notices.contains(SetupPresenter.ATTACHED));
        assertFalse(calls.contains("start"));
    }

    @Test
    public void selectStoresInstallationInConfiguration() throws Exception {
        SetupPresenter presenter = presenter(inline());
        presenter.detect();

        presenter.select(INSTALLED);

        assertEquals(InstallationKind.MANAGED_INSTALLED, services.configuration().current().selectedInstallationKind());
        assertEquals(INSTALLED, view.detection.selected());
    }

    @Test
    public void installWithoutPackagesDoesNothing() throws Exception {
        SetupPresenter presenter = presenter(inline());

        presenter.install(EnumSet.noneOf(DistributionPackage.class));

        assertTrue(calls.isEmpty());
        assertEquals(SetupPresenter.NO_PACKAGES, view.notices.get(view.notices.size() - 1));
    }

    @Test
    public void installDownloadsAndSelectsPortableVersion() throws Exception {
        SetupPresenter presenter = presenter(inline());

        presenter.install(EnumSet.of(DistributionPackage.BASE));

        assertEquals(Collections.singletonList("install [BASE]"), calls);
        assertEquals(PORTABLE.binaryDirectory(), services.configuration().current().selectedBinaryDirectory());
        assertFalse(presenter.isBusy());
    }

    @Test
    public void cancelledInstallSelectsNothingAndReleasesBusy() throws Exception {
        List<Runnable> pending = new ArrayList<Runnable>();
        SetupPresenter presenter = presenter(new JobManager(pending::add, UiDispatcher.inline()));

        presenter.install(EnumSet.of(DistributionPackage.BASE));
        assertTrue(presenter.isBusy());
        view.tracked.cancel();
        pending.remove(0).run();

        assertFalse(presenter.isBusy());
        assertTrue(view.notices.contains(SetupPresenter.INSTALL_CANCELLED));
        assertNull(services.configuration().current().selectedBinaryDirectory());
        assertTrue(calls.isEmpty());
    }

    @Test
    public void uninstallOfVersionInUseIsRefused() throws Exception {
        portableInUse = true;
        SetupPresenter presenter = presenter(inline());

        presenter.uninstall(PORTABLE);

        assertFalse(calls.contains("uninstall"));
        assertTrue(view.notices.contains(SetupPresenter.IN_USE));
        assertFalse(presenter.isBusy());
    }

    @Test
    public void declinedUninstallRemovesNothing() {
        view.acceptUninstall = false;
        presenter(inline()).uninstall(PORTABLE);

        assertTrue(calls.isEmpty());
    }

    @Test
    public void startIsRefusedWhileAnotherServerAnswersOnTheEndpoint() throws Exception {
        runningVersion = "0.40.13";
        SetupPresenter presenter = presenter(inline());
        presenter.detect();
        presenter.select(PORTABLE);

        presenter.startSelected();

        assertFalse(calls.contains("start"));
        assertEquals(0, view.startQuestions);
        assertTrue(view.notices.get(view.notices.size() - 1).startsWith("A server already answers on"));
    }

    @Test
    public void startRunsSelectedInstallationAsOwnedServer() throws Exception {
        SetupPresenter presenter = presenter(inline());
        presenter.detect();
        presenter.select(PORTABLE);

        presenter.startSelected();

        assertEquals(1, view.startQuestions);
        assertEquals(ServerState.RUNNING, lifecycle.status().state());
        assertEquals(RuntimeOwnership.OWNED, lifecycle.status().ownership());
        assertTrue(view.notices.contains(SetupPresenter.STARTED));
    }

    @Test
    public void concurrentLifecycleOperationIsReportedPlainly() throws Exception {
        SetupPresenter presenter = presenter(inline());
        presenter.detect();
        presenter.select(PORTABLE);
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread holder = new Thread(() -> {
            try {
                lock.run("hold", () -> {
                    held.countDown();
                    try {
                        release.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return null;
                });
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        holder.start();
        held.await();
        try {
            presenter.startSelected();
        } finally {
            release.countDown();
            holder.join();
        }

        assertFalse(calls.contains("start"));
        assertTrue(view.notices.contains(SetupPresenter.LIFECYCLE_BUSY));
        assertFalse(presenter.isBusy());
    }

    private SetupPresenter presenter(JobManager jobs) {
        SetupPresenter presenter = new SetupPresenter(services, jobs);
        presenter.bind(view);
        return presenter;
    }

    private static JobManager inline() {
        return new JobManager(Runnable::run, UiDispatcher.inline());
    }

    private interface Answer {
        Object get(String method, Object[] args) throws Exception;
    }

    private static <T> T stub(Class<T> type, Answer answer) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
            (proxy, method, args) -> answer.get(method.getName(), args)));
    }

    private static final class RunningServer implements ManagedServer {
        private final OllamaInstallation installation;
        private final ServerSettings settings;

        RunningServer(OllamaInstallation installation, ServerSettings settings) {
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
            return true;
        }

        @Override
        public Integer exitCode() {
            return null;
        }
    }

    private static final class RecordingView implements SetupView {
        final List<String> notices = new ArrayList<String>();
        final Map<String, Exception> failures = new HashMap<String, Exception>();
        SetupDetection detection;
        Job tracked;
        boolean acceptUninstall = true;
        int startQuestions;

        @Override
        public void showDetection(SetupDetection value) {
            detection = value;
        }

        @Override
        public void showRecommendation(PackageRecommendation recommendation) {
        }

        @Override
        public void setBusy(boolean busy) {
        }

        @Override
        public void showNotice(String notice) {
            notices.add(notice);
        }

        @Override
        public void showFailure(String action, Exception error) {
            failures.put(action, error);
            notices.add("failure:" + action + ":" + error.getMessage());
        }

        @Override
        public void trackJob(Job job) {
            tracked = job;
        }

        @Override
        public boolean confirmUninstall(OllamaInstallation installation) {
            return acceptUninstall;
        }

        @Override
        public boolean confirmStart(OllamaInstallation installation, String endpoint) {
            startQuestions++;
            return true;
        }
    }
}
