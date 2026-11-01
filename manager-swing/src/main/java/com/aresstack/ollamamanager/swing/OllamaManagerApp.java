package com.aresstack.ollamamanager.swing;

import com.aresstack.ollamamanager.application.benchmark.BenchmarkProfiles;
import com.aresstack.ollamamanager.application.benchmark.ConcurrencyBenchmarkService;
import com.aresstack.ollamamanager.application.benchmark.ParallelismSweepService;
import com.aresstack.ollamamanager.application.catalog.ModelCatalogService;
import com.aresstack.ollamamanager.application.common.LifecycleLock;
import com.aresstack.ollamamanager.application.configuration.ConfigurationService;
import com.aresstack.ollamamanager.application.configuration.ExistingSettingsImport;
import com.aresstack.ollamamanager.application.hardware.GpuCompatibilityRules;
import com.aresstack.ollamamanager.application.hardware.PackageAdvisor;
import com.aresstack.ollamamanager.application.installation.PortableInstallationService;
import com.aresstack.ollamamanager.application.model.ModelService;
import com.aresstack.ollamamanager.application.profile.ModelProfileRepository;
import com.aresstack.ollamamanager.application.profile.ProfileActivationService;
import com.aresstack.ollamamanager.application.profile.ProfileEnvironment;
import com.aresstack.ollamamanager.application.server.OwnedServerUsage;
import com.aresstack.ollamamanager.application.server.ServerLifecycleService;
import com.aresstack.ollamamanager.application.setup.SetupService;
import com.aresstack.ollamamanager.domain.server.ServerSettings;
import com.aresstack.ollamamanager.infrastructure.catalog.HuggingFaceCatalog;
import com.aresstack.ollamamanager.infrastructure.catalog.OllamaLibraryCatalog;
import com.aresstack.ollamamanager.infrastructure.distribution.GitHubReleaseCatalog;
import com.aresstack.ollamamanager.infrastructure.distribution.PortableDistributionInstaller;
import com.aresstack.ollamamanager.infrastructure.hardware.SystemHardwareProbe;
import com.aresstack.ollamamanager.infrastructure.json.Json;
import com.aresstack.ollamamanager.infrastructure.ollamaapi.HttpOllamaApiClient;
import com.aresstack.ollamamanager.infrastructure.persistence.JsonBenchmarkHistory;
import com.aresstack.ollamamanager.infrastructure.persistence.JsonConfigurationRepository;
import com.aresstack.ollamamanager.infrastructure.persistence.UserDataDirectory;
import com.aresstack.ollamamanager.infrastructure.runtime.ExternalServerControls;
import com.aresstack.ollamamanager.infrastructure.runtime.SystemInstalledEnvironment;
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
    /** How long to wait for the port after an external server was stopped. */
    static final long PORT_RELEASE_MILLIS = 10000;

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
        HttpOllamaApiClient api = new HttpOllamaApiClient();
        ProcessOllamaRuntime runtime = new ProcessOllamaRuntime(portableRoot);
        LifecycleLock lifecycleLock = new LifecycleLock();
        ServerLifecycleService lifecycle =
            new ServerLifecycleService(runtime, STOP_GRACE_PERIOD_MILLIS, lifecycleLock,
                ExternalServerControls.forCurrentSystem(), PORT_RELEASE_MILLIS);
        PortableInstallationService installations = new PortableInstallationService(
            GitHubReleaseCatalog.defaults(), PortableDistributionInstaller.defaults(), portableRoot,
            new OwnedServerUsage(lifecycle), lifecycleLock);
        SetupService setup = new SetupService(runtime, api, lifecycle, hardwareProbe,
            new PackageAdvisor(new GpuCompatibilityRules()), installations);
        ModelService models = new ModelService(api);
        ProfileEnvironment profileEnvironment = ProfileEnvironment.live(hardwareProbe, lifecycle, models, api);
        JsonBenchmarkHistory benchmarkHistory = new JsonBenchmarkHistory(dataDirectory);
        ConcurrencyBenchmarkService benchmarks =
            new ConcurrencyBenchmarkService(api, hardwareProbe.nvidiaSmi(), benchmarkHistory, profileEnvironment);
        ModelProfileRepository profileRepository = new ModelProfileRepository(configuration);
        return ConsoleServices.builder(configuration)
            .benchmarks(benchmarks, benchmarkHistory)
            .sweeps(new ParallelismSweepService(lifecycle, benchmarks, lifecycleLock))
            .benchmarkProfiles(new BenchmarkProfiles(profileRepository, configuration, profileEnvironment))
            .serverLifecycle(lifecycle)
            .hardwareProbe(hardwareProbe)
            .gpuTelemetry(hardwareProbe.nvidiaSmi())
            .models(models)
            .catalog(new ModelCatalogService(models,
                Arrays.asList(OllamaLibraryCatalog.defaults(), HuggingFaceCatalog.defaults())))
            .installations(installations)
            .profiles(new ProfileActivationService(configuration, profileEnvironment, lifecycle,
                lifecycleLock))
            .profileRepository(profileRepository)
            .setup(setup)
            .existingSettings(new ExistingSettingsImport(configuration, SystemInstalledEnvironment.forCurrentSystem()))
            .build();
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
