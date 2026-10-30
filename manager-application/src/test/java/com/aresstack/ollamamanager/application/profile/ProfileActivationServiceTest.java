package com.aresstack.ollamamanager.application.profile;

import com.aresstack.ollamamanager.application.common.ErrorKind;
import com.aresstack.ollamamanager.application.common.OperationException;
import com.aresstack.ollamamanager.domain.profile.ActivationMode;
import com.aresstack.ollamamanager.domain.profile.ProfileActivationPlan;
import com.aresstack.ollamamanager.domain.server.RuntimeOwnership;
import com.aresstack.ollamamanager.domain.server.ServerSettingCatalog;
import com.aresstack.ollamamanager.domain.server.ServerSettings;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ProfileActivationServiceTest {

    private final InMemoryConfigurationRepository configurations = new InMemoryConfigurationRepository();
    private final ProfileActivationService service = new ProfileActivationService(configurations);

    @Before
    public void setUp() throws Exception {
        new ModelProfileRepository(configurations).save(
            ScenarioPreset.PARALLEL_THROUGHPUT.createProfile("batch", ProfileTestData.SCOPE));
    }

    @Test
    public void ownedServerNeedsConfirmedRestart() throws Exception {
        ServerSettings applied = ServerSettings.defaults();
        ProfileActivationPlan plan = service.plan("batch", applied, RuntimeOwnership.OWNED);
        assertEquals(ActivationMode.RESTART_OWNED_SERVER, plan.mode());
        assertTrue(plan.restartRequiredChanges().contains(ServerSettingCatalog.PARALLELISM));
        assertTrue(plan.restartRequiredChanges().contains(ServerSettingCatalog.KV_CACHE));

        try {
            service.activate("batch", applied, RuntimeOwnership.OWNED, false);
            fail();
        } catch (OperationException e) {
            assertEquals(ErrorKind.INVALID_STATE, e.kind());
        }
        assertNull(configurations.stored.activeProfileId());

        service.activate("batch", applied, RuntimeOwnership.OWNED, true);
        assertEquals("batch", configurations.stored.activeProfileId());
        assertEquals(Integer.valueOf(4), configurations.stored.desiredServerSettings().parallelism());
    }

    @Test
    public void externalServerCannotBeChangedAutomatically() throws Exception {
        ProfileActivationPlan plan = service.activate("batch", ServerSettings.defaults(),
            RuntimeOwnership.EXTERNAL, false);
        assertEquals(ActivationMode.CANNOT_APPLY_TO_EXTERNAL, plan.mode());
    }

    @Test
    public void matchingSettingsNeedOnlyRequestOptions() throws Exception {
        ServerSettings applied = ScenarioPreset.PARALLEL_THROUGHPUT.serverSettings();
        ProfileActivationPlan plan = service.activate("batch", applied, RuntimeOwnership.OWNED, false);
        assertEquals(ActivationMode.REQUEST_OPTIONS_ONLY, plan.mode());
        assertTrue(plan.restartRequiredChanges().isEmpty());
        assertEquals(Integer.valueOf(4096), plan.requestOptions().numCtx());
    }

    @Test
    public void stoppedServerAppliesOnNextStart() throws Exception {
        assertEquals(ActivationMode.APPLY_ON_NEXT_START,
            service.activate("batch", ServerSettings.defaults(), RuntimeOwnership.NONE, false).mode());
    }

    @Test
    public void unknownProfileIsNotFound() {
        try {
            service.plan("missing", ServerSettings.defaults(), RuntimeOwnership.OWNED);
            fail();
        } catch (OperationException e) {
            assertEquals(ErrorKind.NOT_FOUND, e.kind());
        }
    }
}
