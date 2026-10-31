package com.aresstack.ollamamanager.application.server;

import com.aresstack.ollamamanager.application.common.CancellationToken;
import com.aresstack.ollamamanager.application.common.ErrorKind;
import com.aresstack.ollamamanager.application.common.LifecycleLock;
import com.aresstack.ollamamanager.application.common.OperationException;
import com.aresstack.ollamamanager.application.common.ProgressListener;
import com.aresstack.ollamamanager.application.common.TransferProgress;
import com.aresstack.ollamamanager.application.installation.PortableInstallationService;
import com.aresstack.ollamamanager.application.port.DistributionInstallerPort;
import com.aresstack.ollamamanager.application.port.ManagedServer;
import com.aresstack.ollamamanager.application.port.OllamaRuntimePort;
import com.aresstack.ollamamanager.application.port.ReleaseCatalogPort;
import com.aresstack.ollamamanager.domain.distribution.DistributionPackage;
import com.aresstack.ollamamanager.domain.distribution.OllamaRelease;
import com.aresstack.ollamamanager.domain.server.Endpoint;
import com.aresstack.ollamamanager.domain.server.InstallationKind;
import com.aresstack.ollamamanager.domain.server.OllamaInstallation;
import com.aresstack.ollamamanager.domain.server.ServerSettings;
import com.aresstack.ollamamanager.domain.server.ServerState;
import org.junit.After;
import org.junit.Test;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Proves that a server start blocked in its readiness check and a concurrent uninstall never delete the
 * installation, independent of any UI.
 */
public class LifecycleUninstallConcurrencyTest {

    private static final File ROOT = new File(System.getProperty("java.io.tmpdir"), "lifecycle-lock-test")
        .getAbsoluteFile();

    private final GatedRuntime runtime = new GatedRuntime();
    private final RecordingInstaller installer = new RecordingInstaller();
    private final LifecycleLock lock = new LifecycleLock();
    private final ServerLifecycleService lifecycle = new ServerLifecycleService(runtime, 0, lock);
    private final PortableInstallationService installations = new PortableInstallationService(
        new NoCatalog(), installer, ROOT, new OwnedServerUsage(lifecycle), lock);
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final OllamaInstallation active = portable("v0.12.1");
    private final OllamaInstallation other = portable("v0.12.0");

    @After
    public void release() {
        runtime.releaseStart.countDown();
        runtime.releaseStop.countDown();
        executor.shutdownNow();
    }

    @Test
    public void uninstallDuringBlockedStartDeletesNothing() throws Exception {
        Future<?> start = executor.submit(() -> lifecycle.start(active, ServerSettings.defaults(),
            CancellationToken.none()));
        assertTrue(runtime.startEntered.await(5, TimeUnit.SECONDS));
        assertEquals(ServerState.STARTING, lifecycle.status().state());

        assertRefused(active);
        assertRefused(other);
        assertTrue(installer.uninstalled.isEmpty());

        runtime.releaseStart.countDown();
        start.get(5, TimeUnit.SECONDS);
        assertEquals(ServerState.RUNNING, lifecycle.status().state());
        assertRefused(active);
        assertTrue(installer.uninstalled.isEmpty());

        installations.uninstall(other);
        assertEquals(Collections.singletonList(other), installer.uninstalled);
    }

    @Test
    public void uninstallDuringBlockedStopDeletesNothing() throws Exception {
        runtime.releaseStart.countDown();
        lifecycle.start(active, ServerSettings.defaults(), CancellationToken.none());
        Future<?> stop = executor.submit(() -> lifecycle.stop());
        assertTrue(runtime.stopEntered.await(5, TimeUnit.SECONDS));

        assertRefused(active);
        assertTrue(installer.uninstalled.isEmpty());

        runtime.releaseStop.countDown();
        stop.get(5, TimeUnit.SECONDS);
        installations.uninstall(active);
        assertEquals(Collections.singletonList(active), installer.uninstalled);
    }

