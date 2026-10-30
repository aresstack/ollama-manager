package com.aresstack.ollamamanager.infrastructure.distribution;

import com.aresstack.ollamamanager.application.common.CancellationToken;
import com.aresstack.ollamamanager.application.common.ErrorKind;
import com.aresstack.ollamamanager.application.common.OperationCancelledException;
import com.aresstack.ollamamanager.application.common.OperationException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal HTTP GET client with timeouts, a bounded number of https-only redirects and cancellation that
 * aborts blocking reads by disconnecting.
 */
public final class HttpTransport {

    static final int MAX_REDIRECTS = 5;
    private static final String USER_AGENT = "aresstack-ollama-manager";

    private final UrlResolver resolver;
    private final int connectTimeoutMillis;
    private final int readTimeoutMillis;

    public HttpTransport(UrlResolver resolver, int connectTimeoutMillis, int readTimeoutMillis) {
        this.resolver = resolver;
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.readTimeoutMillis = readTimeoutMillis;
    }

    public static HttpTransport defaults() {
        return new HttpTransport(UrlResolver.identity(), 15000, 60000);
    }

    /** An open response; the caller must close it. */
    public static final class Response implements AutoCloseable {
        private final HttpURLConnection connection;
        private final int status;
        private final InputStream body;

        Response(HttpURLConnection connection, int status, InputStream body) {
            this.connection = connection;
            this.status = status;
            this.body = body;
        }

        public int status() {
            return status;
        }

        public String header(String name) {
            return connection.getHeaderField(name);
        }

        /** Return the declared body length, or {@code -1} if unknown. */
        public long contentLength() {
            String value = connection.getHeaderField("Content-Length");
            try {
                return value == null ? -1 : Long.parseLong(value.trim());
            } catch (NumberFormatException e) {
                return -1;
            }
        }

        public InputStream body() {
            return body;
        }

        @Override
        public void close() {
            try {
                body.close();
            } catch (IOException ignored) {
                // Disconnect below releases the socket anyway.
            }
            connection.disconnect();
        }
    }

    /**
     * Send a GET request and follow redirects. Returns 2xx responses only; other statuses fail with
     * {@code NOT_FOUND} (404) or {@code HTTP_ERROR}.
     */
    public Response get(String url, Map<String, String> headers, CancellationToken cancellation)
        throws OperationException {
        String current = url;
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            cancellation.throwIfCancelled();
            requireHttps(current);
            HttpURLConnection connection = open(current, headers);
            cancellation.onCancel(connection::disconnect);
            int status;
            try {
                status = connection.getResponseCode();
            } catch (IOException | RuntimeException e) {
                // JDK 8 may throw a RuntimeException when another thread disconnects to cancel.
                connection.disconnect();
                throw failure(e, current, cancellation);
            }
            if (status >= 300 && status < 400 && status != 304) {
                String location = connection.getHeaderField("Location");
                connection.disconnect();
                if (location == null) {
                    throw new OperationException(ErrorKind.HTTP_ERROR, "redirect without Location from " + current);
                }
                current = absolute(current, location);
                continue;
            }
            if (status < 200 || status >= 300) {
                connection.disconnect();
                throw new OperationException(status == 404 ? ErrorKind.NOT_FOUND : ErrorKind.HTTP_ERROR,
                    "HTTP " + status + " from " + current);
            }
            try {
                return new Response(connection, status, connection.getInputStream());
            } catch (IOException | RuntimeException e) {
                // JDK 8 may throw a RuntimeException when another thread disconnects to cancel.
                connection.disconnect();
                throw failure(e, current, cancellation);
            }
        }
        throw new OperationException(ErrorKind.HTTP_ERROR, "more than " + MAX_REDIRECTS + " redirects for " + url);
    }

    /** Fetch a small text body, failing if it exceeds {@code maxBytes}. */
    public String getText(String url, Map<String, String> headers, int maxBytes, CancellationToken cancellation)
        throws OperationException {
        Response response = get(url, headers, cancellation);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = response.body().read(buffer)) != -1) {
                out.write(buffer, 0, read);
                if (out.size() > maxBytes) {
                    throw new OperationException(ErrorKind.HTTP_ERROR, "response too large from " + url);
                }
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw failure(e, url, cancellation);
        } finally {
            response.close();
        }
    }

    static Map<String, String> headers(String... pairs) {
        Map<String, String> map = new LinkedHashMap<String, String>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return Collections.unmodifiableMap(map);
    }

    static OperationException failure(Exception e, String url, CancellationToken cancellation) {
        if (cancellation.isCancelled()) {
            return new OperationCancelledException("download cancelled: " + url);
        }
        if (e instanceof SocketTimeoutException) {
            return new OperationException(ErrorKind.TIMEOUT, "timeout reading " + url, e);
        }
        return new OperationException(ErrorKind.CONNECTION_FAILED, "cannot read " + url + ": " + e.getMessage(), e);
    }

    private HttpURLConnection open(String publicUrl, Map<String, String> headers) throws OperationException {
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(resolver.resolve(publicUrl)).openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(connectTimeoutMillis);
            connection.setReadTimeout(readTimeoutMillis);
            connection.setRequestProperty("User-Agent", USER_AGENT);
            for (Map.Entry<String, String> header : headers.entrySet()) {
                connection.setRequestProperty(header.getKey(), header.getValue());
            }
            return connection;
        } catch (IOException e) {
            throw new OperationException(ErrorKind.CONNECTION_FAILED, "cannot open " + publicUrl, e);
        }
    }

    private static void requireHttps(String url) throws OperationException {
        if (!url.startsWith("https://")) {
            throw new OperationException(ErrorKind.HTTP_ERROR, "refusing non-https URL: " + url);
        }
    }

    private static String absolute(String base, String location) throws OperationException {
        try {
            return new URL(new URL(base), location).toString();
        } catch (MalformedURLException e) {
            throw new OperationException(ErrorKind.HTTP_ERROR, "invalid redirect " + location, e);
        }
    }
}
