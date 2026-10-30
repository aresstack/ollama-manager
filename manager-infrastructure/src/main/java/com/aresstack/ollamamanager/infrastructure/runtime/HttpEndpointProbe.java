package com.aresstack.ollamamanager.infrastructure.runtime;

import com.aresstack.ollamamanager.domain.server.Endpoint;
import com.aresstack.ollamamanager.infrastructure.json.Json;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
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

    @Override
    public ProbeResult probe(Endpoint endpoint, int timeoutMillis) {
        ProbeResult listening = checkListening(endpoint, timeoutMillis);
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
        } catch (ConnectException e) {
            return ProbeResult.free();
        } catch (IOException e) {
            return ProbeResult.foreign(e.getClass().getSimpleName() + ": " + e.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * Return {@link ProbeResult#free()} if nothing accepts connections, or {@code null} if something listens.
     * Windows retries a refused loopback connect for about two seconds instead of failing fast, so a connect
     * timeout on a loopback address also means that nothing listens there.
     */
    private static ProbeResult checkListening(Endpoint endpoint, int timeoutMillis) {
        try (Socket socket = new Socket(Proxy.NO_PROXY)) {
            socket.connect(new InetSocketAddress(endpoint.host(), endpoint.port()), timeoutMillis);
            return null;
        } catch (ConnectException e) {
            return ProbeResult.free();
        } catch (SocketTimeoutException e) {
            return endpoint.isLoopback() ? ProbeResult.free() : ProbeResult.foreign("connect timed out");
        } catch (IOException e) {
            return ProbeResult.foreign(e.getClass().getSimpleName() + ": " + e.getMessage());
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
