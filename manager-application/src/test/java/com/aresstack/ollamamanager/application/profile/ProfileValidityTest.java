package com.aresstack.ollamamanager.application.profile;

import com.aresstack.ollamamanager.application.common.CancellationToken;
import com.aresstack.ollamamanager.application.common.ErrorKind;
import com.aresstack.ollamamanager.application.common.LifecycleLock;
import com.aresstack.ollamamanager.application.common.OperationException;
import com.aresstack.ollamamanager.application.model.ModelService;
import com.aresstack.ollamamanager.application.port.HardwareProbePort;
import com.aresstack.ollamamanager.application.port.OllamaApiPort;
import com.aresstack.ollamamanager.application.port.OllamaRuntimePort;
import com.aresstack.ollamamanager.application.server.ServerLifecycleService;
import com.aresstack.ollamamanager.domain.hardware.GpuFingerprint;
import com.aresstack.ollamamanager.domain.model.ModelIdentity;
import com.aresstack.ollamamanager.domain.profile.ModelProfile;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ProfileValidityTest {

    private final ModelProfile profile = ScenarioPreset.PARALLEL_THROUGHPUT.createProfile("batch",
        ProfileTestData.SCOPE);
    private final List<ModelIdentity> sameModel = Collections.singletonList(ProfileTestData.SCOPE.model());

    @Test
    public void matchingEnvironmentIsValid() {
        ProfileValidity validity = evaluate(GpuFingerprint.CPU_ONLY, "0.12.6", sameModel);

        assertEquals(ProfileValidity.Result.VALID, validity.result());
        assertTrue(validity.findings().isEmpty());
    }

    @Test
    public void changedDigestIsOutdated() {
        ProfileValidity validity = evaluate(GpuFingerprint.CPU_ONLY, "0.12.6",
            Collections.singletonList(new ModelIdentity("llama3.1:8b", "bbb", "Q4_K_M")));

        assertEquals(ProfileValidity.Result.OUTDATED, validity.result());
    }

    @Test
    public void unknownValuesAreUnverifiedNeverValid() {
        ProfileValidity validity = evaluate(null, null, null);

        assertEquals(ProfileValidity.Result.UNVERIFIED, validity.result());
        assertEquals(3, validity.findings().size());
    }

    @Test
    public void outdatedWinsOverUnverified() {
        ProfileValidity validity = evaluate(new GpuFingerprint("nvidia:rtx-5080"), null, null);

        assertEquals(ProfileValidity.Result.OUTDATED, validity.result());
    }

    @Test
    public void equalEvaluationsAreEqual() {
        assertEquals(evaluate(null, "0.13.0", sameModel), evaluate(null, "0.13.0", sameModel));
    }

    @Test
    public void liveEnvironmentWithoutServerLeavesServerValuesUnknown() throws Exception {
        HardwareProbePort failingProbe = cancellation -> {
            throw new OperationException(ErrorKind.PROCESS_FAILED, "probe failed");
        };
        ServerLifecycleService lifecycle = new ServerLifecycleService(unsupported(OllamaRuntimePort.class), 0,
            new LifecycleLock());
        ProfileEnvironment environment = ProfileEnvironment.live(failingProbe, lifecycle,
            new ModelService(unsupported(OllamaApiPort.class)));

        ProfileEnvironment.Snapshot snapshot = environment.capture(CancellationToken.none());

        assertNull(snapshot.gpuFingerprint());
        assertNull(snapshot.ollamaVersion());
        assertNull(snapshot.installedModels());
        assertEquals(ProfileValidity.Result.UNVERIFIED, ProfileValidity.evaluate(profile, snapshot).result());
    }

    private ProfileValidity evaluate(GpuFingerprint fingerprint, String version, List<ModelIdentity> installed) {
        return ProfileValidity.evaluate(profile, new ProfileEnvironment.Snapshot(fingerprint, version, installed));
    }

    private static <T> T unsupported(Class<T> type) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
            (proxy, method, args) -> {
                throw new UnsupportedOperationException(method.getName());
            }));
    }
}
