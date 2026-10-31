package com.aresstack.ollamamanager.swing.section;

import com.aresstack.ollamamanager.application.common.CancellationToken;
import com.aresstack.ollamamanager.application.common.ProgressListener;
import com.aresstack.ollamamanager.application.common.TransferProgress;
import com.aresstack.ollamamanager.application.model.ModelEntry;
import com.aresstack.ollamamanager.application.model.ModelService;
import com.aresstack.ollamamanager.application.port.OllamaApiPort;
import com.aresstack.ollamamanager.application.server.ServerLifecycleService;
import com.aresstack.ollamamanager.domain.model.InstalledModel;
import com.aresstack.ollamamanager.domain.model.LoadedModel;
import com.aresstack.ollamamanager.domain.model.ModelDetails;
import com.aresstack.ollamamanager.domain.model.ModelOptions;
import com.aresstack.ollamamanager.domain.server.Endpoint;
import com.aresstack.ollamamanager.swing.job.Job;
import com.aresstack.ollamamanager.swing.job.JobManager;
import com.aresstack.ollamamanager.swing.job.UiDispatcher;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ModelsPresenterTest {

    private final RecordingApi api = new RecordingApi();
    private final RecordingView view = new RecordingView();

    @Test
    public void withoutRunningServerNothingIsSent() {
        ModelsPresenter presenter = new ModelsPresenter(TestServices.builder()
            .serverLifecycle(TestServices.stoppedLifecycle()).models(new ModelService(api)).build(),
            new JobManager(Runnable::run, UiDispatcher.inline()));
        presenter.bind(view);

        presenter.refresh();
        presenter.pull("llama3.2:3b");

        assertTrue(api.calls.isEmpty());
        assertEquals("unavailable:No running Ollama server. Start or attach a server first.",
            view.events.get(0));
    }

    @Test
    public void operationsTargetOnlyTheRunningServer() throws Exception {
        ModelsPresenter presenter = presenter(new JobManager(Runnable::run, UiDispatcher.inline()));

        presenter.refresh();

        assertEquals("[installed@127.0.0.1:11499, loaded@127.0.0.1:11499]", api.calls.toString());
        assertEquals("models:0", view.events.get(view.events.size() - 1));
    }

    @Test
    public void secondOperationIsIgnoredWhileOneRuns() throws Exception {
        List<Runnable> pending = new ArrayList<Runnable>();
        ModelsPresenter presenter = presenter(new JobManager(pending::add, UiDispatcher.inline()));

        presenter.pull("a");
        presenter.pull("b");
        presenter.unload("c");

        assertEquals(1, pending.size());
        assertEquals("[actions:false]", view.events.toString());
        pending.get(0).run();
        assertEquals("pull:a", api.calls.get(0));
        assertTrue(view.events.contains("actions:true"));
        assertFalse(presenter.isBusy());
    }

    @Test
    public void olderInventoryResponseDoesNotOverwriteNewerOne() throws Exception {
        List<Runnable> pending = new ArrayList<Runnable>();
        ModelsPresenter presenter = presenter(new JobManager(pending::add, UiDispatcher.inline()));

        presenter.refresh();
        presenter.refresh();
        pending.get(1).run();
        pending.get(0).run();

        assertEquals(1, Collections.frequency(view.events, "models:0"));
    }

    @Test
    public void pendingInventoryDoesNotOverwriteUnavailableAfterStop() throws Exception {
        List<Runnable> pending = new ArrayList<Runnable>();
        ServerLifecycleService lifecycle = TestServices.runningLifecycle();
        ModelsPresenter presenter = new ModelsPresenter(TestServices.builder().serverLifecycle(lifecycle)
            .models(new ModelService(api)).build(), new JobManager(pending::add, UiDispatcher.inline()));
        presenter.bind(view);

        presenter.refresh();
        lifecycle.stop();
        presenter.refresh();
        pending.get(0).run();

        assertFalse(view.events.contains("models:0"));
        assertEquals("unavailable:No running Ollama server. Start or attach a server first.",
            view.events.get(view.events.size() - 1));
    }

    @Test
    public void externalServerIsReadOnly() throws Exception {
        ModelsPresenter presenter = new ModelsPresenter(TestServices.builder()
            .serverLifecycle(TestServices.externalLifecycle()).models(new ModelService(api)).build(),
            new JobManager(Runnable::run, UiDispatcher.inline()));
        presenter.bind(view);
        view.confirmation = "llama3.2:3b";

        presenter.refresh();
        presenter.pull("llama3.2:3b");
        presenter.load("llama3.2:3b");
        presenter.unload("llama3.2:3b");
        presenter.delete("llama3.2:3b");

        assertEquals("[installed@127.0.0.1:11499, loaded@127.0.0.1:11499]", api.calls.toString());
        assertTrue(view.events.contains("mutations:false"));
        assertFalse(view.events.contains("mutations:true"));
    }

    @Test
    public void ownedServerAllowsMutations() throws Exception {
        ModelsPresenter presenter = presenter(new JobManager(Runnable::run, UiDispatcher.inline()));

        presenter.refresh();

        assertTrue(view.events.contains("mutations:true"));
    }

    @Test
    public void cancelledDeleteDialogSendsNothing() throws Exception {
        ModelsPresenter presenter = presenter(new JobManager(Runnable::run, UiDispatcher.inline()));
        view.confirmation = null;

        presenter.delete("llama3.2:3b");

        assertTrue(api.calls.isEmpty());
    }

    @Test
    public void wrongConfirmationIsRejectedByTheService() throws Exception {
        ModelsPresenter presenter = presenter(new JobManager(Runnable::run, UiDispatcher.inline()));
        view.confirmation = "other";

        presenter.delete("llama3.2:3b");

        assertFalse(api.calls.contains("delete:llama3.2:3b"));
        assertTrue(view.events.contains("failure:Delete llama3.2:3b"));
    }

    private ModelsPresenter presenter(JobManager jobs) throws Exception {
        ModelsPresenter presenter = new ModelsPresenter(TestServices.builder()
            .serverLifecycle(TestServices.runningLifecycle()).models(new ModelService(api)).build(), jobs);
        presenter.bind(view);
        return presenter;
    }

    private static final class RecordingApi implements OllamaApiPort {
        final List<String> calls = new ArrayList<String>();

        @Override
        public String version(Endpoint endpoint, CancellationToken cancellation) {
            return "0.1.0";
        }

        @Override
        public List<InstalledModel> installedModels(Endpoint endpoint, CancellationToken cancellation) {
            calls.add("installed@" + endpoint);
            return Collections.emptyList();
        }

        @Override
        public List<LoadedModel> loadedModels(Endpoint endpoint, CancellationToken cancellation) {
            calls.add("loaded@" + endpoint);
            return Collections.emptyList();
        }

        @Override
        public ModelDetails show(Endpoint endpoint, String modelName, CancellationToken cancellation) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void pull(Endpoint endpoint, String modelName, ProgressListener<TransferProgress> listener,
                         CancellationToken cancellation) {
            calls.add("pull:" + modelName);
            listener.onProgress(new TransferProgress("downloading", 5, 10));
        }

        @Override
        public void preload(Endpoint endpoint, String modelName, ModelOptions options, String keepAlive,
                            CancellationToken cancellation) {
            calls.add("preload:" + modelName);
        }

        @Override
        public void unload(Endpoint endpoint, String modelName, CancellationToken cancellation) {
            calls.add("unload:" + modelName);
        }

        @Override
        public void delete(Endpoint endpoint, String modelName, CancellationToken cancellation) {
            calls.add("delete:" + modelName);
        }
    }

    private static final class RecordingView implements ModelsView {
        final List<String> events = new ArrayList<String>();
        String confirmation;

        @Override
        public void showModels(List<ModelEntry> entries) {
            events.add("models:" + entries.size());
        }

        @Override
        public void showUnavailable(String reason) {
            events.add("unavailable:" + reason);
        }

        @Override
        public void setActionsEnabled(boolean enabled) {
            events.add("actions:" + enabled);
        }

        @Override
        public void setMutationsAllowed(boolean allowed, String reason) {
            events.add("mutations:" + allowed);
        }

        @Override
        public void showFailure(String action, Exception error) {
            events.add("failure:" + action);
        }

        @Override
        public void trackJob(Job job) {
        }

        @Override
        public String confirmDelete(String modelName) {
            return confirmation;
        }
    }
}
