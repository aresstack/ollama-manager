package com.aresstack.ollamamanager.infrastructure.runtime;

import com.aresstack.ollamamanager.domain.server.Endpoint;
import com.aresstack.ollamamanager.infrastructure.json.Json;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Checks that something listens, then probes {@code GET /api/version} directly, bypassing any configured proxy, and classifies the answer.
 */
public final class HttpEndpointProbe implements EndpointProbe {

    private static final int MAX_BODY_BYTES = 64 * 1024;
    /**
     * Long enough for Windows to report a refused loopback connect, which it retries for about a second before
     * failing; a shorter timeout would turn every free port into an unknown one.
     */
    static final int CONNECT_TIMEOUT_MILLIS = 5000;

    private final SocketConnector connector;
    private final BindChecker bindChecker;

    public HttpEndpointProbe() {
        this(HttpEndpointProbe::connectSocket, HttpEndpointProbe::bindExclusively);
    }

    HttpEndpointProbe(SocketConnector connector, BindChecker bindChecker) {
        this.connector = connector;
        this.bindChecker = bindChecker;
    }

    @Override
    public ProbeResult probe(Endpoint endpoint, int timeoutMillis) {
        ProbeResult listening = checkListening(endpoint);
        if (listening != null) {
            return listening;
        }
        HttpURLConnection connection = null;
        try {
            URL url = new URL(endpoint.baseUrl() + "/api/version");
            connection = (HttpURLConnection) url.openConnection(Proxy.NO_PROXY);
            connection.setConnectTimeout(timeoutMillis);
            connection.setReadTimeout(timeoutMillis);
            connection.setUseCaches(false);
            connection.setInstanceFollowRedirects(false);
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                return ProbeResult.foreign("HTTP " + status);
            }
            return classify(readBounded(connection.getInputStream()));
        } catch (SocketTimeoutException e) {
            return ProbeResult.foreign("no HTTP answer within " + timeoutMillis + " ms");
        } catch (IOException e) {
            return ProbeResult.foreign(e.getClass().getSimpleName() + ": " + e.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * Return {@link ProbeResult#free()} for an explicit refusal, or {@code null} if something listens. A connect
     * timeout proves nothing, so it counts as free only if an exclusive bind of the exact address and port
     * succeeds; every other outcome fails closed as foreign. The real server can still lose a bind race later,
     * which the runtime reports as {@code PORT_IN_USE}.
     */
    private ProbeResult checkListening(Endpoint endpoint) {
        try {
            connector.connect(endpoint, CONNECT_TIMEOUT_MILLIS);
            return null;
        } catch (ConnectException e) {
            return ProbeResult.free();
        } catch (SocketTimeoutException e) {
            return bindChecker.canBind(endpoint) ? ProbeResult.free()
                : ProbeResult.foreign("connect timed out and the port cannot be bound");
        } catch (IOException e) {
            return ProbeResult.foreign(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /** Tries an exclusive bind of the exact endpoint and releases it again; replaceable in tests. */
    interface BindChecker {
        boolean canBind(Endpoint endpoint);
    }

    /**
     * Bind exactly the endpoint's address without {@code SO_REUSEADDR}. IPv6 hosts may be bracketed. A listener on
     * the wildcard address also blocks this bind on Windows (exclusive bind is the JDK default there) and Linux.
     */
    static boolean bindExclusively(Endpoint endpoint) {
        String host = endpoint.host();
        if (host.startsWith("[") && host.endsWith("]")) {
            host = host.substring(1, host.length() - 1);
        }
        try (ServerSocket socket = new ServerSocket()) {
            socket.setReuseAddress(false);
            socket.bind(new InetSocketAddress(InetAddress.getByName(host), endpoint.port()), 1);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** Opens and closes a plain TCP connection; replaceable in tests. */
    interface SocketConnector {
        void connect(Endpoint endpoint, int timeoutMillis) throws IOException;
    }

    private static void connectSocket(Endpoint endpoint, int timeoutMillis) throws IOException {
        String host = endpoint.host();
        if (host.startsWith("[") && host.endsWith("]")) {
            host = host.substring(1, host.length() - 1);
        }
        try (Socket socket = new Socket(Proxy.NO_PROXY)) {
            socket.connect(new InetSocketAddress(host, endpoint.port()), timeoutMillis);
        }
    }

    static ProbeResult classify(String body) {
        try {
            Object parsed = Json.parse(body);
            if (parsed instanceof Map) {
                Object version = ((Map<?, ?>) parsed).get("version");
                if (version instanceof String && !((String) version).trim().isEmpty()) {
                    return ProbeResult.ollama(((String) version).trim());
                }
            }
            return ProbeResult.foreign("response has no version field");
        } catch (IllegalArgumentException e) {
            return ProbeResult.foreign("response is not JSON");
        }
    }

    private static String readBounded(InputStream input) throws IOException {
        try (InputStream in = input) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                if (out.size() + read > MAX_BODY_BYTES) {
                    throw new IOException("response exceeds " + MAX_BODY_BYTES + " bytes");
                }
                out.write(buffer, 0, read);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
