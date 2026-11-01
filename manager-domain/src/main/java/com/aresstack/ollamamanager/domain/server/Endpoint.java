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

    /**
     * Return the URL clients connect to. A wildcard bind address such as {@code 0.0.0.0} is not connectable on every
     * system, so the loopback address of the same family is used instead.
     */
    public String baseUrl() {
        return "http://" + connectHost() + ":" + port;
    }

    private String connectHost() {
        if (host.equals("0.0.0.0")) {
            return "127.0.0.1";
        }
        if (host.equals("[::]") || host.equals("::")) {
            return "[::1]";
        }
        return host;
    }

    /**
     * Parse an {@code OLLAMA_HOST} value the way Ollama does: an optional {@code http://} or {@code https://}
     * scheme, an optional host (default {@code 127.0.0.1}) and an optional port (default {@link #DEFAULT_PORT}).
     */
    public static Endpoint parseOllamaHost(String value) {
        String text = Preconditions.requireNonBlank(value, "endpoint").trim();
        int scheme = text.indexOf("://");
        if (scheme >= 0) {
            text = text.substring(scheme + 3);
        }
        while (text.endsWith("/")) {
            text = text.substring(0, text.length() - 1);
        }
        int separator = text.lastIndexOf(':');
        boolean bracketed = text.startsWith("[");
        if (separator < 0 || bracketed && separator < text.indexOf(']')) {
            return new Endpoint(text.isEmpty() ? "127.0.0.1" : text, DEFAULT_PORT);
        }
        String host = text.substring(0, separator);
        try {
            return new Endpoint(host.isEmpty() ? "127.0.0.1" : host, Integer.parseInt(text.substring(separator + 1)));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("endpoint port is not a number: " + value, e);
        }
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
