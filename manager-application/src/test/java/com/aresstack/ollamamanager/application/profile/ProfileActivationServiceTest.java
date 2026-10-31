package com.aresstack.ollamamanager.application.profile;

import com.aresstack.ollamamanager.application.common.ErrorKind;
import com.aresstack.ollamamanager.application.common.OperationException;
import com.aresstack.ollamamanager.application.common.CancellationToken;
import com.aresstack.ollamamanager.application.configuration.ConfigurationService;
import com.aresstack.ollamamanager.domain.hardware.GpuFingerprint;
import com.aresstack.ollamamanager.domain.model.ModelIdentity;
import com.aresstack.ollamamanager.domain.profile.ActivationMode;
import com.aresstack.ollamamanager.domain.profile.ProfileActivationPlan;
import com.aresstack.ollamamanager.domain.server.RuntimeOwnership;
import com.aresstack.ollamamanager.domain.server.ServerSettingCatalog;
import com.aresstack.ollamamanager.domain.server.ServerSettings;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ProfileActivationServiceTest {

    private static final CancellationToken NONE = CancellationToken.none();

    private final InMemoryConfigurationRepository configurations = new InMemoryConfigurationRepository();
    private final ConfigurationService configurationService = new ConfigurationService(configurations);
    private final ModelProfileRepository repository = new ModelProfileRepository(configurationService);
    private ProfileEnvironment.Snapshot snapshot = new ProfileEnvironment.Snapshot(GpuFingerprint.CPU_ONLY,
        "0.12.6", Collections.singletonList(ProfileTestData.SCOPE.model()));
    private final ProfileActivationService service =
        new ProfileActivationService(configurationService, cancellation -> snapshot);

    @Before
    public void setUp() throws Exception {
        repository.save(
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
            service.activate("batch", applied, RuntimeOwnership.OWNED, false, null, NONE);
            fail();
        } catch (OperationException e) {
            assertEquals(ErrorKind.INVALID_STATE, e.kind());
        }
        assertNull(configurations.stored.activeProfileId());

        service.activate("batch", applied, RuntimeOwnership.OWNED, true, null, NONE);
        assertEquals("batch", configurations.stored.activeProfileId());
        assertEquals(Integer.valueOf(4), configurations.stored.desiredServerSettings().parallelism());
    }

    @Test
    public void externalServerCannotBeChangedAutomatically() throws Exception {
        ProfileActivationPlan plan = service.activate("batch", ServerSettings.defaults(),
            RuntimeOwnership.EXTERNAL, false, null, NONE);
        assertEquals(ActivationMode.CANNOT_APPLY_TO_EXTERNAL, plan.mode());
    }

    @Test
    public void matchingSettingsNeedOnlyRequestOptions() throws Exception {
        ServerSettings applied = ScenarioPreset.PARALLEL_THROUGHPUT.serverSettings();
        ProfileActivationPlan plan = service.activate("batch", applied, RuntimeOwnership.OWNED, false, null, NONE);
        assertEquals(ActivationMode.REQUEST_OPTIONS_ONLY, plan.mode());
        assertTrue(plan.restartRequiredChanges().isEmpty());
        assertEquals(Integer.valueOf(4096), plan.requestOptions().numCtx());
    }

    @Test
    public void stoppedServerAppliesOnNextStart() throws Exception {
        assertEquals(ActivationMode.APPLY_ON_NEXT_START,
            service.activate("batch", ServerSettings.defaults(), RuntimeOwnership.NONE, false, null, NONE).mode());
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

    @Test
    public void activationKeepsProfileRevisionSavedMeanwhile() throws Exception {
        repository.save(repository.find("batch").revise(ServerSettings.defaults(),
            ScenarioPreset.PARALLEL_THROUGHPUT.modelOptions()));
        service.activate("batch", ServerSettings.defaults(), RuntimeOwnership.OWNED, false, null, NONE);
        assertEquals(2, repository.find("batch").revision());
        assertEquals("batch", configurations.stored.activeProfileId());
    }

    @Test
    public void outdatedProfileIsRejectedWithoutApproval() throws Exception {
        snapshot = new ProfileEnvironment.Snapshot(GpuFingerprint.CPU_ONLY, "0.13.0",
            Collections.singletonList(ProfileTestData.SCOPE.model()));

        assertRejected(null);
        assertNull(configurations.stored.activeProfileId());
    }

    @Test
    public void unverifiedProfileIsRejectedWithoutApproval() throws Exception {
        snapshot = new ProfileEnvironment.Snapshot(GpuFingerprint.CPU_ONLY, null, null);

        assertEquals(ProfileValidity.Result.UNVERIFIED, service.validity("batch", NONE).result());
        assertRejected(null);
    }

    @Test
    public void approvalMatchingTheCurrentEvaluationActivates() throws Exception {
        snapshot = new ProfileEnvironment.Snapshot(GpuFingerprint.CPU_ONLY, "0.13.0",
            Collections.singletonList(ProfileTestData.SCOPE.model()));
        ProfileValidity validity = service.validity("batch", NONE);
        assertEquals(ProfileValidity.Result.OUTDATED, validity.result());

        service.activate("batch", ServerSettings.defaults(), RuntimeOwnership.NONE, false,
            new ValidityApproval("batch", 1, validity.result(), validity.findings()), NONE);

        assertEquals("batch", configurations.stored.activeProfileId());
    }

    @Test
    public void approvalForOtherFindingsIsRejected() throws Exception {
        snapshot = new ProfileEnvironment.Snapshot(GpuFingerprint.CPU_ONLY, "0.13.0",
            Collections.singletonList(ProfileTestData.SCOPE.model()));
        ProfileValidity seen = service.validity("batch", NONE);
        snapshot = new ProfileEnvironment.Snapshot(new GpuFingerprint("nvidia:rtx-5080"), "0.13.0",
            Collections.singletonList(new ModelIdentity("llama3.1:8b", "bbb", "Q8_0")));

        assertRejected(new ValidityApproval("batch", 1, seen.result(), seen.findings()));
    }

    @Test
    public void approvalForOlderRevisionIsRejected() throws Exception {
        snapshot = new ProfileEnvironment.Snapshot(GpuFingerprint.CPU_ONLY, "0.13.0",
            Collections.singletonList(ProfileTestData.SCOPE.model()));
        ProfileValidity seen = service.validity("batch", NONE);
        repository.save(repository.find("batch").revise(ServerSettings.defaults(),
            ScenarioPreset.PARALLEL_THROUGHPUT.modelOptions()));

        assertRejected(new ValidityApproval("batch", 1, seen.result(), seen.findings()));
    }

    @Test
    public void validityApprovalDoesNotReplaceRestartConfirmation() throws Exception {
        snapshot = new ProfileEnvironment.Snapshot(GpuFingerprint.CPU_ONLY, null, null);
        ProfileValidity seen = service.validity("batch", NONE);
        ValidityApproval approval = new ValidityApproval("batch", 1, seen.result(), seen.findings());
        try {
            service.activate("batch", ServerSettings.defaults(), RuntimeOwnership.OWNED, false, approval, NONE);
            fail("a needed restart still needs its own confirmation");
        } catch (OperationException e) {
            assertTrue(e.getMessage().contains("restart"));
        }
        service.activate("batch", ServerSettings.defaults(), RuntimeOwnership.OWNED, true, approval, NONE);
        assertEquals("batch", configurations.stored.activeProfileId());
    }

    @Test
    public void approvalIsNotNeededForValidProfile() throws Exception {
        assertEquals(ProfileValidity.Result.VALID, service.validity("batch", NONE).result());
        assertEquals(Arrays.<String>asList(), service.validity("batch", NONE).findings());
    }

    private void assertRejected(ValidityApproval approval) {
        try {
            service.activate("batch", ServerSettings.defaults(), RuntimeOwnership.NONE, false, approval, NONE);
            fail("an invalid profile must not be activated");
        } catch (OperationException e) {
            assertEquals(ErrorKind.INVALID_STATE, e.kind());
        }
    }
}
