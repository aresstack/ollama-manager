package com.aresstack.ollamamanager.acceptance;

import com.aresstack.ollamamanager.application.port.ManagedServer;
import com.aresstack.ollamamanager.domain.server.Endpoint;
import com.aresstack.ollamamanager.domain.server.InstallationKind;
import com.aresstack.ollamamanager.domain.server.OllamaInstallation;
import com.aresstack.ollamamanager.domain.server.ServerSettings;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class AcceptanceGuardTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private File userModels;
    private File workDir;
    private AcceptanceGuard guard;

    @Before
    public void setUp() throws IOException {
        userModels = folder.newFolder("home", ".ollama", "models");
        workDir = new File(folder.getRoot(), "ollama-acceptance-1");
        guard = new AcceptanceGuard(workDir, Collections.singletonList(userModels));
    }

    @Test
    public void rejectsWorkDirWithoutPrefixOrInsideProtectedFolder() {
        expectRejected(() -> new AcceptanceGuard(new File(folder.getRoot(), "temp"), Collections.<File>emptyList()));
        expectRejected(() -> new AcceptanceGuard(new File(userModels, "ollama-acceptance-2"),
            Collections.singletonList(userModels)));
        expectRejected(() -> new AcceptanceGuard(new File(folder.getRoot(), "ollama-acceptance-3"),
            Collections.singletonList(new File(folder.getRoot(), "ollama-acceptance-3/models"))));
    }

    @Test
    public void rejectsNonEmptyWorkDir() throws IOException {
        guard.requireFreshWorkDir();
        assertTrue(new File(workDir, "left-over").mkdirs());
        expectRejected(guard::requireFreshWorkDir);
    }

    @Test
    public void rejectsDefaultPort() {
        expectRejected(() -> AcceptanceGuard.requireTestPort(11434));
        expectRejected(() -> AcceptanceGuard.requireTestPort(80));
        AcceptanceGuard.requireTestPort(11534);
    }

    @Test
    public void acceptsOnlyFoldersBelowWorkDir() {
        guard.requireInsideWorkDir(new File(workDir, "models"));
        expectRejected(() -> guard.requireInsideWorkDir(workDir));
        expectRejected(() -> guard.requireInsideWorkDir(new File(workDir, "../elsewhere")));
        expectRejected(() -> guard.requireInsideWorkDir(userModels));
    }

    @Test
    public void requiresLiveOwnedServerOnTestEndpoint() {
        Endpoint test = new Endpoint("127.0.0.1", 11534);
        AcceptanceGuard.requireOwnedTestEndpoint(test, server(test, true));
        expectRejected(() -> AcceptanceGuard.requireOwnedTestEndpoint(test, null));
        expectRejected(() -> AcceptanceGuard.requireOwnedTestEndpoint(test, server(test, false)));
        expectRejected(() -> AcceptanceGuard.requireOwnedTestEndpoint(test,
            server(new Endpoint("127.0.0.1", 11535), true)));
        expectRejected(() -> AcceptanceGuard.requireOwnedTestEndpoint(Endpoint.DEFAULT, server(Endpoint.DEFAULT, true)));
    }

    @Test
    public void deletesOnlyNewModelWithManifestInTestRoot() throws IOException {
        File modelRoot = new File(workDir, "models");
        File manifest = AcceptanceGuard.manifestFile(modelRoot, "smollm2:135m");
        assertEquals(new File(modelRoot, "manifests/registry.ollama.ai/library/smollm2/135m".replace('/', File.separatorChar)),
            manifest);

        expectRejected(() -> guard.requireDeletableTestModel("smollm2:135m", Collections.<String>emptyList(), modelRoot));
        assertTrue(manifest.getParentFile().mkdirs() && manifest.createNewFile());
        guard.requireDeletableTestModel("smollm2:135m", Arrays.asList("llama3.1:8b"), modelRoot);

        expectRejected(() -> guard.requireDeletableTestModel("smollm2:135m", Arrays.asList("smollm2:135m"), modelRoot));
        expectRejected(() -> guard.requireDeletableTestModel("smollm2:135m", Collections.<String>emptyList(), userModels));
    }

    @Test
    public void normalizesImplicitLatestTag() {
        assertEquals("gemma4:latest", AcceptanceGuard.normalizeName("gemma4"));
        assertEquals("user/model:latest", AcceptanceGuard.normalizeName("user/model"));
        assertEquals("gemma4:e2b", AcceptanceGuard.normalizeName("gemma4:e2b"));
    }

    @Test
    public void parsesStagesAndAlwaysIncludesReadOnly() {
        assertEquals(Arrays.asList("process", "readonly"),
            Arrays.asList(NativeAcceptanceMain.parseStages("process").toArray()));
        assertEquals(NativeAcceptanceMain.ALL_STAGES.size(), NativeAcceptanceMain.parseStages("all").size());
        try {
            NativeAcceptanceMain.parseStages("delete-everything");
            fail("unknown stage accepted");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    private static ManagedServer server(Endpoint endpoint, boolean alive) {
        OllamaInstallation installation = new OllamaInstallation(InstallationKind.MANAGED_PORTABLE, "0.40.13",
            new File("bin").getAbsoluteFile(), endpoint);
        return new ManagedServer() {
            @Override
            public OllamaInstallation installation() {
                return installation;
            }

            @Override
            public ServerSettings appliedSettings() {
                return ServerSettings.defaults();
            }

            @Override
            public boolean isAlive() {
                return alive;
            }

            @Override
            public Integer exitCode() {
                return alive ? null : 0;
            }
        };
    }

    private static void expectRejected(Runnable action) {
        try {
            action.run();
            fail("guard accepted an unsafe value");
        } catch (IllegalStateException expected) {
            // expected
        }
    }
}
