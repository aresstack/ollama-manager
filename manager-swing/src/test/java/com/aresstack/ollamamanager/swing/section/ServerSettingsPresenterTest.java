package com.aresstack.ollamamanager.swing.section;

import com.aresstack.ollamamanager.application.configuration.ConfigurationService;
import com.aresstack.ollamamanager.domain.server.ServerSetting;
import com.aresstack.ollamamanager.domain.server.ServerSettingCatalog;
import com.aresstack.ollamamanager.swing.job.JobManager;
import com.aresstack.ollamamanager.swing.job.UiDispatcher;
import com.aresstack.ollamamanager.swing.shell.ConsoleServices;
import org.junit.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ServerSettingsPresenterTest {

    private final JobManager jobs = new JobManager(Runnable::run, UiDispatcher.inline());
    private final RecordingView view = new RecordingView();

    @Test
    public void emptyTextMeansOllamaDefault() {
        Map<ServerSetting<?>, String> texts = new LinkedHashMap<ServerSetting<?>, String>();
        texts.put(ServerSettingCatalog.PARALLELISM, "  ");
        SettingsDraft draft = SettingsDraft.parse(texts);

        assertTrue(draft.isValid());
        assertNull(draft.settings().parallelism());
    }

    @Test
    public void everyInvalidFieldIsReportedAndNothingIsSaved() throws Exception {
        ConsoleServices services = TestServices.builder().build();
        ServerSettingsPresenter presenter = presenter(services);
        view.edits.put(ServerSettingCatalog.PARALLELISM, "0");
        view.edits.put(ServerSettingCatalog.KEEP_ALIVE, "forever");

        presenter.save();

        assertEquals(2, view.errors.size());
        assertNull(services.configuration().current().desiredServerSettings().parallelism());
    }

    @Test
    public void validEditIsSavedThroughTheConfigurationService() throws Exception {
        ConsoleServices services = TestServices.builder().build();
        ServerSettingsPresenter presenter = presenter(services);
        view.edits.put(ServerSettingCatalog.PARALLELISM, "4");

        presenter.save();

        assertEquals(Integer.valueOf(4), services.configuration().current().desiredServerSettings().parallelism());
        assertEquals(ServerSettingsPresenter.SAVED, view.notices.get(view.notices.size() - 1));
        assertEquals("4", view.values.get(ServerSettingCatalog.PARALLELISM));
    }

    @Test
    public void saveIsRejectedWhenSettingsChangedMeanwhile() throws Exception {
        ConsoleServices services = TestServices.builder().build();
        ServerSettingsPresenter presenter = presenter(services);
        ConfigurationService configuration = services.configuration();
        configuration.update(current -> current.withDesiredServerSettings(
            current.desiredServerSettings().with(ServerSettingCatalog.QUEUE, 7)));
        view.edits.put(ServerSettingCatalog.PARALLELISM, "4");

        presenter.save();

        assertEquals(1, view.failures);
        assertNull(configuration.current().desiredServerSettings().parallelism());
        assertEquals(Integer.valueOf(7), configuration.current().desiredServerSettings().queue());
    }

    @Test
    public void restartNoticeWhenRunningServerUsesOtherSettings() throws Exception {
        ConsoleServices services = TestServices.builder().serverLifecycle(TestServices.runningLifecycle()).build();
        ServerSettingsPresenter presenter = presenter(services);
        view.edits.put(ServerSettingCatalog.PARALLELISM, "4");

        presenter.save();

        assertEquals(ServerSettingsPresenter.SAVED_RESTART, view.notices.get(view.notices.size() - 1));
        assertEquals("4", view.values.get(ServerSettingCatalog.PARALLELISM));
        assertEquals("", view.applied.get(ServerSettingCatalog.PARALLELISM));
        assertTrue(view.pendingRestart.contains(ServerSettingCatalog.PARALLELISM));
    }

    @Test
    public void storedValuesAreNotShownAsRunningWithoutOwnedServer() throws Exception {
        presenter(TestServices.builder().serverLifecycle(TestServices.externalLifecycle()).build());

        assertNull(view.applied);
        assertTrue(view.pendingRestart.isEmpty());
        assertEquals(ServerSettingsPresenter.EXTERNAL_UNKNOWN, view.appliedSource);
    }

    private ServerSettingsPresenter presenter(ConsoleServices services) {
        ServerSettingsPresenter presenter = new ServerSettingsPresenter(services, jobs);
        presenter.bind(view);
        presenter.load();
        return presenter;
    }

    private static final class RecordingView implements ServerSettingsView {
        final Map<ServerSetting<?>, String> values = new LinkedHashMap<ServerSetting<?>, String>();
        final Map<ServerSetting<?>, String> edits = new LinkedHashMap<ServerSetting<?>, String>();
        Map<ServerSetting<?>, String> errors = new LinkedHashMap<ServerSetting<?>, String>();
        final List<String> notices = new ArrayList<String>();
        int failures;
        Map<ServerSetting<?>, String> applied;
        Set<ServerSetting<?>> pendingRestart;
        String appliedSource;

        @Override
        public void showValues(Map<ServerSetting<?>, String> texts) {
            values.clear();
            values.putAll(texts);
            edits.clear();
            edits.putAll(texts);
        }

        @Override
        public void showApplied(Map<ServerSetting<?>, String> value, Set<ServerSetting<?>> pending,
                                String source) {
            applied = value;
            pendingRestart = pending;
            appliedSource = source;
        }

        @Override
        public Map<ServerSetting<?>, String> editedValues() {
            return new LinkedHashMap<ServerSetting<?>, String>(edits);
        }

        @Override
        public void showErrors(Map<ServerSetting<?>, String> value) {
            errors = value;
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
            failures++;
        }
    }
}
