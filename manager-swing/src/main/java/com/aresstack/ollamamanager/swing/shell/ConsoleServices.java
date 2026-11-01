package com.aresstack.ollamamanager.swing.shell;

import com.aresstack.ollamamanager.application.benchmark.BenchmarkHistoryPort;
import com.aresstack.ollamamanager.application.benchmark.ConcurrencyBenchmarkService;
import com.aresstack.ollamamanager.application.catalog.ModelCatalogService;
import com.aresstack.ollamamanager.application.configuration.ConfigurationService;
import com.aresstack.ollamamanager.application.configuration.ExistingSettingsImport;
import com.aresstack.ollamamanager.application.installation.PortableInstallationService;
import com.aresstack.ollamamanager.application.model.ModelService;
import com.aresstack.ollamamanager.application.port.GpuTelemetryPort;
import com.aresstack.ollamamanager.application.port.HardwareProbePort;
import com.aresstack.ollamamanager.application.profile.ModelProfileRepository;
import com.aresstack.ollamamanager.application.profile.ProfileActivationService;
import com.aresstack.ollamamanager.application.server.ServerLifecycleService;
import com.aresstack.ollamamanager.application.setup.SetupService;

/**
 * Application services the console may use, handed over by the composition root. Only the configuration service
 * is mandatory; every other service may be absent while it is not wired yet, and the UI then says so instead of
 * guessing. The UI never creates services or adapters itself.
 */
public final class ConsoleServices {

    private final ConfigurationService configuration;
    private final ServerLifecycleService serverLifecycle;
    private final HardwareProbePort hardwareProbe;
    private final GpuTelemetryPort gpuTelemetry;
    private final ModelService models;
    private final PortableInstallationService installations;
    private final ProfileActivationService profiles;
    private final SetupService setup;
    private final ModelProfileRepository profileRepository;
    private final ConcurrencyBenchmarkService benchmarks;
    private final BenchmarkHistoryPort benchmarkHistory;
    private final ExistingSettingsImport existingSettings;
    private final ModelCatalogService catalog;

    private ConsoleServices(Builder builder) {
        if (builder.configuration == null) {
            throw new IllegalArgumentException("configuration service must not be null");
        }
        this.configuration = builder.configuration;
        this.serverLifecycle = builder.serverLifecycle;
        this.hardwareProbe = builder.hardwareProbe;
        this.gpuTelemetry = builder.gpuTelemetry;
        this.models = builder.models;
        this.installations = builder.installations;
        this.profiles = builder.profiles;
        this.setup = builder.setup;
        this.profileRepository = builder.profileRepository;
        this.benchmarks = builder.benchmarks;
        this.benchmarkHistory = builder.benchmarkHistory;
        this.existingSettings = builder.existingSettings;
        this.catalog = builder.catalog;
    }

    /** Start a builder around the single shared configuration service. */
    public static Builder builder(ConfigurationService configuration) {
        return new Builder(configuration);
    }

    public ConfigurationService configuration() {
        return configuration;
    }

    /** @return the lifecycle service, or {@code null} when not wired */
    public ServerLifecycleService serverLifecycle() {
        return serverLifecycle;
    }

    /** @return the hardware probe, or {@code null} when not wired */
    public HardwareProbePort hardwareProbe() {
        return hardwareProbe;
    }

    /** @return the GPU telemetry port, or {@code null} when not wired */
    public GpuTelemetryPort gpuTelemetry() {
        return gpuTelemetry;
    }

    /** @return the model service, or {@code null} when not wired */
    public ModelService models() {
        return models;
    }

    /** @return the portable installation service, or {@code null} when not wired */
    public PortableInstallationService installations() {
        return installations;
    }

    /** @return the profile activation service, or {@code null} when not wired */
    public ProfileActivationService profiles() {
        return profiles;
    }

    /** @return the setup assistant service, or {@code null} when not wired */
    public SetupService setup() {
        return setup;
    }

    /**
     * @return the profile repository created by the composition root, or {@code null} when not wired; the UI
     *         never creates its own instance
     */
    public ModelProfileRepository profileRepository() {
        return profileRepository;
    }

    /** @return the concurrency benchmark, or {@code null} when not wired */
    public ConcurrencyBenchmarkService benchmarks() {
        return benchmarks;
    }

    /** @return stored benchmark reports, or {@code null} when not wired */
    public BenchmarkHistoryPort benchmarkHistory() {
        return benchmarkHistory;
    }

    /** Collects optional services. */
    /** @return the import of an existing installation's settings, or {@code null} when not wired */
    public ExistingSettingsImport existingSettings() {
        return existingSettings;
    }

    /** @return the online model catalog (Ollama library, Hugging Face), or {@code null} when not wired */
    public ModelCatalogService catalog() {
        return catalog;
    }

    public static final class Builder {
        private final ConfigurationService configuration;
        private ServerLifecycleService serverLifecycle;
        private HardwareProbePort hardwareProbe;
        private GpuTelemetryPort gpuTelemetry;
        private ModelService models;
        private PortableInstallationService installations;
        private ProfileActivationService profiles;
        private SetupService setup;
        private ModelProfileRepository profileRepository;
        private ConcurrencyBenchmarkService benchmarks;
        private BenchmarkHistoryPort benchmarkHistory;
        private ExistingSettingsImport existingSettings;
        private ModelCatalogService catalog;

        private Builder(ConfigurationService configuration) {
            this.configuration = configuration;
        }

        public Builder serverLifecycle(ServerLifecycleService value) {
            this.serverLifecycle = value;
            return this;
        }

        public Builder hardwareProbe(HardwareProbePort value) {
            this.hardwareProbe = value;
            return this;
        }

        public Builder gpuTelemetry(GpuTelemetryPort value) {
            this.gpuTelemetry = value;
            return this;
        }

        public Builder models(ModelService value) {
            this.models = value;
            return this;
        }

        public Builder installations(PortableInstallationService value) {
            this.installations = value;
            return this;
        }

        public Builder profiles(ProfileActivationService value) {
            this.profiles = value;
            return this;
        }

        public Builder setup(SetupService value) {
            this.setup = value;
            return this;
        }

        public Builder profileRepository(ModelProfileRepository value) {
            this.profileRepository = value;
            return this;
        }

        public Builder benchmarks(ConcurrencyBenchmarkService value, BenchmarkHistoryPort history) {
            this.benchmarks = value;
            this.benchmarkHistory = history;
            return this;
        }

        public Builder existingSettings(ExistingSettingsImport value) {
            this.existingSettings = value;
            return this;
        }

        public Builder catalog(ModelCatalogService value) {
            this.catalog = value;
            return this;
        }

        public ConsoleServices build() {
            return new ConsoleServices(this);
        }
    }
}
