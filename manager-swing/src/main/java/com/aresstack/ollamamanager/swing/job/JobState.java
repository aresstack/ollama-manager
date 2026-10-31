package com.aresstack.ollamamanager.swing.job;

/**
 * Lifecycle of a background job as shown in the UI.
 */
public enum JobState {
    RUNNING("Running"),
    CANCELLING("Cancelling…"),
    SUCCEEDED("Done"),
    FAILED("Failed"),
    CANCELLED("Cancelled");

    private final String label;

    JobState(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean isFinished() {
        return this == SUCCEEDED || this == FAILED || this == CANCELLED;
    }
}
