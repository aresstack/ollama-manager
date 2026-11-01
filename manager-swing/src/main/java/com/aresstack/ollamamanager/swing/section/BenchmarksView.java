package com.aresstack.ollamamanager.swing.section;

import com.aresstack.ollamamanager.application.benchmark.BenchmarkReport;
import com.aresstack.ollamamanager.application.benchmark.StageResult;
import com.aresstack.ollamamanager.swing.job.Job;

import java.util.List;

/**
 * What the benchmarks presenter needs from its panel. All calls happen on the UI thread.
 */
public interface BenchmarksView {

    void showModels(List<String> models);

    void showServerParallel(String text);

    void showUnavailable(String reason);

    void setRunning(boolean running);

    /** A stage of the running sweep finished; draw it right away. */
    void stageFinished(StageResult stage);

    void showReport(BenchmarkReport report);

    void showHistory(List<BenchmarkReport> reports);

    void showFailure(String action, Exception error);

    void trackJob(Job job);
}
