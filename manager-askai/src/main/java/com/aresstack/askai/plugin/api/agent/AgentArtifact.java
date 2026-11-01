package com.aresstack.askai.plugin.api.agent;

/**
 * A generic artifact an agent exposes to the shared artifact area (e.g. a Markdown document, a structured
 * sources table, a state view). The type is an opaque string id so the host can match it to an
 * {@link ArtifactViewContribution} (or its own default Markdown view) without knowing agent-specific enums.
 *
 * <p>The content itself is NOT held here: a Markdown artifact's text lives behind the plugin's own store and
 * is read/written by revision, so large documents never sit in UI state and edits are safe against lost
 * updates.</p>
 */
public interface AgentArtifact {

    /** Stable id used in commands and tool arguments (e.g. {@code "outline"}). */
    String getId();

    String getDisplayName();

    /** Opaque view-type id, e.g. {@code "markdown"}, {@code "research.sources"}, {@code "research.state"}. */
    String getArtifactTypeId();

    /** Relative path within the project store (e.g. {@code "outline.md"}); may be empty for non-file artifacts. */
    String getRelativePath();

    /** Monotonic revision for optimistic locking / change detection. */
    long getRevision();
}
