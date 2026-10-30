package com.aresstack.ollamamanager.domain.server;

import com.aresstack.ollamamanager.domain.common.Preconditions;

import java.io.File;

/**
 * A discovered or installed Ollama runtime. External installations have no binary directory.
 */
public final class OllamaInstallation {

    public static final String UNKNOWN_VERSION = "unknown";

    private final InstallationKind kind;
    private final String version;
    private final File binaryDirectory;
    private final Endpoint endpoint;

    public OllamaInstallation(InstallationKind kind, String version, File binaryDirectory, Endpoint endpoint) {
        this.kind = Preconditions.requireNonNull(kind, "kind");
        this.version = version == null || version.trim().isEmpty() ? UNKNOWN_VERSION : version.trim();
        this.endpoint = Preconditions.requireNonNull(endpoint, "endpoint");
        if (kind.isStartableByManager()) {
            Preconditions.requireNonNull(binaryDirectory, "binaryDirectory");
            if (!binaryDirectory.isAbsolute()) {
                throw new IllegalArgumentException("binaryDirectory must be absolute: " + binaryDirectory);
            }
        }
        this.binaryDirectory = binaryDirectory;
    }

    public static OllamaInstallation external(String version, Endpoint endpoint) {
        return new OllamaInstallation(InstallationKind.EXTERNAL_ATTACHED, version, null, endpoint);
    }

    public InstallationKind kind() {
        return kind;
    }

    public String version() {
        return version;
    }

    /**
     * Return the directory containing the Ollama executable, or {@code null} for an external server.
     */
    public File binaryDirectory() {
        return binaryDirectory;
    }

    public Endpoint endpoint() {
        return endpoint;
    }

    public OllamaInstallation withEndpoint(Endpoint newEndpoint) {
        return new OllamaInstallation(kind, version, binaryDirectory, newEndpoint);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof OllamaInstallation)) {
            return false;
        }
        OllamaInstallation that = (OllamaInstallation) other;
        return kind == that.kind && version.equals(that.version) && endpoint.equals(that.endpoint)
            && (binaryDirectory == null ? that.binaryDirectory == null : binaryDirectory.equals(that.binaryDirectory));
    }

    @Override
    public int hashCode() {
        int result = kind.hashCode();
        result = 31 * result + version.hashCode();
        result = 31 * result + (binaryDirectory == null ? 0 : binaryDirectory.hashCode());
        return 31 * result + endpoint.hashCode();
    }

    @Override
    public String toString() {
        return "OllamaInstallation{" + kind + ", " + version + ", " + binaryDirectory + ", " + endpoint + "}";
    }
}
