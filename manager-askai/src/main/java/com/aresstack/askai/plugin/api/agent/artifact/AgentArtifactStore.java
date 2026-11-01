package com.aresstack.askai.plugin.api.agent.artifact;

/**
 * Read/write access to an agent's Markdown artifacts by stable id. The same store is used by the host's
 * default Markdown view AND (later) the agent's own tools, so user and agent never keep divergent copies.
 * Writes are guarded by {@code expectedRevision} (optimistic locking) to prevent lost updates. Content is
 * never held in UI state; it is read and written through here on demand. Called on the UI thread.
 */
public interface AgentArtifactStore {

    /** @return the current content + revision, or an empty content at revision 0 for an unknown id. */
    ArtifactContent read(String artifactId);

    /** Replace the artifact's Markdown iff {@code expectedRevision} matches; otherwise a conflict result. */
    ArtifactWriteResult replace(String artifactId, long expectedRevision, String markdown);
}
