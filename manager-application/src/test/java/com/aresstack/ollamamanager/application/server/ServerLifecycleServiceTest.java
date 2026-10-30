package com.aresstack.ollamamanager.application.server;

import com.aresstack.ollamamanager.application.common.CancellationToken;
import com.aresstack.ollamamanager.application.common.ErrorKind;
import com.aresstack.ollamamanager.application.common.OperationException;
import com.aresstack.ollamamanager.application.port.ManagedServer;
import com.aresstack.ollamamanager.application.port.OllamaRuntimePort;
import com.aresstack.ollamamanager.domain.server.Endpoint;
import com.aresstack.ollamamanager.domain.server.InstallationKind;
import com.aresstack.ollamamanager.domain.server.OllamaInstallation;
import com.aresstack.ollamamanager.domain.server.RuntimeOwnership;
import com.aresstack.ollamamanager.domain.server.ServerSettingCatalog;
import com.aresstack.ollamamanager.domain.server.ServerSettings;
import com.aresstack.ollamamanager.domain.server.ServerState;
import com.aresstack.ollamamanager.domain.server.ServerStatus;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ServerLifecycleServiceTest {

    private static final OllamaInstallation INSTALLED = new OllamaInstallation(InstallationKind.MANAGED_INSTALLED,
        "0.12.3", new File("/opt/ollama").getAbsoluteFile(), Endpoint.DEFAULT);
    private static final OllamaInstallation EXTERNAL = OllamaInstallation.external("0.12.3", Endpoint.DEFAULT);

    private final FakeRuntime runtime = new FakeRuntime();
    private final ServerLifecycleService service = new ServerLifecycleService(runtime, 1000);

    @Test
    public void startsStopsAndReportsStatus() throws Exception {
        assertEquals(ServerState.STOPPED, service.status().state());

        ServerStatus running = service.start(INSTALLED, ServerSettings.defaults(), CancellationToken.none());
        assertEquals(ServerState.RUNNING, running.state());
        assertEquals(RuntimeOwnership.OWNED, running.ownership());

        assertEquals(ServerState.STOPPED, service.stop().state());
        assertEquals(1, runtime.stops.size());
        assertEquals(ServerState.STOPPED, service.stop().state());
        assertEquals(1, runtime.stops.size());
    }

    @Test
    public void rejectsDuplicateStart() throws Exception {
        service.start(INSTALLED, ServerSettings.defaults(), CancellationToken.none());
        assertFails(ErrorKind.INVALID_STATE, () -> service.start(INSTALLED, ServerSettings.defaults(),
            CancellationToken.none()));
        assertEquals(1, runtime.starts);
    }

    @Test
    public void rejectsCallsWhileStarting() throws Exception {
        runtime.blockStart = new CountDownLatch(1);
        CountDownLatch entered = new CountDownLatch(1);
        runtime.startEntered = entered;
        AtomicReference<Exception> failure = new AtomicReference<Exception>();
        Thread starter = new Thread(() -> {
            try {
                service.start(INSTALLED, ServerSettings.defaults(), CancellationToken.none());
            } catch (Exception e) {
                failure.set(e);
            }
        });
        starter.start();
        assertTrue(entered.await(5, TimeUnit.SECONDS));

        assertEquals(ServerState.STARTING, service.status().state());
        assertFails(ErrorKind.INVALID_STATE, () -> service.start(INSTALLED, ServerSettings.defaults(),
            CancellationToken.none()));
        assertFails(ErrorKind.INVALID_STATE, service::stop);

        runtime.blockStart.countDown();
        starter.join(5000);
        assertEquals(null, failure.get());
        assertEquals(ServerState.RUNNING, service.status().state());
    }

    @Test
    public void reportsCrashOfOwnedServer() throws Exception {
        service.start(INSTALLED, ServerSettings.defaults(), CancellationToken.none());
        runtime.last.exitCode = 3;

        ServerStatus status = service.status();
        assertEquals(ServerState.FAILED, status.state());
        assertTrue(status.message(), status.message().contains("3"));

        service.start(INSTALLED, ServerSettings.defaults(), CancellationToken.none());
        assertEquals(ServerState.RUNNING, service.status().state());
    }

    @Test
    public void reportsFailedStartAndAllowsRetry() throws Exception {
        runtime.nextFailure = new OperationException(ErrorKind.PORT_IN_USE, "busy");
        assertFails(ErrorKind.PORT_IN_USE, () -> service.start(INSTALLED, ServerSettings.defaults(),
            CancellationToken.none()));
        assertEquals(ServerState.FAILED, service.status().state());

        service.start(INSTALLED, ServerSettings.defaults(), CancellationToken.none());
        assertEquals(ServerState.RUNNING, service.status().state());
    }

    @Test
    public void neverControlsExternalServer() throws Exception {
        service.attachExternal(EXTERNAL, "0.12.3");

        assertEquals(RuntimeOwnership.EXTERNAL, service.status().ownership());
        assertFails(ErrorKind.NOT_OWNED, service::stop);
        assertFails(ErrorKind.NOT_OWNED, () -> service.restart(ServerSettings.defaults(), CancellationToken.none()));
        assertFails(ErrorKind.NOT_OWNED, () -> service.start(EXTERNAL, ServerSettings.defaults(),
            CancellationToken.none()));
        assertFails(ErrorKind.PORT_IN_USE, () -> service.start(INSTALLED, ServerSettings.defaults(),
            CancellationToken.none()));
        assertTrue(runtime.stops.isEmpty());
        assertEquals(0, runtime.starts);
    }

    @Test
    public void restartsWithNewSettingsAndTracksRestartNeed() throws Exception {
        service.start(INSTALLED, ServerSettings.defaults(), CancellationToken.none());
        ServerSettings changed = ServerSettings.defaults().with(ServerSettingCatalog.PARALLELISM, 4);
        assertTrue(service.restartRequired(changed));
        assertFalse(service.restartRequired(ServerSettings.defaults()));

        service.restart(changed, CancellationToken.none());

        assertEquals(1, runtime.stops.size());
        assertEquals(2, runtime.starts);
        assertEquals(changed, service.appliedSettings());
        assertFalse(service.restartRequired(changed));
    }

    @Test
    public void restartWithoutServerFails() {
        assertFails(ErrorKind.INVALID_STATE, () -> service.restart(ServerSettings.defaults(), CancellationToken.none()));
    }

    private interface Call {
        Object run() throws Exception;
    }

    private static void assertFails(ErrorKind expected, Call call) {
        try {
            call.run();
            fail("expected " + expected);
        } catch (OperationException e) {
            assertEquals(e.getMessage(), expected, e.kind());
        } catch (Exception e) {
            throw new AssertionError(e);
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

    private static final class FakeRuntime implements OllamaRuntimePort {
        final List<ManagedServer> stops = new ArrayList<ManagedServer>();
        volatile int starts;
        volatile FakeServer last;
        volatile OperationException nextFailure;
        volatile CountDownLatch blockStart;
        volatile CountDownLatch startEntered;

        @Override
        public List<OllamaInstallation> discover(List<File> additionalDirectories, CancellationToken cancellation) {
            return Collections.emptyList();
        }

        @Override
        public ManagedServer start(OllamaInstallation installation, ServerSettings settings,
                                   CancellationToken cancellation) throws OperationException {
            if (startEntered != null) {
                startEntered.countDown();
            }
            if (blockStart != null) {
                try {
                    blockStart.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            if (nextFailure != null) {
                OperationException failure = nextFailure;
                nextFailure = null;
                throw failure;
            }
            starts++;
            last = new FakeServer(installation, settings);
            return last;
        }

        @Override
        public void stop(ManagedServer server, long gracePeriodMillis) {
            stops.add(server);
            ((FakeServer) server).exitCode = 0;
        }

        @Override
        public List<String> recentLog(ManagedServer server) {
            return Collections.emptyList();
        }
    }
}
