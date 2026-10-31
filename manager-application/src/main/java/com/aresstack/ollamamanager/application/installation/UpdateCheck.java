package com.aresstack.ollamamanager.application.installation;

import com.aresstack.ollamamanager.domain.distribution.OllamaRelease;
import com.aresstack.ollamamanager.domain.server.OllamaInstallation;

/**
 * Result of comparing the active portable installation with the latest stable release.
 */
public final class UpdateCheck {

    private final OllamaRelease latest;
    private final OllamaInstallation active;

    public UpdateCheck(OllamaRelease latest, OllamaInstallation active) {
        this.latest = latest;
        this.active = active;
    }

    public OllamaRelease latest() {
        return latest;
    }

    /** Return the active portable installation, or {@code null} if none is installed. */
    public OllamaInstallation active() {
        return active;
    }

    /** Return whether nothing is installed or the latest release is newer than the active version. */
    public boolean updateAvailable() {
        return active == null || ReleaseVersions.compare(latest.version(), active.version()) > 0;
    }
}
