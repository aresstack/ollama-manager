package com.aresstack.ollamamanager.application.benchmark;

import com.aresstack.ollamamanager.application.common.CancellationToken;
import com.aresstack.ollamamanager.application.common.ErrorKind;
import com.aresstack.ollamamanager.application.common.OperationException;
import com.aresstack.ollamamanager.application.configuration.ConfigurationService;
import com.aresstack.ollamamanager.application.port.ConfigurationRepository;
import com.aresstack.ollamamanager.application.profile.ModelProfileRepository;
import com.aresstack.ollamamanager.application.profile.ProfileEnvironment;
import com.aresstack.ollamamanager.domain.configuration.ManagerConfiguration;
import com.aresstack.ollamamanager.domain.hardware.GpuFingerprint;
import com.aresstack.ollamamanager.domain.model.ModelIdentity;
import com.aresstack.ollamamanager.domain.profile.ModelProfile;
import com.aresstack.ollamamanager.domain.server.ServerSettingCatalog;
import com.aresstack.ollamamanager.domain.server.ServerSettings;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class BenchmarkProfilesTest {

    private static final class MemoryRepository implements ConfigurationRepository {
        private ManagerConfiguration stored = ManagerConfiguration.defaults();

        @Override
        public ManagerConfiguration load() {
            return stored;
        }

        @Override
        public void save(ManagerConfiguration configuration) {
            stored = configuration;
        }
    }

    private static StageResult stage(int parallel, double total) {
        return new StageResult(parallel, parallel, 0, total / parallel, total / parallel, total, 10, 20, 100, 200,
            1000, null, 1.0, null);
    }

    private static BenchmarkReport report(Integer numCtx, String serverParallel) {
        return report(numCtx, serverParallel, null);
    }

    private static BenchmarkReport report(Integer numCtx, String serverParallel, ServerSettings settings) {
        return report(numCtx, serverParallel, settings, null);
    }

    private static BenchmarkReport report(Integer numCtx, String serverParallel, ServerSettings settings,
                                          BenchmarkEnvironment environment) {
        return new BenchmarkReport("r1", 0, "llama3.1:8b", numCtx, 128, serverParallel, settings, environment, 100,
            Arrays.asList(stage(1, 50), stage(4, 120), stage(8, 110)), true);
    }

    private static ProfileEnvironment environment(final GpuFingerprint gpu, final String version) {
        return cancellation -> new ProfileEnvironment.Snapshot(gpu, version,
            Collections.singletonList(new ModelIdentity("llama3.1:8b", "sha256:abc", "Q4_K_M")));
    }

    @Test
    public void savesOptimumWithMeasuredServerValueAndContext() throws Exception {
        ConfigurationService configuration = new ConfigurationService(new MemoryRepository());
        configuration.update(current -> current.withDesiredServerSettings(
            ServerSettings.defaults().with(ServerSettingCatalog.FLASH_ATTENTION, Boolean.TRUE)));
        ModelProfileRepository repository = new ModelProfileRepository(configuration);
        BenchmarkProfiles profiles = new BenchmarkProfiles(repository, configuration,
            environment(new GpuFingerprint("rtx"), "0.40.13"));

        BenchmarkReport report = report(4096, "8");
        ModelProfile saved = profiles.save(report, report.optimum(), "  ", CancellationToken.none());

        assertEquals("llama3.1:8b - 4 parallel chats", saved.name());
        assertEquals(1, saved.revision());
        assertEquals(Integer.valueOf(8), saved.serverSettings().parallelism());
        assertEquals(Boolean.TRUE, saved.serverSettings().flashAttention());
        assertEquals(Integer.valueOf(4096), saved.modelOptions().numCtx());
        assertEquals("sha256:abc", saved.scope().model().digest());
        assertEquals("rtx", saved.scope().gpuFingerprint().value());
        assertEquals("0.40.13", saved.scope().ollamaVersion());
        assertEquals(BenchmarkProfiles.WORKLOAD, saved.scope().workload());
        assertEquals(1, repository.list().size());
        assertEquals(saved.id(), repository.list().get(0).id());
    }

    @Test
    public void prefersTheSettingsTheRunWasMeasuredWith() throws Exception {
        ConfigurationService configuration = new ConfigurationService(new MemoryRepository());
        configuration.update(current -> current.withDesiredServerSettings(
            ServerSettings.defaults().with(ServerSettingCatalog.FLASH_ATTENTION, Boolean.FALSE)
                .with(ServerSettingCatalog.PARALLELISM, 2)));
        BenchmarkProfiles profiles = new BenchmarkProfiles(new ModelProfileRepository(configuration), configuration,
            environment(new GpuFingerprint("rtx"), "0.40.13"));
        ServerSettings measured = ServerSettings.defaults().with(ServerSettingCatalog.FLASH_ATTENTION, Boolean.TRUE)
            .with(ServerSettingCatalog.PARALLELISM, 8);
        BenchmarkReport report = report(null, "8", measured);
        ModelProfile saved = profiles.save(report, report.optimum(), null, CancellationToken.none());
        // The stored desired settings were never benchmarked; the run's settings were.
        assertEquals(Boolean.TRUE, saved.serverSettings().flashAttention());
        assertEquals(Integer.valueOf(8), saved.serverSettings().parallelism());
    }

    @Test
    public void leavesServerValueUnsetWhenItWasNotMeasured() throws Exception {
        ConfigurationService configuration = new ConfigurationService(new MemoryRepository());
        configuration.update(current -> current.withDesiredServerSettings(
            ServerSettings.defaults().with(ServerSettingCatalog.PARALLELISM, 2)));
        BenchmarkProfiles profiles = new BenchmarkProfiles(new ModelProfileRepository(configuration), configuration,
            environment(GpuFingerprint.CPU_ONLY, "0.40.13"));
        BenchmarkReport report = report(null, null);
        ModelProfile saved = profiles.save(report, report.optimum(), "Mine", CancellationToken.none());
        assertEquals("Mine", saved.name());
        // Four chats were optimal, but OLLAMA_NUM_PARALLEL=4 was never measured, so the profile does not claim it.
        assertNull(saved.serverSettings().parallelism());
        assertNull(saved.modelOptions().numCtx());
        assertFalse(BenchmarkProfiles.hasServerValue(report(null, "default")));
        assertTrue(BenchmarkProfiles.hasServerValue(report(null, "4")));
        assertEquals(Integer.valueOf(4), BenchmarkProfiles.serverParallel(report(null, "4")));
    }

    @Test
    public void scopesTheProfileToTheEnvironmentOfTheRunNotOfTheSave() throws Exception {
        ConfigurationService configuration = new ConfigurationService(new MemoryRepository());
        // The server was upgraded and the model tag re-pulled since the run.
        BenchmarkProfiles profiles = new BenchmarkProfiles(new ModelProfileRepository(configuration), configuration,
            environment(new GpuFingerprint("rtx"), "0.41.0"));
        BenchmarkEnvironment measured = new BenchmarkEnvironment(new GpuFingerprint("old-gpu"), "0.40.13",
            new ModelIdentity("llama3.1:8b", "sha256:old", "Q4_K_M"));
        BenchmarkReport report = report(null, "4", null, measured);
        ModelProfile saved = profiles.save(report, report.optimum(), null, CancellationToken.none());
        assertEquals("old-gpu", saved.scope().gpuFingerprint().value());
        assertEquals("0.40.13", saved.scope().ollamaVersion());
        assertEquals("sha256:old", saved.scope().model().digest());
    }

    @Test
    public void fallsBackToTheCurrentEnvironmentWhenTheRunRecordedAnIncompleteOne() throws Exception {
        ConfigurationService configuration = new ConfigurationService(new MemoryRepository());
        BenchmarkProfiles profiles = new BenchmarkProfiles(new ModelProfileRepository(configuration), configuration,
            environment(new GpuFingerprint("rtx"), "0.40.13"));
        BenchmarkEnvironment partial = new BenchmarkEnvironment(null, "0.40.13", ModelIdentity.named("llama3.1:8b"));
        BenchmarkReport report = report(null, "4", null, partial);
        ModelProfile saved = profiles.save(report, report.optimum(), null, CancellationToken.none());
        assertEquals("rtx", saved.scope().gpuFingerprint().value());
        assertEquals("sha256:abc", saved.scope().model().digest());
    }

    @Test
    public void refusesWithoutKnownEnvironment() throws Exception {
        ConfigurationService configuration = new ConfigurationService(new MemoryRepository());
        BenchmarkProfiles profiles = new BenchmarkProfiles(new ModelProfileRepository(configuration), configuration,
            environment(null, "0.40.13"));
        BenchmarkReport report = report(null, "4");
        try {
            profiles.save(report, report.optimum(), null, CancellationToken.none());
            fail("expected INVALID_STATE");
        } catch (OperationException e) {
            assertEquals(ErrorKind.INVALID_STATE, e.kind());
        }
        assertEquals(0, new ModelProfileRepository(configuration).list().size());
    }
}
