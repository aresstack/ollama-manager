package com.aresstack.ollamamanager.infrastructure.ollamaapi;

import com.aresstack.ollamamanager.application.common.CancellationToken;
import com.aresstack.ollamamanager.application.common.ErrorKind;
import com.aresstack.ollamamanager.application.common.OperationCancelledException;
import com.aresstack.ollamamanager.application.common.OperationException;
import com.aresstack.ollamamanager.application.common.ProgressListener;
import com.aresstack.ollamamanager.application.common.TransferProgress;
import com.aresstack.ollamamanager.application.port.OllamaApiPort;
import com.aresstack.ollamamanager.domain.model.InstalledModel;
import com.aresstack.ollamamanager.domain.model.LoadedModel;
import com.aresstack.ollamamanager.domain.model.ModelDetails;
import com.aresstack.ollamamanager.domain.model.ModelOptions;
import com.aresstack.ollamamanager.domain.server.Endpoint;
import com.aresstack.ollamamanager.infrastructure.json.Json;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * {@link OllamaApiPort} on OkHttp. Bodies are read incrementally and bounded, so a pull never buffers the
 * whole response. Cancellation calls {@link Call#cancel()}, which also aborts a blocked read; the registration
 * is closed when the request ends, so a reused token keeps no stale callbacks. Loopback
 * endpoints bypass every proxy (see {@link LoopbackDirectProxySelector}).
 */
public final class HttpOllamaApiClient implements OllamaApiPort {

    static final int MAX_BODY_BYTES = 16 * 1024 * 1024;
    static final int MAX_LINE_CHARS = 1024 * 1024;

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient client;
    private final OkHttpClient longClient;

    /** Create a client with 5 s connect, 30 s read and 10 min idle timeout for pull and model loading. */
    public HttpOllamaApiClient() {
        this(5000, 30000, 600000);
    }

    /**
     * @param longReadTimeoutMillis idle read timeout for pull streams and {@code /api/generate}, which can
     *                              block while a model loads
     */
    public HttpOllamaApiClient(int connectTimeoutMillis, int readTimeoutMillis, int longReadTimeoutMillis) {
        if (connectTimeoutMillis <= 0 || readTimeoutMillis <= 0 || longReadTimeoutMillis <= 0) {
            throw new IllegalArgumentException("timeouts must be positive");
        }
        client = new OkHttpClient.Builder()
            .proxySelector(new LoopbackDirectProxySelector())
            .socketFactory(new DirectSocketFactory())
            .connectTimeout(connectTimeoutMillis, TimeUnit.MILLISECONDS)
            .readTimeout(readTimeoutMillis, TimeUnit.MILLISECONDS)
            .writeTimeout(readTimeoutMillis, TimeUnit.MILLISECONDS)
            // Never resend a pull, preload or delete on its own.
            .retryOnConnectionFailure(false)
            .followRedirects(false)
            .addNetworkInterceptor(chain -> {
                ConnectionState state = chain.request().tag(ConnectionState.class);
                if (state != null) {
                    state.connected = true;
                }
                return chain.proceed(chain.request());
            })
            .build();
        longClient = client.newBuilder().readTimeout(longReadTimeoutMillis, TimeUnit.MILLISECONDS).build();
    }

    @Override
    public String version(Endpoint endpoint, CancellationToken cancellation) throws OperationException {
        Object root = exchange(client, endpoint, "GET", "/api/version", null, cancellation, true);
        return map(root, "version", OllamaResponseMapper::version);
    }

    @Override
    public List<InstalledModel> installedModels(Endpoint endpoint, CancellationToken cancellation)
        throws OperationException {
        Object root = exchange(client, endpoint, "GET", "/api/tags", null, cancellation, true);
        return map(root, "tags", OllamaResponseMapper::installedModels);
    }

    @Override
    public List<LoadedModel> loadedModels(Endpoint endpoint, CancellationToken cancellation)
        throws OperationException {
        Object root = exchange(client, endpoint, "GET", "/api/ps", null, cancellation, true);
        return map(root, "ps", OllamaResponseMapper::loadedModels);
    }

    @Override
    public ModelDetails show(Endpoint endpoint, String modelName, CancellationToken cancellation)
        throws OperationException {
        Object root = exchange(client, endpoint, "POST", "/api/show", modelBody(modelName), cancellation, true);
        return map(root, "show", value -> OllamaResponseMapper.details(modelName, value));
    }

    @Override
    public void pull(Endpoint endpoint, String modelName, ProgressListener<TransferProgress> listener,
                     CancellationToken cancellation) throws OperationException {
        Map<String, Object> body = modelBody(modelName);
        body.put("stream", Boolean.TRUE);
        ConnectionState state = new ConnectionState();
        Call call = newCall(longClient, endpoint, "POST", "/api/pull", body, state, cancellation);
        CancellationToken.Registration registration = cancellation.onCancel(call::cancel);
        try (Response response = call.execute()) {
            ResponseBody responseBody = response.body();
            if (!response.isSuccessful()) {
                throw httpFailure(response.code(), readBody(responseBody));
            }
            readPullStream(responseBody.byteStream(), listener, cancellation);
        } catch (IOException e) {
            throw ioFailure(e, state, cancellation);
        } finally {
            registration.close();
        }
    }

    @Override
    public void preload(Endpoint endpoint, String modelName, ModelOptions options, String keepAlive,
                        CancellationToken cancellation) throws OperationException {
        Map<String, Object> body = modelBody(modelName);
        body.put("stream", Boolean.FALSE);
        if (keepAlive != null) {
            body.put("keep_alive", keepAlive);
        }
        Map<String, Object> requestOptions = (options == null ? ModelOptions.none() : options).toRequestOptions();
        if (!requestOptions.isEmpty()) {
            body.put("options", requestOptions);
        }
        exchange(longClient, endpoint, "POST", "/api/generate", body, cancellation, true);
    }

    @Override
    public void unload(Endpoint endpoint, String modelName, CancellationToken cancellation)
        throws OperationException {
        Map<String, Object> body = modelBody(modelName);
        body.put("stream", Boolean.FALSE);
        body.put("keep_alive", Long.valueOf(0));
        exchange(client, endpoint, "POST", "/api/generate", body, cancellation, true);
    }

    @Override
    public void delete(Endpoint endpoint, String modelName, CancellationToken cancellation)
        throws OperationException {
        exchange(client, endpoint, "DELETE", "/api/delete", modelBody(modelName), cancellation, false);
    }

    private Object exchange(OkHttpClient httpClient, Endpoint endpoint, String method, String path,
                            Map<String, Object> body, CancellationToken cancellation, boolean expectJson)
        throws OperationException {
        ConnectionState state = new ConnectionState();
        Call call = newCall(httpClient, endpoint, method, path, body, state, cancellation);
        String text;
        CancellationToken.Registration registration = cancellation.onCancel(call::cancel);
        try (Response response = call.execute()) {
            text = readBody(response.body());
            if (!response.isSuccessful()) {
                throw httpFailure(response.code(), text);
            }
        } catch (IOException e) {
            throw ioFailure(e, state, cancellation);
        } finally {
            registration.close();
        }
        cancellation.throwIfCancelled();
        if (!expectJson) {
            return null;
        }
        try {
            return Json.parse(text);
        } catch (IllegalArgumentException e) {
            throw new OperationException(ErrorKind.NOT_OLLAMA, path + " returned no valid JSON", e);
        }
    }

    private static Call newCall(OkHttpClient httpClient, Endpoint endpoint, String method, String path,
                                Map<String, Object> body, ConnectionState state, CancellationToken cancellation)
        throws OperationException {
        cancellation.throwIfCancelled();
        RequestBody requestBody = body == null ? null
            : RequestBody.create(Json.write(body).getBytes(StandardCharsets.UTF_8), JSON);
        Request request = new Request.Builder()
            .url(endpoint.baseUrl() + path)
            .header("Accept", "application/json, application/x-ndjson")
            .method(method, requestBody)
            .tag(ConnectionState.class, state)
            .build();
        return httpClient.newCall(request);
    }

    private static void readPullStream(InputStream in, ProgressListener<TransferProgress> listener,
                                       CancellationToken cancellation) throws IOException, OperationException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        StringBuilder line = new StringBuilder();
        int c;
        while (true) {
            c = reader.read();
            if (c == -1 || c == '\n') {
                if (line.toString().trim().length() > 0 && handlePullLine(line.toString(), listener)) {
                    return;
                }
                cancellation.throwIfCancelled();
                if (c == -1) {
                    throw new OperationException(ErrorKind.IO, "pull stream ended before success");
                }
                line.setLength(0);
            } else {
                if (line.length() >= MAX_LINE_CHARS) {
                    throw new OperationException(ErrorKind.NOT_OLLAMA, "pull status line too long");
                }
                line.append((char) c);
            }
        }
    }

    /** Report one NDJSON status line; return whether it signals success. */
    private static boolean handlePullLine(String line, ProgressListener<TransferProgress> listener)
        throws OperationException {
        Map<String, Object> status;
        try {
            status = OllamaResponseMapper.asMap(Json.parse(line), "pull status");
        } catch (IllegalArgumentException e) {
            throw new OperationException(ErrorKind.NOT_OLLAMA, "invalid pull status line", e);
        }
        String error = OllamaResponseMapper.errorMessage(status);
        if (error != null) {
            throw new OperationException(ErrorKind.HTTP_ERROR, "pull failed: " + error);
        }
        Object phase = status.get("status");
        if (!(phase instanceof String)) {
            throw new OperationException(ErrorKind.NOT_OLLAMA, "pull status line without status");
        }
        long completed;
        long total;
        try {
            Long completedValue = OllamaResponseMapper.optionalLong(status, "completed");
            Long totalValue = OllamaResponseMapper.optionalLong(status, "total");
            total = totalValue == null || totalValue < 0 ? -1 : totalValue;
            completed = completedValue == null || completedValue < 0 ? 0 : completedValue;
        } catch (IllegalArgumentException e) {
            throw new OperationException(ErrorKind.NOT_OLLAMA, "invalid pull byte counts", e);
        }
        listener.onProgress(new TransferProgress((String) phase, completed, total));
        return "success".equals(phase);
    }

    /** Read a body up to {@link #MAX_BODY_BYTES}, failing with {@code NOT_OLLAMA} beyond that. */
    static String readBody(ResponseBody body) throws IOException, OperationException {
        if (body == null) {
            return "";
        }
        try (InputStream in = body.byteStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                if (out.size() + read > MAX_BODY_BYTES) {
                    throw new OperationException(ErrorKind.NOT_OLLAMA, "response exceeds " + MAX_BODY_BYTES
                        + " bytes");
                }
                out.write(buffer, 0, read);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    static OperationException httpFailure(int status, String body) {
        String message = null;
        try {
            message = OllamaResponseMapper.errorMessage(Json.parse(body));
        } catch (IllegalArgumentException ignored) {
            // A non-JSON error body still yields the status code below.
        }
        String text = "HTTP " + status + (message == null ? "" : ": " + message);
        if (status == 404) {
            return new OperationException(ErrorKind.NOT_FOUND, text);
        }
        if (status == 503) {
            return new OperationException(ErrorKind.SERVICE_UNAVAILABLE, text);
        }
        return new OperationException(ErrorKind.HTTP_ERROR, text);
    }

    /**
     * Map an IO failure. A failure before the connection was established is {@code CONNECTION_FAILED}, also
     * for a connect timeout, which Windows reports for a closed port after its SYN retries.
     */
    static OperationException ioFailure(IOException e, ConnectionState state, CancellationToken cancellation) {
        if (cancellation.isCancelled()) {
            return new OperationCancelledException("operation cancelled");
        }
        if (!state.connected) {
            return new OperationException(ErrorKind.CONNECTION_FAILED, "cannot connect to Ollama", e);
        }
        if (e instanceof SocketTimeoutException) {
            return new OperationException(ErrorKind.TIMEOUT, "Ollama did not answer in time", e);
        }
        return new OperationException(ErrorKind.IO, "communication with Ollama failed: " + e.getMessage(), e);
    }

    private static <T> T map(Object root, String what, Mapping<T> mapping) throws OperationException {
        try {
            return mapping.apply(root);
        } catch (IllegalArgumentException e) {
            throw new OperationException(ErrorKind.NOT_OLLAMA, "unexpected " + what + " response: "
                + e.getMessage(), e);
        }
    }

    private static Map<String, Object> modelBody(String modelName) {
        if (modelName == null || modelName.trim().isEmpty()) {
            throw new IllegalArgumentException("modelName must not be blank");
        }
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("model", modelName.trim());
        return body;
    }

    private interface Mapping<T> {
        T apply(Object root);
    }

    /** Set by a network interceptor once a connection to the server exists. */
    static final class ConnectionState {
        volatile boolean connected;
    }
}
