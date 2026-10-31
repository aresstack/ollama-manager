package com.aresstack.ollamamanager.swing.job;

import com.aresstack.ollamamanager.application.common.CancellationToken;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * One background operation with observable state. All state changes are published through the UI dispatcher,
 * so listeners and getters are meant to be used on the UI thread.
 */
public final class Job {

    /** Fraction value for progress without a known total. */
    public static final double INDETERMINATE = -1d;

    private final long id;
    private final String title;
    private final boolean cancellable;
    private final CancellationToken cancellation = new CancellationToken();
    private final UiDispatcher dispatcher;
    private final List<Consumer<Job>> listeners = new CopyOnWriteArrayList<Consumer<Job>>();

    private volatile JobState state = JobState.RUNNING;
    private volatile String phase = "";
    private volatile double fraction = INDETERMINATE;
    private volatile String errorMessage;

    Job(long id, String title, boolean cancellable, UiDispatcher dispatcher) {
        this.id = id;
        this.title = title;
        this.cancellable = cancellable;
        this.dispatcher = dispatcher;
    }

    public long id() {
        return id;
    }

    public String title() {
        return title;
    }

    public boolean isCancellable() {
        return cancellable && !state.isFinished() && state != JobState.CANCELLING;
    }

    public JobState state() {
        return state;
    }

    public String phase() {
        return phase;
    }

    /** Completion between 0 and 1, or {@link #INDETERMINATE}. */
    public double fraction() {
        return fraction;
    }

    /** User-facing failure text, or {@code null} unless the job failed. */
    public String errorMessage() {
        return errorMessage;
    }

    CancellationToken cancellation() {
        return cancellation;
    }

    /**
     * Request cancellation. The job reports {@link JobState#CANCELLED} only once the work has actually returned.
     */
    public void cancel() {
        if (!isCancellable()) {
            return;
        }
        state = JobState.CANCELLING;
        phase = "Cancelling…";
        cancellation.cancel();
        fire();
    }

    /** Observe state changes; the listener runs on the UI thread. */
    public void addListener(Consumer<Job> listener) {
        listeners.add(listener);
    }

    public void removeListener(Consumer<Job> listener) {
        listeners.remove(listener);
    }

    void publishProgress(String newPhase, double newFraction) {
        dispatcher.dispatch(() -> {
            if (state.isFinished() || state == JobState.CANCELLING) {
                return;
            }
            phase = newPhase == null ? "" : newPhase;
            fraction = newFraction;
            fire();
        });
    }

    void finish(JobState finalState, String message) {
        state = finalState;
        errorMessage = finalState == JobState.FAILED ? message : null;
        phase = finalState.label();
        if (finalState == JobState.SUCCEEDED) {
            fraction = 1d;
        }
        fire();
    }

    private void fire() {
        for (Consumer<Job> listener : listeners) {
            listener.accept(this);
        }
    }

    @Override
    public String toString() {
        return title + " (" + state.label() + ")";
    }
}
