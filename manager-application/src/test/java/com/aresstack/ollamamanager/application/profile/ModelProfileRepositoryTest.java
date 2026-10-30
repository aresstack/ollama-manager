package com.aresstack.ollamamanager.application.profile;

import com.aresstack.ollamamanager.application.common.ErrorKind;
import com.aresstack.ollamamanager.application.common.OperationException;
import com.aresstack.ollamamanager.domain.model.ModelOptions;
import com.aresstack.ollamamanager.domain.profile.ModelProfile;
import com.aresstack.ollamamanager.domain.server.ServerSettingCatalog;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

public class ModelProfileRepositoryTest {

    private final InMemoryConfigurationRepository configurations = new InMemoryConfigurationRepository();
    private final ModelProfileRepository repository = new ModelProfileRepository(configurations);

    @Test
    public void savesNewProfileAndRevisions() throws Exception {
        ModelProfile profile = ScenarioPreset.INTERACTIVE_CHAT.createProfile("p1", ProfileTestData.SCOPE);
        repository.save(profile);
        ModelProfile revised = profile.revise(profile.serverSettings().with(ServerSettingCatalog.PARALLELISM, 2),
            profile.modelOptions().withNumCtx(16384));
        repository.save(revised);

        ModelProfile stored = repository.find("p1");
        assertEquals(2, stored.revision());
        assertEquals(Integer.valueOf(2), stored.serverSettings().parallelism());
        assertEquals(Integer.valueOf(16384), stored.modelOptions().numCtx());
        assertEquals(1, repository.list().size());
    }

    @Test
    public void rejectsStaleRevision() throws Exception {
        ModelProfile profile = ScenarioPreset.INTERACTIVE_CHAT.createProfile("p1", ProfileTestData.SCOPE);
        repository.save(profile);
        repository.save(profile.revise(profile.serverSettings(), ModelOptions.none()));
        try {
            repository.save(profile.revise(profile.serverSettings(), profile.modelOptions()));
            fail();
        } catch (OperationException e) {
            assertEquals(ErrorKind.INVALID_STATE, e.kind());
        }
        assertEquals(ModelOptions.none(), repository.find("p1").modelOptions());
    }

    @Test
    public void newProfileMustStartAtRevisionOne() throws Exception {
        ModelProfile profile = ScenarioPreset.LONG_CONTEXT.createProfile("p1", ProfileTestData.SCOPE);
        try {
            repository.save(profile.revise(profile.serverSettings(), profile.modelOptions()));
            fail();
        } catch (OperationException e) {
            assertEquals(ErrorKind.INVALID_STATE, e.kind());
        }
    }

    @Test
    public void deletingActiveProfileClearsActiveId() throws Exception {
        repository.save(ScenarioPreset.INTERACTIVE_CHAT.createProfile("p1", ProfileTestData.SCOPE));
        repository.save(ScenarioPreset.LONG_CONTEXT.createProfile("p2", ProfileTestData.SCOPE));
        configurations.save(configurations.load().withActiveProfileId("p1"));

        repository.delete("p1");
        repository.delete("unknown");

        assertNull(repository.active());
        assertEquals(1, repository.list().size());
        assertEquals("p2", repository.list().get(0).id());
    }

    @Test
    public void presetsUseTheirWorkloadInScope() {
        ModelProfile profile = ScenarioPreset.PARALLEL_THROUGHPUT.createProfile("p", ProfileTestData.SCOPE);
        assertEquals("batch", profile.scope().workload());
        assertEquals(1, profile.revision());
        assertEquals(ProfileTestData.SCOPE.model(), profile.scope().model());
        assertEquals(Boolean.TRUE, profile.serverSettings().flashAttention());
    }
}
