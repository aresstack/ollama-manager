package com.aresstack.ollamamanager.infrastructure.distribution;

import com.aresstack.ollamamanager.application.common.CancellationToken;
import com.aresstack.ollamamanager.application.common.ErrorKind;
import com.aresstack.ollamamanager.application.common.OperationCancelledException;
import com.aresstack.ollamamanager.application.common.OperationException;
import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Minimal HTTP GET client on OkHttp with timeouts and a bounded number of https-only redirects. Cancellation
 * calls {@link Call#cancel()}, which returns immediately and makes a blocked read fail, so cancelling from the
 * Swing event dispatch thread never waits for the read timeout.
 */
public final class HttpTransport {

    static final int MAX_REDIRECTS = 5;
    private static final String USER_AGENT = "aresstack-ollama-manager";

    private final UrlResolver resolver;
    private final OkHttpClient client;

    public HttpTransport(UrlResolver resolver, int connectTimeoutMillis, int readTimeoutMillis) {
        this.resolver = resolver;
        this.client = new OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(connectTimeoutMillis, TimeUnit.MILLISECONDS)
            .readTimeout(readTimeoutMillis, TimeUnit.MILLISECONDS)
            .build();
    }

    public static HttpTransport defaults() {
        return new HttpTransport(UrlResolver.identity(), 15000, 60000);
    }

    /** An open response; the caller must close it. */
    public static final class Response implements AutoCloseable {
        private final okhttp3.Response response;
        private final CancellationToken.Registration registration;

        Response(okhttp3.Response response, CancellationToken.Registration registration) {
            this.response = response;
            this.registration = registration;
        }

        public int status() {
            return response.code();
        }

        public String header(String name) {
            return response.header(name);
        }

        /** Return the declared body length, or {@code -1} if unknown. */
        public long contentLength() {
            return response.body() == null ? -1 : response.body().contentLength();
        }

        public InputStream body() {
            return response.body().byteStream();
        }

        @Override
        public void close() {
            response.close();
            registration.close();
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
            Call call = client.newCall(request(current, headers));
            CancellationToken.Registration registration = cancellation.onCancel(call::cancel);
            okhttp3.Response response = null;
            boolean handedOver = false;
            try {
                try {
                    response = call.execute();
                } catch (IOException e) {
                    throw failure(e, current, cancellation);
                }
                int status = response.code();
                if (status >= 300 && status < 400 && status != 304) {
                    String location = response.header("Location");
                    if (location == null) {
                        throw new OperationException(ErrorKind.HTTP_ERROR, "redirect without Location from " + current);
                    }
                    current = absolute(current, location);
                    continue;
                }
                if (status < 200 || status >= 300) {
                    throw new OperationException(status == 404 ? ErrorKind.NOT_FOUND : ErrorKind.HTTP_ERROR,
                        "HTTP " + status + " from " + current);
                }
                handedOver = true;
                return new Response(response, registration);
            } finally {
                if (!handedOver) {
                    if (response != null) {
                        response.close();
                    }
                    registration.close();
                }
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
            InputStream in = response.body();
            while ((read = in.read(buffer)) != -1) {
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

    private Request request(String publicUrl, Map<String, String> headers) throws OperationException {
        HttpUrl resolved = HttpUrl.parse(resolver.resolve(publicUrl));
        if (resolved == null) {
            throw new OperationException(ErrorKind.HTTP_ERROR, "invalid URL " + publicUrl);
        }
        Request.Builder builder = new Request.Builder().url(resolved).get().header("User-Agent", USER_AGENT);
        for (Map.Entry<String, String> header : headers.entrySet()) {
            builder.header(header.getKey(), header.getValue());
        }
        return builder.build();
    }

    private static void requireHttps(String url) throws OperationException {
        if (!url.startsWith("https://")) {
            throw new OperationException(ErrorKind.HTTP_ERROR, "refusing non-https URL: " + url);
        }
    }

    private static String absolute(String base, String location) throws OperationException {
        HttpUrl baseUrl = HttpUrl.parse(base);
        HttpUrl target = baseUrl == null ? null : baseUrl.resolve(location);
        if (target == null) {
            throw new OperationException(ErrorKind.HTTP_ERROR, "invalid redirect " + location);
        }
        return target.toString();
    }
}
