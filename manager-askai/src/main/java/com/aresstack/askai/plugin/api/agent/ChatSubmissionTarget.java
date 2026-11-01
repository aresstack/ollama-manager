package com.aresstack.askai.plugin.api.agent;

/**
 * The routing target the shared composer submits <em>plain</em> text to when an agent session is active
 * (Questing). Slash commands are handled by the command registry BEFORE they reach this target — the target
 * only sees prompts. Yapping keeps its own Ollama route; the host swaps the route, never the composer widget.
 *
 * <p>Called on the UI thread.</p>
 */
public interface ChatSubmissionTarget {

    SubmissionAvailability getAvailability();

    /** Submit a non-command prompt to the active session. Empty/blank text should be ignored. */
    void submitText(String text);

    /** Interrupt the current run, if any. Safe to call when nothing is running. */
    void stop();
}
