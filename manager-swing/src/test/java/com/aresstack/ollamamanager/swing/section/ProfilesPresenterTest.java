package com.aresstack.ollamamanager.swing.section;

import com.aresstack.ollamamanager.application.profile.ProfileActivationService;
import com.aresstack.ollamamanager.application.server.ServerLifecycleService;
import com.aresstack.ollamamanager.domain.hardware.GpuFingerprint;
import com.aresstack.ollamamanager.domain.model.ModelIdentity;
import com.aresstack.ollamamanager.domain.model.ModelOptions;
import com.aresstack.ollamamanager.domain.profile.ModelProfile;
import com.aresstack.ollamamanager.domain.profile.ProfileActivationPlan;
import com.aresstack.ollamamanager.domain.profile.ProfileScope;
import com.aresstack.ollamamanager.domain.server.RuntimeOwnership;
import com.aresstack.ollamamanager.domain.server.ServerSettingCatalog;
import com.aresstack.ollamamanager.domain.server.ServerSettings;
import com.aresstack.ollamamanager.domain.server.ServerState;
import com.aresstack.ollamamanager.swing.job.Job;
import com.aresstack.ollamamanager.swing.job.JobManager;
import com.aresstack.ollamamanager.swing.job.UiDispatcher;
import com.aresstack.ollamamanager.swing.shell.ConsoleServices;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ProfilesPresenterTest {

    private static final ModelIdentity MODEL = new ModelIdentity("llama3.2:3b", "sha256:aaa", "Q4_K_M");
    private static final GpuFingerprint GPU = new GpuFingerprint("nvidia:rtx5080:16gb");

    private final JobManager jobs = new JobManager(Runnable::run, UiDispatcher.inline());
    private final RecordingView view = new RecordingView();

    @Test
    public void checkIsValidOnlyWhenModelGpuAndVersionMatch() {
        ModelProfile profile = profile(ServerSettings.defaults());

        assertEquals(ProfileCheck.Result.VALID,
            ProfileCheck.of(profile, GPU, "0.1.0", Collections.singletonList(MODEL)).result());
        assertEquals(ProfileCheck.Result.OUTDATED, ProfileCheck.of(profile, GPU, "0.1.0",
            Collections.singletonList(new ModelIdentity("llama3.2:3b", "sha256:bbb", "Q4_K_M"))).result());
        assertEquals(ProfileCheck.Result.OUTDATED, ProfileCheck.of(profile, new GpuFingerprint("cpu-only"),
            "0.1.0", Collections.singletonList(MODEL)).result());
        assertEquals(ProfileCheck.Result.OUTDATED,
            ProfileCheck.of(profile, GPU, "0.2.0", Collections.singletonList(MODEL)).result());
        assertEquals(ProfileCheck.Result.UNVERIFIED, ProfileCheck.of(profile, GPU, null, null).result());
        assertEquals(ProfileCheck.Result.UNVERIFIED, ProfileCheck.of(profile, GPU, "0.1.0",
            Collections.singletonList(ModelIdentity.named("llama3.2:3b"))).result());
    }

    @Test
    public void unverifiedProfileIsNotActivatedWithoutConfirmation() throws Exception {
        ConsoleServices services = services(null, ServerSettings.defaults());
        ProfilesPresenter presenter = presenter(services);
        view.acceptUnverified = false;

        presenter.activate("p1");

        assertEquals(1, view.unverifiedQuestions);
        assertNull(services.configuration().current().activeProfileId());
    }

    @Test
    public void confirmedProfileWithoutServerAppliesOnNextStart() throws Exception {
        ConsoleServices services = services(TestServices.stoppedLifecycle(),
            ServerSettings.defaults().with(ServerSettingCatalog.PARALLELISM, 4));
        ProfilesPresenter presenter = presenter(services);

        presenter.activate("p1");

        assertEquals("p1", services.configuration().current().activeProfileId());
        assertEquals(Integer.valueOf(4), services.configuration().current().desiredServerSettings().parallelism());
        assertEquals(0, view.restartQuestions);
        assertTrue(view.notices.contains(ProfilesPresenter.noticeFor(
            com.aresstack.ollamamanager.domain.profile.ActivationMode.APPLY_ON_NEXT_START)));
    }

    @Test
    public void declinedRestartStoresNothingAndKeepsServerRunning() throws Exception {
        ServerLifecycleService lifecycle = TestServices.runningLifecycle();
        ConsoleServices services = services(lifecycle,
            ServerSettings.defaults().with(ServerSettingCatalog.PARALLELISM, 4));
        ProfilesPresenter presenter = presenter(services);
        view.acceptRestart = false;

        presenter.activate("p1");

        assertEquals(1, view.restartQuestions);
        assertNull(services.configuration().current().activeProfileId());
        assertNull(lifecycle.appliedSettings().parallelism());
        assertFalse(presenter.isBusy());
    }

    @Test
    public void confirmedRestartActivatesAndRestartsOwnedServer() throws Exception {
        ServerLifecycleService lifecycle = TestServices.runningLifecycle();
        ConsoleServices services = services(lifecycle,
            ServerSettings.defaults().with(ServerSettingCatalog.PARALLELISM, 4));
        ProfilesPresenter presenter = presenter(services);

        presenter.activate("p1");

        assertEquals("p1", services.configuration().current().activeProfileId());
        assertEquals(ServerState.RUNNING, lifecycle.status().state());
        assertEquals(Integer.valueOf(4), lifecycle.appliedSettings().parallelism());
    }

    @Test
    public void externalServerIsNeverRestarted() throws Exception {
        ServerLifecycleService lifecycle = TestServices.externalLifecycle();
        ConsoleServices services = services(lifecycle,
            ServerSettings.defaults().with(ServerSettingCatalog.PARALLELISM, 4));
        ProfilesPresenter presenter = presenter(services);

        presenter.activate("p1");

        assertEquals(0, view.restartQuestions);
        assertEquals(RuntimeOwnership.EXTERNAL, lifecycle.status().ownership());
        assertEquals("p1", services.configuration().current().activeProfileId());
    }

    private ProfilesPresenter presenter(ConsoleServices services) {
        ProfilesPresenter presenter = new ProfilesPresenter(services, jobs);
        presenter.bind(view);
        presenter.refresh();
        return presenter;
    }

    private static ConsoleServices services(ServerLifecycleService lifecycle, ServerSettings profileSettings)
        throws Exception {
        ConsoleServices base = TestServices.builder().build();
        base.configuration().update(current -> current.withProfile(profile(profileSettings)));
        return ConsoleServices.builder(base.configuration()).serverLifecycle(lifecycle)
            .profiles(new ProfileActivationService(base.configuration())).build();
    }

    private static ModelProfile profile(ServerSettings settings) {
        return new ModelProfile("p1", "Chat", 1, new ProfileScope(MODEL, GPU, "0.1.0", "chat"), settings,
            new ModelOptions(8192, null, null, null));
    }

    private static final class RecordingView implements ProfilesView {
        final List<String> notices = new ArrayList<String>();
        List<ProfileRow> rows = Arrays.asList();
        boolean acceptUnverified = true;
        boolean acceptRestart = true;
        int unverifiedQuestions;
        int restartQuestions;

        @Override
        public void showProfiles(List<ProfileRow> value) {
            rows = value;
        }

        @Override
        public void setBusy(boolean busy) {
        }

        @Override
        public void showNotice(String notice) {
            notices.add(notice);
        }

        @Override
        public void showFailure(String action, Exception error) {
            notices.add("failure:" + action + ":" + error.getMessage());
        }

        @Override
        public void trackJob(Job job) {
        }

        @Override
        public boolean confirmUnverifiedActivation(ProfileRow row) {
            unverifiedQuestions++;
            return acceptUnverified;
        }

        @Override
        public boolean confirmRestart(ProfileRow row, ProfileActivationPlan plan) {
            restartQuestions++;
            return acceptRestart;
        }
    }
}
