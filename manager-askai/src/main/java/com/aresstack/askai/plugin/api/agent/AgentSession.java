package com.aresstack.askai.plugin.api.agent;

import com.aresstack.askai.plugin.api.agent.artifact.AgentArtifactStore;

import java.util.List;

/**
 * A live agent session. Unlike a workspace it owns no chat surface and no composer — those are the host's
 * shared components. It exposes a {@link ChatSubmissionTarget} the composer routes to, the artifacts it
 * contributes, and a generic {@link AgentStateSnapshot}. Its backend activity is delivered to the shared chat
 * through the {@link AgentConversationSink} it was given at creation.
 *
 * <p>Threading &amp; ownership: created by an {@link AgentSessionFactory}, driven on the UI thread, and closed
 * exactly once. After {@link #close()} no further conversation-sink call must happen.</p>
 */
public interface AgentSession {

    ChatSubmissionTarget getChatTarget();

    List<AgentArtifact> getArtifacts();

    /** @return the store the shared artifact views read/write Markdown through, or {@code null} if none. */
    AgentArtifactStore getArtifactStore();

    AgentStateSnapshot getState();

    /** Bring the session to the foreground; may start or resume its run. Idempotent. */
    void activate();

    /** Send to background; must preserve state (no disposal here). */
    void deactivate();

    /** Release the backend session and any owned resources. Idempotent; no sink call afterwards. */
    void close();
}
