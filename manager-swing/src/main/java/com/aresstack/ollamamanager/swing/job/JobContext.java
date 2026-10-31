package com.aresstack.ollamamanager.swing.job;

import com.aresstack.ollamamanager.application.common.CancellationToken;

/**
 * What a running job sees: its cancellation token and a way to publish progress.
 */
public final class JobContext {

    private final Job job;

    JobContext(Job job) {
        this.job = job;
    }

    /** Pass this token to every use case call so that Cancel reaches the adapter. */
    public CancellationToken cancellation() {
        return job.cancellation();
    }

    /** Report an indeterminate step. */
    public void phase(String phase) {
        job.publishProgress(phase, Job.INDETERMINATE);
    }

    /** Report a step with a completion fraction between 0 and 1. */
    public void progress(String phase, double fraction) {
        job.publishProgress(phase, Math.max(0d, Math.min(1d, fraction)));
    }
}
