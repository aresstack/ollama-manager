package com.aresstack.ollamamanager.application.profile;

import com.aresstack.ollamamanager.domain.model.ModelOptions;
import com.aresstack.ollamamanager.domain.profile.ModelProfile;
import com.aresstack.ollamamanager.domain.profile.ProfileScope;
import com.aresstack.ollamamanager.domain.server.KvCacheType;
import com.aresstack.ollamamanager.domain.server.ServerSettingCatalog;
import com.aresstack.ollamamanager.domain.server.ServerSettings;

/**
 * Built-in starting points for a profile. They are templates, not measured results: a user profile created
 * from one must still be benchmarked (#8) before it counts as optimised.
 */
public enum ScenarioPreset {
    INTERACTIVE_CHAT("chat", "Interactive chat",
        ServerSettings.defaults().with(ServerSettingCatalog.PARALLELISM, 1),
        new ModelOptions(8192, null, null, null)),
    PARALLEL_THROUGHPUT("batch", "Parallel throughput",
        ServerSettings.defaults()
            .with(ServerSettingCatalog.PARALLELISM, 4)
            .with(ServerSettingCatalog.FLASH_ATTENTION, Boolean.TRUE)
            .with(ServerSettingCatalog.KV_CACHE, KvCacheType.Q8_0),
        new ModelOptions(4096, null, null, null)),
    LONG_CONTEXT("long-context", "Long context",
        ServerSettings.defaults()
            .with(ServerSettingCatalog.PARALLELISM, 1)
            .with(ServerSettingCatalog.FLASH_ATTENTION, Boolean.TRUE)
            .with(ServerSettingCatalog.KV_CACHE, KvCacheType.Q8_0),
        new ModelOptions(32768, null, null, null));

    private final String workload;
    private final String label;
    private final ServerSettings serverSettings;
    private final ModelOptions modelOptions;

    ScenarioPreset(String workload, String label, ServerSettings serverSettings, ModelOptions modelOptions) {
        this.workload = workload;
        this.label = label;
        this.serverSettings = serverSettings;
        this.modelOptions = modelOptions;
    }

    /** Return the workload label written into the profile scope. */
    public String workload() {
        return workload;
    }

    public String label() {
        return label;
    }

    public ServerSettings serverSettings() {
        return serverSettings;
    }

    public ModelOptions modelOptions() {
        return modelOptions;
    }

    /**
     * Create revision 1 of a user profile from this preset. {@code baseScope} supplies model, GPU fingerprint
     * and Ollama version; the workload is replaced by this preset's workload.
     */
    public ModelProfile createProfile(String id, ProfileScope baseScope) {
        ProfileScope scope = new ProfileScope(baseScope.model(), baseScope.gpuFingerprint(),
            baseScope.ollamaVersion(), workload);
        return new ModelProfile(id, label + " - " + scope.model().name(), 1, scope, serverSettings, modelOptions);
    }
}
