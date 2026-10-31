package com.aresstack.ollamamanager.swing.section;

import com.aresstack.ollamamanager.application.common.CancellationToken;
import com.aresstack.ollamamanager.application.common.OperationException;
import com.aresstack.ollamamanager.application.configuration.ConfigurationService;
import com.aresstack.ollamamanager.application.port.ConfigurationRepository;
import com.aresstack.ollamamanager.application.port.ManagedServer;
import com.aresstack.ollamamanager.application.port.OllamaRuntimePort;
import com.aresstack.ollamamanager.application.server.ServerLifecycleService;
import com.aresstack.ollamamanager.domain.configuration.ManagerConfiguration;
import com.aresstack.ollamamanager.domain.server.Endpoint;
import com.aresstack.ollamamanager.domain.server.InstallationKind;
import com.aresstack.ollamamanager.domain.server.OllamaInstallation;
import com.aresstack.ollamamanager.domain.server.ServerSettings;
import com.aresstack.ollamamanager.swing.shell.ConsoleServices;

import java.io.File;
import java.util.Collections;
import java.util.List;

/**
 * In-memory services for presenter tests. Nothing here starts a process or opens a connection.
 */
final class TestServices {

    /** Test endpoint; it is never contacted. */
    static final Endpoint ENDPOINT = Endpoint.parse("127.0.0.1:11499");

    private TestServices() {
    }

    static ConsoleServices.Builder builder() {
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

    /** Lifecycle service without any server. */
    static ServerLifecycleService stoppedLifecycle() {
        return new ServerLifecycleService(new FakeRuntime(), 0);
    }

    /** Lifecycle service whose fake owned server is running on {@link #ENDPOINT}. */
    static ServerLifecycleService runningLifecycle() throws OperationException {
        ServerLifecycleService lifecycle = stoppedLifecycle();
        lifecycle.start(new OllamaInstallation(InstallationKind.MANAGED_PORTABLE, "0.1.0",
            new File("ollama").getAbsoluteFile(), ENDPOINT), ServerSettings.defaults(), CancellationToken.none());
        return lifecycle;
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
}
