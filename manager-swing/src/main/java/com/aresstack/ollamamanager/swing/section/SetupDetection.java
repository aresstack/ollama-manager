package com.aresstack.ollamamanager.swing.section;

import com.aresstack.ollamamanager.domain.server.Endpoint;
import com.aresstack.ollamamanager.domain.server.InstallationKind;
import com.aresstack.ollamamanager.domain.server.OllamaInstallation;
import com.aresstack.ollamamanager.domain.server.ServerSettings;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What the setup assistant found: a server answering on the configured endpoint, startable installations on disk,
 * the manager's portable versions and the installation currently selected in the configuration.
 */
public final class SetupDetection {

    private final Endpoint endpoint;
    private final ServerSettings settings;
    private final String runningVersion;
    private final String probeFailure;
    private final boolean externalAttached;
    private final List<OllamaInstallation> discovered;
    private final List<OllamaInstallation> portable;
    private final InstallationKind selectedKind;
    private final File selectedDirectory;

    SetupDetection(Endpoint endpoint, ServerSettings settings, String runningVersion, String probeFailure,
                   boolean externalAttached,
                   List<OllamaInstallation> discovered,
                   List<OllamaInstallation> portable, InstallationKind selectedKind, File selectedDirectory) {
        this.endpoint = endpoint;
        this.settings = settings;
        this.runningVersion = runningVersion;
        this.probeFailure = probeFailure;
        this.externalAttached = externalAttached;
        this.discovered = Collections.unmodifiableList(new ArrayList<OllamaInstallation>(discovered));
        this.portable = Collections.unmodifiableList(new ArrayList<OllamaInstallation>(portable));
        this.selectedKind = selectedKind;
        this.selectedDirectory = selectedDirectory;
    }

    /** @return the endpoint that was probed: the bind address of the stored server settings */
    public Endpoint endpoint() {
        return endpoint;
    }

    /** @return the version reported by the server on {@link #endpoint()}, or {@code null} if nothing answered */
    public String runningVersion() {
        return runningVersion;
    }

    public boolean isServerRunning() {
        return runningVersion != null;
    }

    /** @return why the endpoint could not be checked (not Ollama, no answer in time), or {@code null} */
    public String probeFailure() {
        return probeFailure;
    }

    /** @return the stored server settings this detection was made with */
    ServerSettings settings() {
        return settings;
    }

    /** @return whether the manager currently uses an external server read-only */
    public boolean isExternalAttached() {
        return externalAttached;
    }

    /** @return installations found on disk outside the manager's portable directory */
    public List<OllamaInstallation> discovered() {
        return discovered;
    }

    /** @return portable versions installed by the manager */
    public List<OllamaInstallation> portable() {
        return portable;
    }

    /** @return every startable installation, portable versions first */
    public List<OllamaInstallation> installations() {
        List<OllamaInstallation> all = new ArrayList<OllamaInstallation>(portable);
        for (OllamaInstallation installation : discovered) {
            if (!all.contains(installation)) {
                all.add(installation);
            }
        }
        return all;
    }

    /** @return the stored selection if it is one of the found installations, otherwise {@code null} */
    public OllamaInstallation selected() {
        if (selectedKind == null || selectedDirectory == null) {
            return null;
        }
        for (OllamaInstallation installation : installations()) {
            if (installation.kind() == selectedKind
                && selectedDirectory.getAbsoluteFile().equals(installation.binaryDirectory().getAbsoluteFile())) {
                return installation;
            }
        }
        return null;
    }

    /** @return whether a selection is stored that no longer exists on disk */
    public boolean isSelectionMissing() {
        return selectedKind != null && selectedDirectory != null && selected() == null;
    }

    public boolean isSelected(OllamaInstallation installation) {
        return installation != null && installation.equals(selected());
    }
}
