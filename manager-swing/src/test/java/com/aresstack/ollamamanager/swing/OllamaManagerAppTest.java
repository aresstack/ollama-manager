package com.aresstack.ollamamanager.swing;

import com.aresstack.ollamamanager.application.server.OwnedServerUsage;
import com.aresstack.ollamamanager.domain.server.Endpoint;
import com.aresstack.ollamamanager.domain.server.InstallationKind;
import com.aresstack.ollamamanager.domain.server.OllamaInstallation;
import com.aresstack.ollamamanager.swing.shell.ConsoleServices;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class OllamaManagerAppTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void smokeReportShowsLoopbackDefault() {
        String report = OllamaManagerApp.smokeTestReport();

        assertTrue(report.startsWith("Ollama Manager smoke test OK"));
        assertTrue(report.contains("OLLAMA_HOST=127.0.0.1:11434"));
    }

    @Test
    public void compositionRootWiresEveryService() throws Exception {
        ConsoleServices services = OllamaManagerApp.createServices(folder.getRoot());

        assertNotNull(services.configuration());
        assertNotNull(services.serverLifecycle());
        assertNotNull(services.hardwareProbe());
        assertNotNull(services.gpuTelemetry());
        assertNotNull(services.models());
        assertNotNull(services.installations());
        assertNotNull(services.profiles());
        assertNotNull(services.setup());
        assertNotNull(services.profileRepository());
        assertTrue(services.installations().installedVersions().isEmpty());
    }

    @Test
    public void installationIsNotInUseWithoutOwnedServer() throws Exception {
        ConsoleServices services = OllamaManagerApp.createServices(folder.getRoot());
        OllamaInstallation installation = new OllamaInstallation(InstallationKind.MANAGED_PORTABLE, "0.12.0",
            folder.newFolder("ollama", "versions", "v0.12.0"), Endpoint.DEFAULT);

        assertFalse(new OwnedServerUsage(services.serverLifecycle()).isInUse(installation));
    }
}
