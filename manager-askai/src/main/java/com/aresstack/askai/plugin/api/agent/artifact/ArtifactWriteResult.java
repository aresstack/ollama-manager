package com.aresstack.askai.plugin.api.agent.artifact;

/**
 * Outcome of writing an artifact. On success it carries the new revision; on a revision conflict (someone
 * else — the agent or another view — wrote first) it carries the current content so the caller can rebase or
 * warn. Optimistic locking: the write only applies when the caller's {@code expectedRevision} matches.
 */
public final class ArtifactWriteResult {

    private final boolean success;
    private final long revision;
    private final String currentMarkdown;
    private final String reason;

    private ArtifactWriteResult(boolean success, long revision, String currentMarkdown, String reason) {
        this.success = success;
        this.revision = revision;
        this.currentMarkdown = currentMarkdown == null ? "" : currentMarkdown;
        this.reason = reason == null ? "" : reason;
    }

    public static ArtifactWriteResult ok(long newRevision) {
        return new ArtifactWriteResult(true, newRevision, "", "");
    }

    public static ArtifactWriteResult conflict(String currentMarkdown, long currentRevision) {
        return new ArtifactWriteResult(false, currentRevision, currentMarkdown,
                "The artifact changed since it was loaded.");
    }

    public static ArtifactWriteResult error(String reason) {
        return new ArtifactWriteResult(false, -1L, "", reason);
    }

    public boolean isSuccess() {
        return success;
    }

    public long getRevision() {
        return revision;
    }

    public String getCurrentMarkdown() {
        return currentMarkdown;
    }

    public String getReason() {
        return reason;
    }
}
