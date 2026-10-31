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
import com.aresstack.ollamamanager.domain.hardware.GpuDescriptor;
import com.aresstack.ollamamanager.domain.hardware.GpuFingerprint;
import com.aresstack.ollamamanager.domain.hardware.HardwareSnapshot;
import com.aresstack.ollamamanager.domain.model.ModelIdentity;
import com.aresstack.ollamamanager.domain.profile.ModelProfile;
import com.aresstack.ollamamanager.domain.server.Endpoint;
import com.aresstack.ollamamanager.domain.server.OllamaInstallation;
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
            new ModelService(unsupported(OllamaApiPort.class)), unsupported(OllamaApiPort.class));

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

    @Test
    public void liveEnvironmentQueriesCurrentVersionOfAttachedServer() throws Exception {
        ServerLifecycleService lifecycle = new ServerLifecycleService(unsupported(OllamaRuntimePort.class), 0,
            new LifecycleLock());
        lifecycle.attachExternal(OllamaInstallation.external("0.12.6", Endpoint.DEFAULT), "0.12.6");
        OllamaApiPort upgraded = answering(OllamaApiPort.class, method -> {
            switch (method) {
                case "version":
                    return "0.13.0";
                default:
                    return Collections.emptyList();
            }
        });
        ProfileEnvironment environment = ProfileEnvironment.live(cancellation -> cpuOnly(true), lifecycle,
            new ModelService(upgraded), upgraded);

        ProfileEnvironment.Snapshot snapshot = environment.capture(CancellationToken.none());

        assertEquals("0.13.0", snapshot.ollamaVersion());
        assertEquals(GpuFingerprint.CPU_ONLY, snapshot.gpuFingerprint());
    }

    @Test
    public void incompleteGpuDiscoveryIsNotVerifiedCpuOnly() throws Exception {
        ServerLifecycleService lifecycle = new ServerLifecycleService(unsupported(OllamaRuntimePort.class), 0,
            new LifecycleLock());
        ProfileEnvironment environment = ProfileEnvironment.live(cancellation -> cpuOnly(false), lifecycle,
            new ModelService(unsupported(OllamaApiPort.class)), unsupported(OllamaApiPort.class));

        assertNull(environment.capture(CancellationToken.none()).gpuFingerprint());
    }

    private static HardwareSnapshot cpuOnly(boolean complete) {
        return new HardwareSnapshot("Windows 11", "amd64", null, 8, null,
            Collections.<GpuDescriptor>emptyList(), complete);
    }

    private interface MethodAnswer {
        Object answer(String method);
    }

    private static <T> T answering(Class<T> type, MethodAnswer answer) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
            (proxy, method, args) -> answer.answer(method.getName())));
    }
}
