package com.aresstack.ollamamanager.swing.job;

import com.aresstack.ollamamanager.application.common.OperationCancelledException;
import com.aresstack.ollamamanager.swing.component.ErrorText;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Runs every blocking UI operation as a {@link Job} off the UI thread, with one uniform outcome policy:
 * <ul>
 *     <li>{@code onSuccess} runs only after the work returned normally, never before.</li>
 *     <li>A cancelled job never reports success or an error dialog; it ends as {@link JobState#CANCELLED}.</li>
 *     <li>Any other exception ends the job as {@link JobState#FAILED} and reaches {@code onFailure}.</li>
 * </ul>
 */
public final class JobManager {

    private final Executor executor;
    private final UiDispatcher dispatcher;
    private final AtomicLong ids = new AtomicLong();
    private final List<Job> jobs = new ArrayList<Job>();
    private final List<Consumer<Job>> listeners = new CopyOnWriteArrayList<Consumer<Job>>();

    public JobManager(Executor executor, UiDispatcher dispatcher) {
        if (executor == null || dispatcher == null) {
            throw new IllegalArgumentException("executor and dispatcher must not be null");
        }
        this.executor = executor;
        this.dispatcher = dispatcher;
    }

    /**
     * Start a job. Callbacks run on the UI thread.
     *
     * @param title       short name shown in the job list
     * @param cancellable whether the UI offers a Cancel button
     * @param work        blocking work
     * @param onSuccess   result handler, called only after confirmed success
     * @param onFailure   failure handler, called with the original exception; never called on cancellation
     */
    public <T> Job submit(String title, boolean cancellable, JobWork<T> work,
                          Consumer<? super T> onSuccess, Consumer<? super Exception> onFailure) {
        final Job job = new Job(ids.incrementAndGet(), title, cancellable, dispatcher);
        dispatcher.dispatch(() -> {
            synchronized (jobs) {
                jobs.add(job);
            }
            fire(job);
        });
        job.addListener(this::fire);
        final JobContext context = new JobContext(job);
        executor.execute(() -> {
            T result;
            try {
                job.cancellation().throwIfCancelled();
                result = work.run(context);
            } catch (Exception e) {
                boolean cancelled = e instanceof OperationCancelledException
                    || e instanceof InterruptedException || job.cancellation().isCancelled();
                dispatcher.dispatch(() -> {
                    if (cancelled) {
                        job.finish(JobState.CANCELLED, null);
                    } else {
                        job.finish(JobState.FAILED, ErrorText.describe(e));
                        if (onFailure != null) {
                            onFailure.accept(e);
                        }
                    }
                });
                return;
            }
            dispatcher.dispatch(() -> {
                // Report the confirmed outcome even if cancel arrived after the work had committed.
                job.finish(JobState.SUCCEEDED, null);
                if (onSuccess != null) {
                    onSuccess.accept(result);
                }
            });
        });
        return job;
    }

    /** Snapshot of all jobs started in this session, oldest first. */
    public List<Job> jobs() {
        synchronized (jobs) {
            return Collections.unmodifiableList(new ArrayList<Job>(jobs));
        }
    }

    /** Number of jobs that have not finished yet. */
    public int activeCount() {
        int count = 0;
        for (Job job : jobs()) {
            if (!job.state().isFinished()) {
                count++;
            }
        }
        return count;
    }

    /** Request cancellation of every running job, for example when the window closes. */
    public void cancelAll() {
        for (Job job : jobs()) {
            job.cancel();
        }
    }

    /** Observe job additions and state changes on the UI thread. */
    public void addListener(Consumer<Job> listener) {
        listeners.add(listener);
    }

    private void fire(Job job) {
        for (Consumer<Job> listener : listeners) {
            listener.accept(job);
        }
    }
}
