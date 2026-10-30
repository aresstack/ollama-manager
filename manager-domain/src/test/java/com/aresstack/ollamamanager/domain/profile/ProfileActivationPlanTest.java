package com.aresstack.ollamamanager.domain.profile;

import com.aresstack.ollamamanager.domain.hardware.GpuFingerprint;
import com.aresstack.ollamamanager.domain.model.ModelIdentity;
import com.aresstack.ollamamanager.domain.model.ModelOptions;
import com.aresstack.ollamamanager.domain.server.RuntimeOwnership;
import com.aresstack.ollamamanager.domain.server.ServerSettingCatalog;
import com.aresstack.ollamamanager.domain.server.ServerSettings;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ProfileActivationPlanTest {

    private static final ServerSettings APPLIED = ServerSettings.defaults().with(ServerSettingCatalog.PARALLELISM, 2);

    @Test
    public void requestOptionsOnlyWhenServerSettingsMatch() {
        ProfileActivationPlan plan = ProfileActivationPlan.plan(profile(APPLIED), APPLIED, RuntimeOwnership.OWNED);

        assertEquals(ActivationMode.REQUEST_OPTIONS_ONLY, plan.mode());
        assertTrue(plan.restartRequiredChanges().isEmpty());
        assertEquals(Integer.valueOf(16384), plan.requestOptions().numCtx());
    }

    @Test
    public void ownedServerNeedsRestartForServerWideChange() {
        ServerSettings target = APPLIED.with(ServerSettingCatalog.PARALLELISM, 8);

        ProfileActivationPlan plan = ProfileActivationPlan.plan(profile(target), APPLIED, RuntimeOwnership.OWNED);

        assertEquals(ActivationMode.RESTART_OWNED_SERVER, plan.mode());
        assertEquals(Collections.singletonList(ServerSettingCatalog.PARALLELISM), plan.restartRequiredChanges());
    }

    @Test
    public void externalServerCannotBeChangedAutomatically() {
        ServerSettings target = APPLIED.with(ServerSettingCatalog.PARALLELISM, 8);

        assertEquals(ActivationMode.CANNOT_APPLY_TO_EXTERNAL,
            ProfileActivationPlan.plan(profile(target), APPLIED, RuntimeOwnership.EXTERNAL).mode());
    }

    @Test
    public void stoppedServerAppliesOnNextStart() {
        assertEquals(ActivationMode.APPLY_ON_NEXT_START,
            ProfileActivationPlan.plan(profile(APPLIED), APPLIED, RuntimeOwnership.NONE).mode());
    }

    @Test
    public void reviseIncrementsRevision() {
        ModelProfile revised = profile(APPLIED).revise(APPLIED, ModelOptions.none());

        assertEquals(2, revised.revision());
    }

    @Test
    public void scopeBecomesStaleWhenVersionOrDigestChanges() {
        ProfileScope scope = scope("sha256:a", "0.12.0");

        assertTrue(scope.isValidFor(scope("sha256:a", "0.12.0")));
        assertEquals(false, scope.isValidFor(scope("sha256:b", "0.12.0")));
        assertEquals(false, scope.isValidFor(scope("sha256:a", "0.13.0")));
    }

    private static ModelProfile profile(ServerSettings settings) {
        return new ModelProfile("p1", "Chat", 1, scope("sha256:a", "0.12.0"), settings,
            new ModelOptions(16384, null, null, null));
    }

    private static ProfileScope scope(String digest, String version) {
        return new ProfileScope(new ModelIdentity("llama3.1:8b", digest, "Q4_K_M"), GpuFingerprint.CPU_ONLY,
            version, "chat");
    }
}
