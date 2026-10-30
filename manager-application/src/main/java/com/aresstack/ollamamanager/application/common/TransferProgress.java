package com.aresstack.ollamamanager.application.common;

/**
 * Progress of a byte transfer such as a download or model pull layer.
 */
public final class TransferProgress {

    private final String phase;
    private final long completedBytes;
    private final long totalBytes;

    /**
     * @param totalBytes total size, or {@code -1} if unknown
     */
    public TransferProgress(String phase, long completedBytes, long totalBytes) {
        if (completedBytes < 0 || totalBytes < -1) {
            throw new IllegalArgumentException("invalid byte counts: " + completedBytes + "/" + totalBytes);
        }
        this.phase = phase == null ? "" : phase;
        this.completedBytes = completedBytes;
        this.totalBytes = totalBytes;
    }

    /** Return a short status such as {@code downloading} or {@code verifying sha256 digest}. */
    public String phase() {
        return phase;
    }

    public long completedBytes() {
        return completedBytes;
    }

    /** Return the total size, or {@code -1} if unknown. */
    public long totalBytes() {
        return totalBytes;
    }

    /** Return the completed fraction between 0 and 1, or {@code -1} if the total is unknown. */
    public double fraction() {
        if (totalBytes <= 0) {
            return -1;
        }
        return Math.min(1.0, (double) completedBytes / totalBytes);
    }

    @Override
    public String toString() {
        return phase + " " + completedBytes + "/" + totalBytes;
    }
}
