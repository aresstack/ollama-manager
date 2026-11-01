package com.aresstack.askai.plugin.api.agent.artifact;

/** An artifact's current text plus its revision, as read from an {@link AgentArtifactStore}. Immutable. */
public final class ArtifactContent {

    private final String markdown;
    private final long revision;

    public ArtifactContent(String markdown, long revision) {
        this.markdown = markdown == null ? "" : markdown;
        this.revision = revision;
    }

    public String getMarkdown() {
        return markdown;
    }

    public long getRevision() {
        return revision;
    }
}
