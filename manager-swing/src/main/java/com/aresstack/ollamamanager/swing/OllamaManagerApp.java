package com.aresstack.ollamamanager.swing;

import com.aresstack.ollamamanager.application.configuration.ConfigurationService;
import com.aresstack.ollamamanager.application.installation.InstallationUsage;
import com.aresstack.ollamamanager.application.installation.PortableInstallationService;
import com.aresstack.ollamamanager.application.model.ModelService;
import com.aresstack.ollamamanager.application.profile.ProfileActivationService;
import com.aresstack.ollamamanager.application.server.ServerLifecycleService;
import com.aresstack.ollamamanager.domain.server.OllamaInstallation;
import com.aresstack.ollamamanager.domain.server.ServerSettings;
import com.aresstack.ollamamanager.domain.server.ServerStatus;
import com.aresstack.ollamamanager.infrastructure.distribution.GitHubReleaseCatalog;
import com.aresstack.ollamamanager.infrastructure.distribution.PortableDistributionInstaller;
import com.aresstack.ollamamanager.infrastructure.hardware.SystemHardwareProbe;
import com.aresstack.ollamamanager.infrastructure.json.Json;
import com.aresstack.ollamamanager.infrastructure.ollamaapi.HttpOllamaApiClient;
import com.aresstack.ollamamanager.infrastructure.persistence.JsonConfigurationRepository;
import com.aresstack.ollamamanager.infrastructure.persistence.UserDataDirectory;
import com.aresstack.ollamamanager.infrastructure.runtime.ProcessOllamaRuntime;
import com.aresstack.ollamamanager.swing.shell.ConsoleServices;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.io.File;
import java.util.Arrays;

/**
 * Entry point and composition root: wires adapters to ports and opens the Swing shell.
 */
public final class OllamaManagerApp {

    static final String SMOKE_TEST_FLAG = "--smoke-test";
    /** Directory below the user data directory that holds manager-owned portable installations. */
    static final String PORTABLE_DIRECTORY = "ollama";
    /** Time an owned server gets to exit on its own before it is killed. */
    static final long STOP_GRACE_PERIOD_MILLIS = 5000;

    private OllamaManagerApp() {
    }

    public static void main(String[] args) {
        if (Arrays.asList(args).contains(SMOKE_TEST_FLAG)) {
            System.out.println(smokeTestReport());
            return;
        }
        ConsoleServices services = createServices(UserDataDirectory.resolve());
        SwingUtilities.invokeLater(() -> openMainFrame(services));
    }

    /**
     * Build all application services once. Exactly one configuration service exists per process, so every
     * settings and profile write goes through the same lock.
     */
    static ConsoleServices createServices(File dataDirectory) {
        ConfigurationService configuration =
            new ConfigurationService(new JsonConfigurationRepository(dataDirectory));
        File portableRoot = new File(dataDirectory, PORTABLE_DIRECTORY);
        SystemHardwareProbe hardwareProbe = SystemHardwareProbe.forCurrentSystem();
        ServerLifecycleService lifecycle =
            new ServerLifecycleService(new ProcessOllamaRuntime(portableRoot), STOP_GRACE_PERIOD_MILLIS);
        PortableInstallationService installations = new PortableInstallationService(
            GitHubReleaseCatalog.defaults(), PortableDistributionInstaller.defaults(), portableRoot,
            usedByOwnedServer(lifecycle));
        return ConsoleServices.builder(configuration)
            .serverLifecycle(lifecycle)
            .hardwareProbe(hardwareProbe)
            .gpuTelemetry(hardwareProbe.nvidiaSmi())
            .models(new ModelService(new HttpOllamaApiClient()))
            .installations(installations)
            .profiles(new ProfileActivationService(configuration))
            .build();
    }

    /** Treat an installation as in use while the manager's own server runs from its binary directory. */
    static InstallationUsage usedByOwnedServer(ServerLifecycleService lifecycle) {
        return installation -> {
            ServerStatus status = lifecycle.status();
            OllamaInstallation running = status.installation();
            return status.canBeStoppedByManager() && running != null && running.binaryDirectory() != null
                && running.binaryDirectory().equals(installation.binaryDirectory());
        };
    }

    /**
     * Verify that all modules load on the current JVM without opening a window.
     */
    static String smokeTestReport() {
        return "Ollama Manager smoke test OK"
            + " | java.version=" + System.getProperty("java.version")
            + " | default environment=" + ServerSettings.defaults().toEnvironment()
            + " | repository=" + JsonConfigurationRepository.class.getSimpleName()
            + " | json=" + Json.parse(Json.write(ServerSettings.defaults().toEncodedMap()));
    }

    private static void openMainFrame(ConsoleServices services) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // Keep the default look and feel.
        }
        new MainFrame(services, new BackgroundTasks()).setVisible(true);
    }
}
