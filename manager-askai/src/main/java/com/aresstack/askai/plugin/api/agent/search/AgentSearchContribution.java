package com.aresstack.askai.plugin.api.agent.search;

import com.aresstack.askai.plugin.api.agent.AgentSession;

/**
 * The ACTIVE agent's search in the host's title bar. The host owns ONE search field (look, focus, Enter/go
 * button, clearing after a search); the plugin only defines what the field means: its placeholder, its
 * tooltip and what a search does — e.g. the research agent runs a web search, another agent could search its
 * documents. Without an active agent that provides one, the title-bar search is hidden.
 */
public interface AgentSearchContribution {

    /** Whether this search applies to the given session (e.g. only research sessions). */
    boolean supports(AgentSession session);

    /** The hint shown in the empty field, e.g. "Web search…". */
    String getPlaceholder();

    /** The field's tooltip, e.g. what the search does. */
    String getTooltip();

    /**
     * Runs a search for {@code session}. Called on the EDT with a non-empty, trimmed query; must not block
     * (start background work and return).
     */
    void search(AgentSession session, String query);
}
