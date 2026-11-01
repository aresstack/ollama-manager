package com.aresstack.ollamamanager.application.server;

import com.aresstack.ollamamanager.application.common.CancellationToken;
import com.aresstack.ollamamanager.application.common.ErrorKind;
import com.aresstack.ollamamanager.application.common.LifecycleLock;
import com.aresstack.ollamamanager.application.common.OperationException;
import com.aresstack.ollamamanager.application.configuration.ConfigurationService;
import com.aresstack.ollamamanager.application.configuration.ExistingSettingsImport;
import com.aresstack.ollamamanager.application.port.ConfigurationRepository;
import com.aresstack.ollamamanager.application.port.ExternalServerControlPort;
import com.aresstack.ollamamanager.application.port.ExternalServerProcess;
import com.aresstack.ollamamanager.application.port.InstalledEnvironmentPort;
import com.aresstack.ollamamanager.application.port.ManagedServer;
import com.aresstack.ollamamanager.application.port.OllamaRuntimePort;
import com.aresstack.ollamamanager.domain.configuration.ManagerConfiguration;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ServerParallelismServiceTest {

    private static final OllamaInstallation PORTABLE = new OllamaInstallation(InstallationKind.MANAGED_PORTABLE,
        "0.12.3", new File("ollama").getAbsoluteFile(), Endpoint.DEFAULT);
    private static final OllamaInstallation EXTERNAL = OllamaInstallation.external("0.12.3", Endpoint.DEFAULT);

    private final FakeRuntime runtime = new FakeRuntime();
    private final LifecycleLock lock = new LifecycleLock();
    private final InMemoryRepository repository = new InMemoryRepository();
    private final ConfigurationService configuration = new ConfigurationService(repository);
    private final Map<String, String> environment = new LinkedHashMap<String, String>();
    private final ExistingSettingsImport existing = new ExistingSettingsImport(configuration,
        new InstalledEnvironmentPort() {
            @Override
            public Map<String, String> ollamaVariables(CancellationToken cancellation) {
                return environment;
            }

            @Override
            public String sourceDescription() {
                return "test environment";
            }
        });

    private ServerLifecycleService owned() throws OperationException {
        ServerLifecycleService lifecycle = new ServerLifecycleService(runtime, 0, lock);
        lifecycle.start(PORTABLE, ServerSettings.defaults().with(ServerSettingCatalog.PARALLELISM, 4),
            CancellationToken.none());
        return lifecycle;
    }

    private ServerLifecycleService external(ExternalServerControlPort control) throws OperationException {
        ServerLifecycleService lifecycle = new ServerLifecycleService(runtime, 0, lock, control, 0);
        lifecycle.attachExternal(EXTERNAL, "0.12.3");
        return lifecycle;
    }

    @Test
    public void storesTheValueAndRestartsTheOwnedServerWithIt() throws Exception {
        ServerLifecycleService lifecycle = owned();
        repository.stored = repository.stored.withDesiredServerSettings(
            ServerSettings.defaults().with(ServerSettingCatalog.LOADED_MODELS, 2));
        ServerParallelismService service = new ServerParallelismService(lifecycle, configuration, existing, lock);

        ServerStatus status = service.apply(16, CancellationToken.none());

        assertEquals(ServerState.RUNNING, status.state());
        assertEquals(RuntimeOwnership.OWNED, status.ownership());
        assertEquals(Integer.valueOf(16), lifecycle.appliedSettings().parallelism());
        // The other desired settings travel along, and the stored configuration now carries the value.
        assertEquals(Integer.valueOf(2), lifecycle.appliedSettings().loadedModels());
        assertEquals(Integer.valueOf(16), repository.stored.desiredServerSettings().parallelism());
        assertTrue(repository.stored.desiredServerSettings().isChosen(ServerSettingCatalog.PARALLELISM));
        assertEquals(2, runtime.starts.size());
    }

    @Test
    public void keepsTheStoredValueWhenTheRestartFails() throws Exception {
        ServerLifecycleService lifecycle = owned();
        runtime.failNextStart = true;
        ServerParallelismService service = new ServerParallelismService(lifecycle, configuration, null, lock);

        try {
            service.apply(8, CancellationToken.none());
            fail("expected the start failure");
        } catch (OperationException e) {
            assertEquals(ErrorKind.PROCESS_FAILED, e.kind());
        }
        assertEquals(Integer.valueOf(8), repository.stored.desiredServerSettings().parallelism());
        assertEquals(ServerState.FAILED, lifecycle.status().state());
    }

    @Test
    public void takesOverAControllableExternalServerWithItsConfiguredSettings() throws Exception {
        File binary = new File("/opt/ollama/ollama").getAbsoluteFile();
        FakeControl control = new FakeControl(new ExternalServerProcess(77, "ollama", binary, 70, "ollama app"));
        ServerLifecycleService lifecycle = external(control);
        environment.put("OLLAMA_MODELS", new File("models").getAbsolutePath());
        environment.put("OLLAMA_NUM_PARALLEL", "2");
        ServerParallelismService service = new ServerParallelismService(lifecycle, configuration, existing, lock);
        assertTrue(service.canApply(lifecycle.status()));

        ServerStatus status = service.apply(16, CancellationToken.none());

        assertEquals(RuntimeOwnership.OWNED, status.ownership());
        assertEquals(1, control.terminated.size());
        ServerSettings applied = lifecycle.appliedSettings();
        assertEquals(Integer.valueOf(16), applied.parallelism());
        // The configured model folder of the external installation is kept; its parallel value is replaced.
        assertEquals(new File("models").getAbsoluteFile(), applied.modelRoot());
        assertEquals(binary.getParentFile(), status.installation().binaryDirectory());
    }

    @Test
    public void refusesAnExternalServerWithoutControl() throws Exception {
        ServerLifecycleService lifecycle = external(null);
        ServerParallelismService service = new ServerParallelismService(lifecycle, configuration, existing, lock);
        assertFalse(service.canApply(lifecycle.status()));

        try {
            service.apply(16, CancellationToken.none());
            fail("expected NOT_OWNED");
        } catch (OperationException e) {
            assertEquals(ErrorKind.NOT_OWNED, e.kind());
        }
        assertEquals(0, repository.saves);
        assertEquals(RuntimeOwnership.EXTERNAL, lifecycle.status().ownership());
    }

    @Test
    public void refusesWithoutARunningServer() throws Exception {
        ServerLifecycleService lifecycle = new ServerLifecycleService(runtime, 0, lock);
        ServerParallelismService service = new ServerParallelismService(lifecycle, configuration, null, lock);
        assertFalse(service.canApply(lifecycle.status()));

        try {
            service.apply(16, CancellationToken.none());
            fail("expected INVALID_STATE");
        } catch (OperationException e) {
            assertEquals(ErrorKind.INVALID_STATE, e.kind());
        }
        assertEquals(0, repository.saves);
    }

    @Test
    public void rejectsValuesOutsideTheSettingsRangeBeforeTouchingAnything() throws Exception {
        ServerLifecycleService lifecycle = owned();
        ServerParallelismService service = new ServerParallelismService(lifecycle, configuration, null, lock);

        for (int value : new int[] {0, 257}) {
            try {
                service.apply(value, CancellationToken.none());
                fail("expected INVALID_CONFIGURATION for " + value);
            } catch (OperationException e) {
                assertEquals(ErrorKind.INVALID_CONFIGURATION, e.kind());
            }
        }
        assertEquals(0, repository.saves);
        assertEquals(1, runtime.starts.size());
    }

    @Test
    public void failsFastWhileAnotherLifecycleOperationHoldsTheLock() throws Exception {
        ServerLifecycleService lifecycle = owned();
        final ServerParallelismService service = new ServerParallelismService(lifecycle, configuration, null, lock);
        final CountDownLatch held = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        Thread holder = new Thread(() -> {
            try {
                lock.run("hold", () -> {
                    held.countDown();
                    try {
                        release.await(5, TimeUnit.SECONDS);
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
        assertTrue(held.await(5, TimeUnit.SECONDS));
        try {
            service.apply(16, CancellationToken.none());
            fail("expected INVALID_STATE");
        } catch (OperationException e) {
            assertEquals(ErrorKind.INVALID_STATE, e.kind());
        } finally {
            release.countDown();
            holder.join(5000);
        }
        assertEquals(0, repository.saves);
        assertNull(runtime.lastFailure);
    }

    private static final class InMemoryRepository implements ConfigurationRepository {
        ManagerConfiguration stored = ManagerConfiguration.defaults();
        int saves;

        @Override
        public ManagerConfiguration load() {
            return stored;
        }

        @Override
        public void save(ManagerConfiguration configuration) {
            stored = configuration;
            saves++;
        }
    }

    private static final class FakeControl implements ExternalServerControlPort {
        final List<ExternalServerProcess> terminated = new ArrayList<ExternalServerProcess>();
        ExternalServerProcess listener;

        FakeControl(ExternalServerProcess listener) {
            this.listener = listener;
        }

        @Override
        public ExternalServerProcess find(Endpoint endpoint, CancellationToken cancellation) {
            return listener;
        }

        @Override
        public void terminate(ExternalServerProcess process, long gracePeriodMillis, CancellationToken cancellation) {
            terminated.add(process);
            listener = null;
        }
    }

    private static final class FakeRuntime implements OllamaRuntimePort {
        final List<ServerSettings> starts = new ArrayList<ServerSettings>();
        volatile boolean failNextStart;
        volatile String lastFailure;

        @Override
        public List<OllamaInstallation> discover(List<File> additionalDirectories, CancellationToken cancellation) {
            return Collections.emptyList();
        }

        @Override
        public ManagedServer start(OllamaInstallation installation, ServerSettings settings,
                                   CancellationToken cancellation) throws OperationException {
            if (failNextStart) {
                failNextStart = false;
                lastFailure = "start failed";
                throw new OperationException(ErrorKind.PROCESS_FAILED, lastFailure);
            }
            starts.add(settings);
            return new FakeServer(installation, settings);
        }

        @Override
        public void stop(ManagedServer server, long gracePeriodMillis) {
            ((FakeServer) server).exitCode = 0;
        }

        @Override
        public List<String> recentLog(ManagedServer server) {
            return Collections.emptyList();
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
