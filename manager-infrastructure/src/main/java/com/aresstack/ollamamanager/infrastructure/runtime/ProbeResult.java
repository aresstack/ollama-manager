package com.aresstack.ollamamanager.infrastructure.runtime;

/**
 * Outcome of probing {@code GET /api/version} on an endpoint.
 */
public final class ProbeResult {

    /** What was found on the endpoint. */
    public enum Kind {
        /** Nothing accepts connections. */
        FREE,
        /** An Ollama server answered with a version. */
        OLLAMA,
        /** Something accepts connections but is not a usable Ollama API. */
        FOREIGN
    }

    private static final ProbeResult FREE = new ProbeResult(Kind.FREE, null, "");

    private final Kind kind;
    private final String version;
    private final String detail;

    private ProbeResult(Kind kind, String version, String detail) {
        this.kind = kind;
        this.version = version;
        this.detail = detail == null ? "" : detail;
    }

    public static ProbeResult free() {
        return FREE;
    }

    public static ProbeResult ollama(String version) {
        return new ProbeResult(Kind.OLLAMA, version, "");
    }

    public static ProbeResult foreign(String detail) {
        return new ProbeResult(Kind.FOREIGN, null, detail);
    }

    public Kind kind() {
        return kind;
    }

    /** Return the reported version, or {@code null} unless the kind is {@link Kind#OLLAMA}. */
    public String version() {
        return version;
    }

    public String detail() {
        return detail;
    }

    @Override
    public String toString() {
        return kind + (version == null ? "" : " " + version) + (detail.isEmpty() ? "" : " (" + detail + ")");
    }
}
