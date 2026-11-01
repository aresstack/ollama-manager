package com.aresstack.ollamamanager.application.benchmark;

import com.aresstack.ollamamanager.application.common.CancellationToken;
import com.aresstack.ollamamanager.application.common.ErrorKind;
import com.aresstack.ollamamanager.application.common.OperationException;
import com.aresstack.ollamamanager.application.configuration.ConfigurationService;
import com.aresstack.ollamamanager.application.profile.ModelProfileRepository;
import com.aresstack.ollamamanager.application.profile.ProfileEnvironment;
import com.aresstack.ollamamanager.domain.model.ModelOptions;
import com.aresstack.ollamamanager.domain.profile.ModelProfile;
import com.aresstack.ollamamanager.domain.profile.ProfileScope;
import com.aresstack.ollamamanager.domain.server.ServerSetting;
import com.aresstack.ollamamanager.domain.server.ServerSettingCatalog;
import com.aresstack.ollamamanager.domain.server.ServerSettings;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Turn a measured optimum into a model profile: the stored desired settings with the measurement-relevant
 * settings of the run (when it recorded them) and {@code OLLAMA_NUM_PARALLEL} set to the measured server value, and the benchmark's {@code num_ctx} as model option. When the run's server value is unknown or
 * was Ollama's default, the profile leaves {@code OLLAMA_NUM_PARALLEL} unset rather than claim an unmeasured
 * value; the optimal number of chats stays in the profile name. Saving goes through the shared profile repository.
 * A profile of an embedding run carries its own workload label, because Ollama serves embedding-only models
 * with one slot and the measured value says nothing about chats.
 */
public final class BenchmarkProfiles {

    /** Workload label of profiles created from a chat benchmark. */
    public static final String WORKLOAD = "parallel-chats";

    /** Workload label of profiles created from an embedding benchmark. */
    public static final String EMBEDDING_WORKLOAD = "parallel-embeddings";

    /** Server settings that influence how many chats fit, carried from the run into the profile. */
    static final List<ServerSetting<?>> MEASURED_SETTINGS = Collections.unmodifiableList(
        Arrays.<ServerSetting<?>>asList(ServerSettingCatalog.PARALLELISM, ServerSettingCatalog.LOADED_MODELS,
            ServerSettingCatalog.CONTEXT_LENGTH, ServerSettingCatalog.KV_CACHE, ServerSettingCatalog.FLASH_ATTENTION));

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
        if (report.embedding()) {
            return report.model() + " - " + stage.parallel()
                + (stage.parallel() == 1 ? " embedding" : " parallel embeddings");
        }
        return report.model() + " - " + stage.parallel() + (stage.parallel() == 1 ? " chat" : " parallel chats");
    }

    /** Workload label of a profile of {@code report}. */
    static String workload(BenchmarkReport report) {
        return report.embedding() ? EMBEDDING_WORKLOAD : WORKLOAD;
    }

    /** OLLAMA_NUM_PARALLEL the profile should set: the run's measured server value, or {@code null} to leave it unset. */
    static Integer serverParallel(BenchmarkReport report) {
        try {
            if (report.serverNumParallel() != null) {
                return Integer.valueOf(report.serverNumParallel());
            }
        } catch (NumberFormatException ignored) {
            // Ollama's default or an unknown value: no number was measured.
        }
        return null;
    }

    /** Whether a profile of {@code report} would carry a measured OLLAMA_NUM_PARALLEL. */
    public static boolean hasServerValue(BenchmarkReport report) {
        return serverParallel(report) != null;
    }

    /**
     * The scope of a profile of {@code report}: the GPU, server version and model digest recorded during the run,
     * so that a run from an older server version or another GPU yields a profile the validity check flags as
     * outdated. Runs stored without that record fall back to the environment at save time.
     */
    private ProfileScope scope(BenchmarkReport report, CancellationToken cancellation) throws OperationException {
        BenchmarkEnvironment measured = report.environment();
        if (measured != null && measured.isComplete()) {
            return new ProfileScope(measured.model(), measured.gpuFingerprint(), measured.ollamaVersion(),
                workload(report));
        }
        ProfileEnvironment.Snapshot snapshot = environment.capture(cancellation);
        if (snapshot.gpuFingerprint() == null || snapshot.ollamaVersion() == null) {
            throw new OperationException(ErrorKind.INVALID_STATE,
                "cannot save a profile: GPU or server version unknown; is the server running?");
        }
        return new ProfileScope(BenchmarkEnvironment.from(snapshot, report.model()).model(), snapshot.gpuFingerprint(),
            snapshot.ollamaVersion(), workload(report));
    }

    /**
     * Settings the profile is built from: the stored desired settings, with the settings that shape the
     * measurement taken from the run when it recorded them. Model folder, bind address, keep-alive and queue stay
     * as the user has them now; a profile must not drag an old model folder back.
     */
    private ServerSettings baseSettings(BenchmarkReport report) throws OperationException {
        ServerSettings base = configuration.current().desiredServerSettings();
        ServerSettings measured = report.serverSettings();
        if (measured == null) {
            return base;
        }
        for (ServerSetting<?> setting : MEASURED_SETTINGS) {
            base = copy(base, measured, setting);
        }
        return base;
    }

    private static <T> ServerSettings copy(ServerSettings target, ServerSettings source, ServerSetting<T> setting) {
        return target.with(setting, source.isChosen(setting) ? source.get(setting) : null);
    }

    /**
     * Create and save revision 1 of a profile for {@code stage}.
     *
     * @throws OperationException {@code INVALID_STATE} when the running environment (GPU, server version) cannot
     *                            be captured, because a profile without exact scope would never be valid
     */
    public ModelProfile save(BenchmarkReport report, StageResult stage, String name, CancellationToken cancellation)
        throws OperationException {
        ProfileScope scope = scope(report, cancellation);
        ServerSettings settings = baseSettings(report).with(ServerSettingCatalog.PARALLELISM, serverParallel(report));
        ModelOptions options = report.numCtx() == null ? ModelOptions.none()
            : ModelOptions.none().withNumCtx(report.numCtx());
        String profileName = name == null || name.trim().isEmpty() ? defaultName(report, stage) : name.trim();
        ModelProfile profile = new ModelProfile(UUID.randomUUID().toString(), profileName, 1, scope, settings,
            options);
        profiles.save(profile);
        return profile;
    }
}
