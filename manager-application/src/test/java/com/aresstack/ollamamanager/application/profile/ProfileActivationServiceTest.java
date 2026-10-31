package com.aresstack.ollamamanager.application.profile;

import com.aresstack.ollamamanager.application.common.CancellationToken;
import com.aresstack.ollamamanager.application.common.ErrorKind;
import com.aresstack.ollamamanager.application.common.LifecycleLock;
import com.aresstack.ollamamanager.application.common.OperationException;
import com.aresstack.ollamamanager.application.configuration.ConfigurationService;
import com.aresstack.ollamamanager.application.port.ManagedServer;
import com.aresstack.ollamamanager.application.port.OllamaRuntimePort;
import com.aresstack.ollamamanager.application.server.ServerLifecycleService;
import com.aresstack.ollamamanager.domain.hardware.GpuFingerprint;
import com.aresstack.ollamamanager.domain.model.ModelIdentity;
import com.aresstack.ollamamanager.domain.profile.ActivationMode;
import com.aresstack.ollamamanager.domain.profile.ProfileActivationPlan;
import com.aresstack.ollamamanager.domain.server.Endpoint;
import com.aresstack.ollamamanager.domain.server.InstallationKind;
import com.aresstack.ollamamanager.domain.server.OllamaInstallation;
import com.aresstack.ollamamanager.domain.server.ServerSettingCatalog;
import com.aresstack.ollamamanager.domain.server.ServerSettings;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ProfileActivationServiceTest {

    private static final CancellationToken NONE = CancellationToken.none();
    private static final OllamaInstallation OWNED = new OllamaInstallation(InstallationKind.MANAGED_PORTABLE,
        "0.12.6", new File("ollama").getAbsoluteFile(), Endpoint.DEFAULT);

    private final InMemoryConfigurationRepository configurations = new InMemoryConfigurationRepository();
    private final ConfigurationService configurationService = new ConfigurationService(configurations);
    private final ModelProfileRepository repository = new ModelProfileRepository(configurationService);
    private final FakeRuntime runtime = new FakeRuntime();
    private final LifecycleLock lock = new LifecycleLock();
    private final ServerLifecycleService lifecycle = new ServerLifecycleService(runtime, 0, lock);
    private final AtomicInteger captures = new AtomicInteger();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private ProfileEnvironment.Snapshot snapshot = new ProfileEnvironment.Snapshot(GpuFingerprint.CPU_ONLY,
        "0.12.6", Collections.singletonList(ProfileTestData.SCOPE.model()));
    private Runnable duringCapture = () -> { };
    private final ProfileActivationService service = new ProfileActivationService(configurationService,
        cancellation -> {
            captures.incrementAndGet();
            duringCapture.run();
            return snapshot;
        }, lifecycle, lock);

    @Before
    public void setUp() throws Exception {
        repository.save(ScenarioPreset.PARALLEL_THROUGHPUT.createProfile("batch", ProfileTestData.SCOPE));
    }

    @After
    public void tearDown() {
        runtime.releaseStart.countDown();
        executor.shutdownNow();
    }

    @Test
    public void ownedServerNeedsConfirmedRestartWhichRunsInsideActivation() throws Exception {
        startOwned(ServerSettings.defaults());
        ProfileActivationPlan plan = service.plan("batch");
        assertEquals(ActivationMode.RESTART_OWNED_SERVER, plan.mode());
        assertTrue(plan.restartRequiredChanges().contains(ServerSettingCatalog.PARALLELISM));
        assertTrue(plan.restartRequiredChanges().contains(ServerSettingCatalog.KV_CACHE));

        assertInvalidState(() -> service.activate("batch", ActivationMode.RESTART_OWNED_SERVER, false, null, NONE));
        assertNull(configurations.stored.activeProfileId());

        service.activate("batch", ActivationMode.RESTART_OWNED_SERVER, true, null, NONE);
        assertEquals("batch", configurations.stored.activeProfileId());
        assertEquals(Integer.valueOf(4), configurations.stored.desiredServerSettings().parallelism());
        assertEquals(2, runtime.starts.size());
        assertEquals(Integer.valueOf(4), lifecycle.appliedSettings().parallelism());
    }

    @Test
    public void externalServerCannotBeChangedAutomatically() throws Exception {
        lifecycle.attachExternal(OllamaInstallation.external("0.12.6", Endpoint.DEFAULT), "0.12.6");
        assertEquals(ActivationMode.CANNOT_APPLY_TO_EXTERNAL, service.plan("batch").mode());
        service.activate("batch", ActivationMode.CANNOT_APPLY_TO_EXTERNAL, false, null, NONE);
        assertTrue(runtime.starts.isEmpty());
    }

    @Test
    public void matchingSettingsNeedOnlyRequestOptions() throws Exception {
        startOwned(ScenarioPreset.PARALLEL_THROUGHPUT.serverSettings());
        ProfileActivationPlan plan = service.activate("batch", ActivationMode.REQUEST_OPTIONS_ONLY, false, null,
            NONE);
        assertTrue(plan.restartRequiredChanges().isEmpty());
        assertEquals(Integer.valueOf(4096), plan.requestOptions().numCtx());
        assertEquals(1, runtime.starts.size());
    }

    @Test
    public void stoppedServerAppliesOnNextStart() throws Exception {
        assertEquals(ActivationMode.APPLY_ON_NEXT_START,
            service.activate("batch", ActivationMode.APPLY_ON_NEXT_START, false, null, NONE).mode());
    }

    @Test
    public void failedOwnedServerCountsAsNotRunning() throws Exception {
        startOwned(ServerSettings.defaults());
        runtime.last.exitCode = 1;

        assertEquals(ActivationMode.APPLY_ON_NEXT_START, service.plan("batch").mode());
        service.activate("batch", ActivationMode.APPLY_ON_NEXT_START, false, null, NONE);
        assertEquals(1, runtime.starts.size());
    }

    @Test
    public void startingServerIsRejected() throws Exception {
        runtime.blockStart = true;
        Future<?> start = executor.submit(() -> lifecycle.start(OWNED, ServerSettings.defaults(), NONE));
        assertTrue(runtime.startEntered.await(5, TimeUnit.SECONDS));

        assertInvalidState(() -> service.plan("batch"));
        assertInvalidState(() -> service.activate("batch", ActivationMode.APPLY_ON_NEXT_START, false, null, NONE));
        assertNull(configurations.stored.activeProfileId());

        runtime.releaseStart.countDown();
        start.get(5, TimeUnit.SECONDS);
    }

    @Test
    public void changedServerStateSinceConfirmationIsRejected() throws Exception {
        assertEquals(ActivationMode.APPLY_ON_NEXT_START, service.plan("batch").mode());
        startOwned(ServerSettings.defaults());

        assertInvalidState(() -> service.activate("batch", ActivationMode.APPLY_ON_NEXT_START, false, null, NONE));
        assertNull(configurations.stored.activeProfileId());
    }

    @Test
    public void serverCannotBeSwappedBetweenCaptureAndCommit() throws Exception {
        lifecycle.attachExternal(OllamaInstallation.external("0.12.6", Endpoint.DEFAULT), "0.12.6");
        Throwable[] reattach = new Throwable[1];
        duringCapture = () -> {
            try {
                executor.submit(() -> {
                    lifecycle.attachExternal(OllamaInstallation.external("0.13.0", Endpoint.DEFAULT), "0.13.0");
                    return null;
                }).get(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                reattach[0] = e.getCause();
            }
        };

        service.activate("batch", ActivationMode.CANNOT_APPLY_TO_EXTERNAL, false, null, NONE);

        assertTrue("a re-attach during activation must fail fast", reattach[0] instanceof OperationException);
        assertEquals(ErrorKind.INVALID_STATE, ((OperationException) reattach[0]).kind());
        assertEquals("0.12.6", lifecycle.status().reportedVersion());
        assertEquals("batch", configurations.stored.activeProfileId());
    }

    @Test
    public void unknownProfileIsNotFound() {
        try {
            service.plan("missing");
            fail();
        } catch (OperationException e) {
            assertEquals(ErrorKind.NOT_FOUND, e.kind());
        }
    }

    @Test
    public void activationKeepsProfileRevisionSavedMeanwhile() throws Exception {
        repository.save(repository.find("batch").revise(ServerSettings.defaults(),
            ScenarioPreset.PARALLEL_THROUGHPUT.modelOptions()));
        service.activate("batch", ActivationMode.APPLY_ON_NEXT_START, false, null, NONE);
        assertEquals(2, repository.find("batch").revision());
        assertEquals("batch", configurations.stored.activeProfileId());
    }

    @Test
    public void validitiesEvaluateAllProfilesFromOneCapture() throws Exception {
        repository.save(ScenarioPreset.PARALLEL_THROUGHPUT.createProfile("second", ProfileTestData.SCOPE));
        repository.save(ScenarioPreset.PARALLEL_THROUGHPUT.createProfile("third", ProfileTestData.SCOPE));

        Map<String, ProfileValidity> validities = service.validities(NONE);

        assertEquals(1, captures.get());
        assertEquals(3, validities.size());
        assertEquals(ProfileValidity.Result.VALID, validities.get("third").result());
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

        service.activate("batch", ActivationMode.APPLY_ON_NEXT_START, false,
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
    public void approvalForAnotherInstalledArtifactIsRejected() throws Exception {
        snapshot = new ProfileEnvironment.Snapshot(GpuFingerprint.CPU_ONLY, "0.12.6",
            Collections.singletonList(new ModelIdentity("llama3.1:8b", "bbb", "Q4_K_M")));
        ProfileValidity seen = service.validity("batch", NONE);
        assertEquals(ProfileValidity.Result.OUTDATED, seen.result());
        snapshot = new ProfileEnvironment.Snapshot(GpuFingerprint.CPU_ONLY, "0.12.6",
            Collections.singletonList(new ModelIdentity("llama3.1:8b", "ccc", "Q4_K_M")));

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
        startOwned(ServerSettings.defaults());
        snapshot = new ProfileEnvironment.Snapshot(GpuFingerprint.CPU_ONLY, null, null);
        ProfileValidity seen = service.validity("batch", NONE);
        ValidityApproval approval = new ValidityApproval("batch", 1, seen.result(), seen.findings());
        try {
            service.activate("batch", ActivationMode.RESTART_OWNED_SERVER, false, approval, NONE);
            fail("a needed restart still needs its own confirmation");
        } catch (OperationException e) {
            assertTrue(e.getMessage().contains("restart"));
        }
        service.activate("batch", ActivationMode.RESTART_OWNED_SERVER, true, approval, NONE);
        assertEquals("batch", configurations.stored.activeProfileId());
    }

    @Test
    public void validProfileNeedsNoApproval() throws Exception {
        ProfileValidity validity = service.validity("batch", NONE);
        assertEquals(ProfileValidity.Result.VALID, validity.result());
        assertTrue(validity.findings().isEmpty());
    }

    private void startOwned(ServerSettings settings) throws OperationException {
        lifecycle.start(OWNED, settings, NONE);
    }

    private void assertRejected(ValidityApproval approval) {
        assertInvalidState(() -> service.activate("batch", ActivationMode.APPLY_ON_NEXT_START, false, approval,
            NONE));
    }

    private interface Call {
        Object run() throws OperationException;
    }

    private static void assertInvalidState(Call call) {
        try {
            call.run();
            fail("expected INVALID_STATE");
        } catch (OperationException e) {
            assertEquals(ErrorKind.INVALID_STATE, e.kind());
        }
    }

    private static final class FakeRuntime implements OllamaRuntimePort {
        final List<ServerSettings> starts = new ArrayList<ServerSettings>();
        final CountDownLatch startEntered = new CountDownLatch(1);
        final CountDownLatch releaseStart = new CountDownLatch(1);
        volatile boolean blockStart;
        volatile FakeServer last;

        @Override
        public List<OllamaInstallation> discover(List<File> additionalDirectories, CancellationToken cancellation) {
            return Collections.emptyList();
        }

        @Override
        public ManagedServer start(OllamaInstallation installation, ServerSettings settings,
                                   CancellationToken cancellation) {
            startEntered.countDown();
            if (blockStart) {
                try {
                    releaseStart.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            starts.add(settings);
            last = new FakeServer(installation, settings);
            return last;
        }

        @Override
        public void stop(ManagedServer server, long gracePeriodMillis) {
            ((FakeServer) server).exitCode = 0;
        }

        @Override
        public List<String> recentLog(ManagedServer server) {
            return Collections.emptyList();
        }
    }

    private static final class FakeServer implements ManagedServer {
        private final OllamaInstallation installation;
        private final ServerSettings settings;
        volatile Integer exitCode;

        FakeServer(OllamaInstallation installation, ServerSettings settings) {
            this.installation = installation;
            this.settings = settings;
        }

        @Override
        public OllamaInstallation installation() {
            return installation;
        }

        @Override
        public ServerSettings appliedSettings() {
            return settings;
        }

        @Override
        public boolean isAlive() {
            return exitCode == null;
        }

        @Override
        public Integer exitCode() {
            return exitCode;
        }
    }
}
