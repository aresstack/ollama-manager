package com.aresstack.askai.plugin.api.agent;

/**
 * Whether the shared composer may currently submit to the active agent session. Drives Send/Stop enablement
 * without the host knowing anything agent-specific.
 */
public enum SubmissionAvailability {

    /** Ready to accept a prompt (Send enabled, Stop disabled). */
    AVAILABLE,

    /** A run is in progress (Send disabled, Stop enabled). */
    BUSY,

    /** The session cannot accept input right now (both disabled), e.g. it is closed or in a terminal state. */
    UNAVAILABLE
}
