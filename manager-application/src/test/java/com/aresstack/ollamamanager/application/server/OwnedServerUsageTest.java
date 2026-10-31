package com.aresstack.ollamamanager.application.server;

import com.aresstack.ollamamanager.application.common.LifecycleLock;
import com.aresstack.ollamamanager.application.common.CancellationToken;
import com.aresstack.ollamamanager.application.installation.InstallationUsage;
import com.aresstack.ollamamanager.application.port.ManagedServer;
import com.aresstack.ollamamanager.application.port.OllamaRuntimePort;
import com.aresstack.ollamamanager.domain.server.Endpoint;
import com.aresstack.ollamamanager.domain.server.InstallationKind;
import com.aresstack.ollamamanager.domain.server.OllamaInstallation;
import com.aresstack.ollamamanager.domain.server.ServerSettings;
import com.aresstack.ollamamanager.domain.server.ServerState;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class OwnedServerUsageTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private final GatedRuntime runtime = new GatedRuntime();
    private final ServerLifecycleService lifecycle = new ServerLifecycleService(runtime, 0, new LifecycleLock());
    private final InstallationUsage usage = new OwnedServerUsage(lifecycle);
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private OllamaInstallation active;
    private OllamaInstallation other;

    @Before
    public void createInstallations() throws Exception {
        active = portable("v0.12.1");
        other = portable("v0.12.0");
    }

    @After
    public void releaseRuntime() {
        runtime.releaseStart.countDown();
        runtime.releaseStop.countDown();
        executor.shutdownNow();
    }

    @Test
    public void everyInstallationIsInUseWhileStarting() throws Exception {
        Future<?> start = executor.submit(() -> lifecycle.start(active, ServerSettings.defaults(),
            CancellationToken.none()));
        assertTrue(runtime.startEntered.await(5, TimeUnit.SECONDS));

        assertEquals(ServerState.STARTING, lifecycle.status().state());
        assertTrue(usage.isInUse(active));
        assertTrue(usage.isInUse(other));

        runtime.releaseStart.countDown();
        start.get(5, TimeUnit.SECONDS);
    }

    @Test
    public void onlyTheRunningInstallationIsInUseWhileRunning() throws Exception {
        runtime.releaseStart.countDown();
        lifecycle.start(active, ServerSettings.defaults(), CancellationToken.none());

        assertEquals(ServerState.RUNNING, lifecycle.status().state());
        assertTrue(usage.isInUse(active));
        assertFalse(usage.isInUse(other));
    }

    @Test
    public void everyInstallationIsInUseWhileStoppingAndNoneAfterwards() throws Exception {
        runtime.releaseStart.countDown();
        lifecycle.start(active, ServerSettings.defaults(), CancellationToken.none());
        Future<?> stop = executor.submit(() -> lifecycle.stop());
        assertTrue(runtime.stopEntered.await(5, TimeUnit.SECONDS));

        assertEquals(ServerState.STOPPING, lifecycle.status().state());
        assertTrue(usage.isInUse(active));
        assertTrue(usage.isInUse(other));

        runtime.releaseStop.countDown();
        stop.get(5, TimeUnit.SECONDS);
        assertFalse(usage.isInUse(active));
        assertFalse(usage.isInUse(other));
    }

    private OllamaInstallation portable(String version) throws Exception {
        File directory = folder.newFolder("ollama", "versions", version);
        return new OllamaInstallation(InstallationKind.MANAGED_PORTABLE, version, directory, Endpoint.DEFAULT);
    }

    /** Runtime whose start and stop block until the test releases them. */
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

        private static void await(CountDownLatch latch) {
            try {
                latch.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
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
