package com.aresstack.ollamamanager.domain.server;

import com.aresstack.ollamamanager.domain.common.Preconditions;

import java.util.Locale;

/**
 * Host and port an Ollama server binds to or is reachable at. Defaults to loopback.
 */
public final class Endpoint {

    public static final int DEFAULT_PORT = 11434;
    public static final Endpoint DEFAULT = new Endpoint("127.0.0.1", DEFAULT_PORT);

    private final String host;
    private final int port;

    public Endpoint(String host, int port) {
        this.host = Preconditions.requireNonBlank(host, "host");
        if (this.host.contains("/") || this.host.contains(" ")) {
            throw new IllegalArgumentException("host must be a plain host name or address: " + host);
        }
        this.port = Preconditions.requireInRange(port, 1, 65535, "port");
    }

    /**
     * Parse the {@code host:port} notation used by {@code OLLAMA_HOST}.
     */
    public static Endpoint parse(String value) {
        String text = Preconditions.requireNonBlank(value, "endpoint");
        int separator = text.lastIndexOf(':');
        if (separator <= 0 || separator == text.length() - 1) {
            throw new IllegalArgumentException("endpoint must have the form host:port: " + value);
        }
        try {
            return new Endpoint(text.substring(0, separator), Integer.parseInt(text.substring(separator + 1)));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("endpoint port is not a number: " + value, e);
        }
    }

    public String host() {
        return host;
    }

    public int port() {
        return port;
    }

    public boolean isLoopback() {
        String normalized = host.toLowerCase(Locale.ROOT);
        return normalized.equals("localhost") || normalized.startsWith("127.") || normalized.equals("[::1]");
    }

    /**
     * Return the value for {@code OLLAMA_HOST}.
     */
    public String toHostValue() {
        return host + ":" + port;
    }

    public String baseUrl() {
        return "http://" + toHostValue();
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof Endpoint)) {
            return false;
        }
        Endpoint that = (Endpoint) other;
        return port == that.port && host.equalsIgnoreCase(that.host);
    }

    @Override
    public int hashCode() {
        return 31 * host.toLowerCase(Locale.ROOT).hashCode() + port;
    }

    @Override
    public String toString() {
        return toHostValue();
    }
}
