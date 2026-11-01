package com.aresstack.ollamamanager.swing.section;

import com.aresstack.ollamamanager.application.benchmark.BenchmarkReport;
import com.aresstack.ollamamanager.application.benchmark.StageResult;
import com.aresstack.ollamamanager.domain.profile.ModelProfile;
import com.aresstack.ollamamanager.swing.job.Job;

import java.util.List;

/**
 * What the benchmarks presenter needs from its panel. All calls happen on the UI thread.
 */
public interface BenchmarksView {

    void showModels(List<String> models);

    /** OLLAMA_NUM_PARALLEL of the running server: a number, {@link BenchmarksPresenter#SERVER_DEFAULT} or null. */
    void showServerParallel(String text);

    /**
     * Whether a sweep over server values is possible; it needs a server the manager can restart.
     *
     * @param reason shown while not available, {@code null} when available
     */
    void setSweepAvailable(boolean available, String reason);

    /**
     * Whether the running server can be restarted with a higher OLLAMA_NUM_PARALLEL from here.
     *
     * @param external whether that restart takes over a server the manager did not start
     */
    void setServerRestartAvailable(boolean available, boolean external);

    /** The server was restarted with {@code numParallel} as OLLAMA_NUM_PARALLEL. */
    void showServerRestarted(int numParallel);

    void showUnavailable(String reason);

    void setRunning(boolean running);

    /** A run ended without a result: cancelled, or failed when {@code failed}; the failure itself is shown separately. */
    void runEnded(boolean failed);

    /** A stage of the running sweep finished; draw it right away. */
    void stageFinished(StageResult stage);

    /** One server value of a running sweep finished; the next stages belong to a new line. */
    void reportFinished(BenchmarkReport report);

    void showReport(BenchmarkReport report);

    /** Show several runs on top of each other, for example the results of a sweep. */
    void showReports(List<BenchmarkReport> reports);

    void showHistory(List<BenchmarkReport> reports);

    void showProfileSaved(ModelProfile profile);

    void showFailure(String action, Exception error);

    void trackJob(Job job);
}