    @Test
    public void startDuringUninstallFailsFastAndKeepsServerStopped() throws Exception {
        installer.blockUninstall = new CountDownLatch(1);
        Future<?> uninstall = executor.submit(() -> {
            installations.uninstall(other);
            return null;
        });
        assertTrue(installer.uninstallEntered.await(5, TimeUnit.SECONDS));

        try {
            lifecycle.start(active, ServerSettings.defaults(), CancellationToken.none());
            fail("start must not run while an uninstall holds the lock");
        } catch (OperationException e) {
            assertEquals(ErrorKind.INVALID_STATE, e.kind());
        }
        assertEquals(1, runtime.startEntered.getCount());
        assertFalse(lifecycle.status().canBeStoppedByManager());

        installer.blockUninstall.countDown();
        uninstall.get(5, TimeUnit.SECONDS);
        assertFalse(lock.isHeld());
    }

    private void assertRefused(OllamaInstallation installation) {
        try {
            installations.uninstall(installation);
            fail("uninstall of " + installation.version() + " must be refused");
        } catch (OperationException e) {
            assertEquals(ErrorKind.INVALID_STATE, e.kind());
        }
    }

    private static OllamaInstallation portable(String version) {
        return new OllamaInstallation(InstallationKind.MANAGED_PORTABLE, version,
            new File(new File(ROOT, "versions"), version), Endpoint.DEFAULT);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Runtime whose start (the readiness check) and stop block until the test releases them. */
    private static final class GatedRuntime implements OllamaRuntimePort {
        final CountDownLatch startEntered = new CountDownLatch(1);
        final CountDownLatch releaseStart = new CountDownLatch(1);
        final CountDownLatch stopEntered = new CountDownLatch(1);
        final CountDownLatch releaseStop = new CountDownLatch(1);

        @Override
        public List<OllamaInstallation> discover(List<File> additionalDirectories, CancellationToken cancellation) {
            return Collections.emptyList();
        }

        @Override
        public ManagedServer start(OllamaInstallation installation, ServerSettings settings,
                                   CancellationToken cancellation) {
            startEntered.countDown();
            await(releaseStart);
            return new FakeServer(installation, settings);
        }

        @Override
        public void stop(ManagedServer server, long gracePeriodMillis) {
            stopEntered.countDown();
            await(releaseStop);
            ((FakeServer) server).exitCode = 0;
        }

        @Override
        public List<String> recentLog(ManagedServer server) {
            return Collections.emptyList();
        }
    }

    private static final class RecordingInstaller implements DistributionInstallerPort {
        final List<OllamaInstallation> uninstalled = new CopyOnWriteArrayList<OllamaInstallation>();
        final CountDownLatch uninstallEntered = new CountDownLatch(1);
        volatile CountDownLatch blockUninstall;

        @Override
        public OllamaInstallation install(OllamaRelease release, Set<DistributionPackage> packages, File installRoot,
                                          ProgressListener<TransferProgress> listener,
                                          CancellationToken cancellation) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<OllamaInstallation> installedVersions(File installRoot) {
            return Collections.emptyList();
        }

        @Override
        public void uninstall(OllamaInstallation installation, File installRoot) {
            uninstallEntered.countDown();
            if (blockUninstall != null) {
                await(blockUninstall);
            }
            uninstalled.add(installation);
        }
    }

    private static final class NoCatalog implements ReleaseCatalogPort {
        @Override
        public OllamaRelease latestStable(CancellationToken cancellation) {
            throw new UnsupportedOperationException();
        }

        @Override
        public OllamaRelease findRelease(String version, CancellationToken cancellation) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class FakeServer implements ManagedServer {
        private final OllamaInstallation installation;
        private final ServerSettings settings;
        volatile Integer exitCode;

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
            return exitCode == null;
        }

        @Override
        public Integer exitCode() {
            return exitCode;
        }
    }
}
