package com.aresstack.ollamamanager.swing.shell;

import com.aresstack.ollamamanager.application.configuration.ConfigurationService;
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

    /** Collects optional services. */
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

        public ConsoleServices build() {
            return new ConsoleServices(this);
        }
    }
}
