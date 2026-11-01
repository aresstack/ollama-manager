package com.aresstack.ollamamanager.application.benchmark;

import com.aresstack.ollamamanager.application.common.CancellationToken;
import com.aresstack.ollamamanager.application.common.ErrorKind;
import com.aresstack.ollamamanager.application.common.OperationException;
import com.aresstack.ollamamanager.application.configuration.ConfigurationService;
import com.aresstack.ollamamanager.application.profile.ModelProfileRepository;
import com.aresstack.ollamamanager.application.profile.ProfileEnvironment;
import com.aresstack.ollamamanager.domain.model.ModelIdentity;
import com.aresstack.ollamamanager.domain.model.ModelOptions;
import com.aresstack.ollamamanager.domain.profile.ModelProfile;
import com.aresstack.ollamamanager.domain.profile.ProfileScope;
import com.aresstack.ollamamanager.domain.server.ServerSettingCatalog;
import com.aresstack.ollamamanager.domain.server.ServerSettings;

import java.util.UUID;

/**
 * Turn a measured optimum into a model profile: the stored server settings with {@code OLLAMA_NUM_PARALLEL} set
 * to the measured server value (or, when the server value is unknown, to the optimal number of chats) and the
 * benchmark's {@code num_ctx} as model option. Saving goes through the shared profile repository.
 */
public final class BenchmarkProfiles {

    /** Workload label of profiles created from a benchmark. */
    public static final String WORKLOAD = "parallel-chats";

    private final ModelProfileRepository profiles;
    private final ConfigurationService configuration;
    private final ProfileEnvironment environment;

    public BenchmarkProfiles(ModelProfileRepository profiles, ConfigurationService configuration,
                             ProfileEnvironment environment) {
        if (profiles == null || configuration == null || environment == null) {
            throw new IllegalArgumentException("profiles, configuration and environment must not be null");
        }
        this.profiles = profiles;
        this.configuration = configuration;
        this.environment = environment;
    }

    /** Default profile name for {@code stage} of {@code report}. */
    public static String defaultName(BenchmarkReport report, StageResult stage) {
        return report.model() + " - " + stage.parallel() + (stage.parallel() == 1 ? " chat" : " parallel chats");
    }

    /** OLLAMA_NUM_PARALLEL the profile should set for {@code stage}. */
    static int serverParallel(BenchmarkReport report, StageResult stage) {
        try {
            if (report.serverNumParallel() != null) {
                return Integer.parseInt(report.serverNumParallel());
            }
        } catch (NumberFormatException ignored) {
            // "default" or unknown: fall back to the measured optimum.
        }
        return stage.parallel();
    }

    /**
     * Create and save revision 1 of a profile for {@code stage}.
     *
     * @throws OperationException {@code INVALID_STATE} when the running environment (GPU, server version) cannot
     *                            be captured, because a profile without exact scope would never be valid
     */
    public ModelProfile save(BenchmarkReport report, StageResult stage, String name, CancellationToken cancellation)
        throws OperationException {
        ProfileEnvironment.Snapshot snapshot = environment.capture(cancellation);
        if (snapshot.gpuFingerprint() == null || snapshot.ollamaVersion() == null) {
            throw new OperationException(ErrorKind.INVALID_STATE,
                "cannot save a profile: GPU or server version unknown; is the server running?");
        }
        ModelIdentity model = ModelIdentity.named(report.model());
        if (snapshot.installedModels() != null) {
            for (ModelIdentity installed : snapshot.installedModels()) {
                if (installed.name().equalsIgnoreCase(model.name())) {
                    model = installed;
                }
            }
        }
        ProfileScope scope = new ProfileScope(model, snapshot.gpuFingerprint(), snapshot.ollamaVersion(), WORKLOAD);
        ServerSettings settings = configuration.current().desiredServerSettings()
            .with(ServerSettingCatalog.PARALLELISM, serverParallel(report, stage));
        ModelOptions options = report.numCtx() == null ? ModelOptions.none()
            : ModelOptions.none().withNumCtx(report.numCtx());
        String profileName = name == null || name.trim().isEmpty() ? defaultName(report, stage) : name.trim();
        ModelProfile profile = new ModelProfile(UUID.randomUUID().toString(), profileName, 1, scope, settings,
            options);
        profiles.save(profile);
        return profile;
    }
}
