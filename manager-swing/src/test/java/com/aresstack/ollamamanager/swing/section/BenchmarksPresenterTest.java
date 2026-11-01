package com.aresstack.ollamamanager.swing.section;

import com.aresstack.ollamamanager.application.benchmark.BenchmarkReport;
import com.aresstack.ollamamanager.application.benchmark.ConcurrencyBenchmarkRequest;
import com.aresstack.ollamamanager.application.benchmark.ConcurrencyBenchmarkService;
import com.aresstack.ollamamanager.application.benchmark.StageResult;
import com.aresstack.ollamamanager.application.common.CancellationToken;
import com.aresstack.ollamamanager.application.common.ErrorKind;
import com.aresstack.ollamamanager.application.common.OperationException;
import com.aresstack.ollamamanager.application.common.ProgressListener;
import com.aresstack.ollamamanager.application.common.TransferProgress;
import com.aresstack.ollamamanager.application.configuration.ExistingSettingsImport;
import com.aresstack.ollamamanager.application.model.ModelService;
import com.aresstack.ollamamanager.application.port.InstalledEnvironmentPort;
import com.aresstack.ollamamanager.application.port.OllamaApiPort;
import com.aresstack.ollamamanager.domain.benchmark.GenerationMetrics;
import com.aresstack.ollamamanager.domain.model.InstalledModel;
import com.aresstack.ollamamanager.domain.model.LoadedModel;
import com.aresstack.ollamamanager.domain.model.ModelDetails;
import com.aresstack.ollamamanager.domain.model.ModelIdentity;
import com.aresstack.ollamamanager.domain.model.ModelOptions;
import com.aresstack.ollamamanager.domain.profile.ModelProfile;
import com.aresstack.ollamamanager.domain.server.Endpoint;
import com.aresstack.ollamamanager.swing.job.Job;
import com.aresstack.ollamamanager.swing.job.JobManager;
import com.aresstack.ollamamanager.swing.job.UiDispatcher;
import com.aresstack.ollamamanager.swing.shell.ConsoleServices;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class BenchmarksPresenterTest {

    private final JobManager jobs = new JobManager(Runnable::run, UiDispatcher.inline());
    private final RecordingView view = new RecordingView();
    private final Map<String, String> environment = new LinkedHashMap<String, String>();

    private ConsoleServices externalServices() throws OperationException {
        ConsoleServices.Builder builder = TestServices.builder();
        FakeApi api = new FakeApi();
        ConsoleServices probe = builder.build();
        return builder.serverLifecycle(TestServices.externalLifecycle())
            .models(new ModelService(api))
            .benchmarks(new ConcurrencyBenchmarkService(api, null, null), null)
            .existingSettings(new ExistingSettingsImport(probe.configuration(), new InstalledEnvironmentPort() {
                @Override
                public Map<String, String> ollamaVariables(CancellationToken cancellation) {
                    return environment;
                }

                @Override
                public String sourceDescription() {
                    return "test environment";
                }
            }))
            .build();
    }

    @Test
    public void keepsTheExternalServersValueAfterARun() throws Exception {
        environment.put("OLLAMA_NUM_PARALLEL", "8");
        BenchmarksPresenter presenter = new BenchmarksPresenter(externalServices(), jobs);
        presenter.bind(view);

        presenter.refresh();
        assertEquals("8", view.serverParallel.get(view.serverParallel.size() - 1));

        assertTrue(presenter.run(new ConcurrencyBenchmarkRequest("m", Arrays.asList(1), null, 16, 1, null, true)));

        assertNotNull(view.report);
        assertEquals("8", view.report.serverNumParallel());
        // The value read from the environment is still shown once the run is over.
        assertEquals("8", view.serverParallel.get(view.serverParallel.size() - 1));
        assertEquals("8", presenter.serverParallelText());
    }

    @Test
    public void reportsAnUnsetValueAsTheDefault() throws Exception {
        BenchmarksPresenter presenter = new BenchmarksPresenter(externalServices(), jobs);
        presenter.bind(view);

        presenter.refresh();

        assertEquals(BenchmarksPresenter.SERVER_DEFAULT, view.serverParallel.get(view.serverParallel.size() - 1));
    }

    /** Records what the presenter shows. */
    private static final class RecordingView implements BenchmarksView {
        final List<String> serverParallel = new ArrayList<String>();
        BenchmarkReport report;

        @Override
        public void showModels(List<String> models) {
        }

        @Override
        public void showServerParallel(String text) {
            serverParallel.add(text);
        }

        @Override
        public void setSweepAvailable(boolean available, String reason) {
        }

        @Override
        public void setServerRestartAvailable(boolean available, boolean external) {
        }

        @Override
        public void showServerRestarted(int numParallel) {
        }

        @Override
        public void showUnavailable(String reason) {
            throw new AssertionError("unexpected: " + reason);
        }

        @Override
        public void setRunning(boolean running) {
        }

        @Override
        public void runEnded(boolean failed) {
        }

        @Override
        public void showWorkload(boolean embedding) {
        }

        @Override
        public void stageFinished(StageResult stage) {
        }

        @Override
        public void reportFinished(BenchmarkReport finished) {
        }

        @Override
        public void showReport(BenchmarkReport shown) {
            report = shown;
        }

        @Override
        public void showReports(List<BenchmarkReport> reports) {
        }

        @Override
        public void showHistory(List<BenchmarkReport> reports) {
        }

        @Override
        public void showProfileSaved(ModelProfile profile) {
        }

        @Override
        public void showFailure(String action, Exception error) {
            throw new AssertionError(action, error);
        }

        @Override
        public void trackJob(Job job) {
        }
    }

    /** One installed chat model "m" that answers every generate request at once. */
    private static final class FakeApi implements OllamaApiPort {
        @Override
        public String version(Endpoint endpoint, CancellationToken cancellation) {
            return "0.0";
        }

        @Override
        public List<InstalledModel> installedModels(Endpoint endpoint, CancellationToken cancellation) {
            return Collections.singletonList(new InstalledModel(new ModelIdentity("m", "sha256:abc", "Q4"), 1000,
                null, null, null));
        }

        @Override
        public List<LoadedModel> loadedModels(Endpoint endpoint, CancellationToken cancellation) {
            return Collections.singletonList(new LoadedModel(ModelIdentity.named("m"), 1000, 1000, 4096, null));
        }

        @Override
        public ModelDetails show(Endpoint endpoint, String modelName, CancellationToken cancellation)
            throws OperationException {
            throw new OperationException(ErrorKind.NOT_FOUND, "no details");
        }

        @Override
        public void pull(Endpoint endpoint, String modelName, ProgressListener<TransferProgress> listener,
                         CancellationToken cancellation) {
        }

        @Override
        public void preload(Endpoint endpoint, String modelName, ModelOptions options, String keepAlive,
                            CancellationToken cancellation) {
        }

        @Override
        public void unload(Endpoint endpoint, String modelName, CancellationToken cancellation) {
        }

        @Override
        public void delete(Endpoint endpoint, String modelName, CancellationToken cancellation) {
        }

        @Override
        public GenerationMetrics generate(Endpoint endpoint, String modelName, String prompt, ModelOptions options,
                                          String keepAlive, CancellationToken cancellation) {
            return new GenerationMetrics(5, 20, 1_000_000L, 10, 50_000_000L, 16, 160_000_000L);
        }
    }
}
