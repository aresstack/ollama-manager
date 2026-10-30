package com.aresstack.ollamamanager.acceptance;

import com.aresstack.ollamamanager.application.common.CancellationToken;
import com.aresstack.ollamamanager.application.port.ManagedServer;
import com.aresstack.ollamamanager.application.port.OllamaRuntimePort;
import com.aresstack.ollamamanager.domain.server.Endpoint;
import com.aresstack.ollamamanager.domain.server.InstallationKind;
import com.aresstack.ollamamanager.domain.server.OllamaInstallation;
import com.aresstack.ollamamanager.domain.server.ServerSettingCatalog;
import com.aresstack.ollamamanager.domain.server.ServerSettings;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class GuardedRuntimeTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private final List<ServerSettings> started = new ArrayList<ServerSettings>();
    private File workDir;
    private OllamaInstallation installation;
    private GuardedRuntime runtime;

    @Before
    public void setUp() {
        workDir = new File(folder.getRoot(), "ollama-acceptance-1");
        installation = new OllamaInstallation(InstallationKind.MANAGED_PORTABLE, "0.40.13",
            new File(workDir, "install").getAbsoluteFile(), Endpoint.DEFAULT);
        // A fake runtime that would start anything: it stands for a reference server that is currently down.
        OllamaRuntimePort startsAnything = new OllamaRuntimePort() {
            @Override
            public List<OllamaInstallation> discover(List<File> additionalDirectories, CancellationToken cancellation) {
                return Collections.emptyList();
            }

            @Override
            public ManagedServer start(OllamaInstallation ignored, ServerSettings settings,
                                       CancellationToken cancellation) {
                started.add(settings);
                return null;
            }

            @Override
            public void stop(ManagedServer server, long gracePeriodMillis) {
            }

            @Override
            public List<String> recentLog(ManagedServer server) {
                return Collections.emptyList();
            }
        };
        runtime = new GuardedRuntime(startsAnything,
            new AcceptanceGuard(workDir, Collections.<File>emptyList(), 11434));
    }

    @Test
    public void neverStartsOnFreeReferencePort() throws Exception {
        expectRefused(settings(Endpoint.DEFAULT, new File(workDir, "models")));
        assertEquals(0, started.size());
    }

    @Test
    public void neverStartsWithoutModelRootBelowWorkDir() throws Exception {
        Endpoint test = new Endpoint("127.0.0.1", 11534);
        expectRefused(ServerSettings.defaults().with(ServerSettingCatalog.BIND, test));
        expectRefused(settings(test, folder.newFolder("elsewhere")));
        assertEquals(0, started.size());
    }

    @Test
    public void startsOnTestPortWithIsolatedModelRoot() throws Exception {
        runtime.start(installation, settings(new Endpoint("127.0.0.1", 11534), new File(workDir, "models")),
            CancellationToken.none());
        assertEquals(1, started.size());
    }

    private static ServerSettings settings(Endpoint bind, File modelRoot) {
        return ServerSettings.defaults().with(ServerSettingCatalog.BIND, bind)
            .with(ServerSettingCatalog.MODEL_ROOT, modelRoot.getAbsoluteFile());
    }

    private void expectRefused(ServerSettings settings) throws Exception {
        try {
            runtime.start(installation, settings, CancellationToken.none());
            fail("unsafe start accepted: " + settings);
        } catch (IllegalStateException expected) {
            // expected
        }
    }
}
