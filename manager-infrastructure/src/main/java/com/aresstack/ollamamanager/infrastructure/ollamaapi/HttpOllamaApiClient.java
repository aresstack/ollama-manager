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

/**
 * {@link OllamaApiPort} over {@link RawHttpRequest}. Streams are read incrementally and bounded, so a pull
 * never buffers the whole response. Cancellation closes the socket, which aborts a blocked read at once.
 */
public final class HttpOllamaApiClient implements OllamaApiPort {

    private static final int HTTP_OK = 200;

    static final int MAX_BODY_BYTES = 16 * 1024 * 1024;
    static final int MAX_LINE_CHARS = 1024 * 1024;

    private final int connectTimeoutMillis;
    private final int readTimeoutMillis;
    private final int longReadTimeoutMillis;

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
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.readTimeoutMillis = readTimeoutMillis;
        this.longReadTimeoutMillis = longReadTimeoutMillis;
    }

    @Override
    public String version(Endpoint endpoint, CancellationToken cancellation) throws OperationException {
        Object root = exchange(endpoint, "GET", "/api/version", null, readTimeoutMillis, cancellation);
        return map(root, "version", OllamaResponseMapper::version);
    }

    @Override
    public List<InstalledModel> installedModels(Endpoint endpoint, CancellationToken cancellation)
        throws OperationException {
        Object root = exchange(endpoint, "GET", "/api/tags", null, readTimeoutMillis, cancellation);
        return map(root, "tags", OllamaResponseMapper::installedModels);
    }

    @Override
    public List<LoadedModel> loadedModels(Endpoint endpoint, CancellationToken cancellation)
        throws OperationException {
        Object root = exchange(endpoint, "GET", "/api/ps", null, readTimeoutMillis, cancellation);
        return map(root, "ps", OllamaResponseMapper::loadedModels);
    }

    @Override
    public ModelDetails show(Endpoint endpoint, String modelName, CancellationToken cancellation)
        throws OperationException {
        Object root = exchange(endpoint, "POST", "/api/show", modelBody(modelName), readTimeoutMillis,
            cancellation);
        return map(root, "show", value -> OllamaResponseMapper.details(modelName, value));
    }

    @Override
    public void pull(Endpoint endpoint, String modelName, ProgressListener<TransferProgress> listener,
                     CancellationToken cancellation) throws OperationException {
        Map<String, Object> body = modelBody(modelName);
        body.put("stream", Boolean.TRUE);
        cancellation.throwIfCancelled();
        try (RawHttpRequest.Response response = open(endpoint, "POST", "/api/pull", body, longReadTimeoutMillis,
            cancellation)) {
            if (response.status() != HTTP_OK) {
                throw httpFailure(response.status(), readBody(response.body()));
            }
            readPullStream(response.body(), listener, cancellation);
        } catch (IOException e) {
            throw ioFailure(e, cancellation);
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
        exchange(endpoint, "POST", "/api/generate", body, longReadTimeoutMillis, cancellation);
    }

    @Override
    public void unload(Endpoint endpoint, String modelName, CancellationToken cancellation)
        throws OperationException {
        Map<String, Object> body = modelBody(modelName);
        body.put("stream", Boolean.FALSE);
        body.put("keep_alive", Long.valueOf(0));
        exchange(endpoint, "POST", "/api/generate", body, readTimeoutMillis, cancellation);
    }

    @Override
    public void delete(Endpoint endpoint, String modelName, CancellationToken cancellation)
        throws OperationException {
        exchange(endpoint, "DELETE", "/api/delete", modelBody(modelName), readTimeoutMillis, cancellation, false);
    }

    private Object exchange(Endpoint endpoint, String method, String path, Map<String, Object> body,
                            int readTimeout, CancellationToken cancellation) throws OperationException {
        return exchange(endpoint, method, path, body, readTimeout, cancellation, true);
    }

    private Object exchange(Endpoint endpoint, String method, String path, Map<String, Object> body,
                            int readTimeout, CancellationToken cancellation, boolean expectJson)
        throws OperationException {
        cancellation.throwIfCancelled();
        String text;
        try (RawHttpRequest.Response response = open(endpoint, method, path, body, readTimeout, cancellation)) {
            text = readBody(response.body());
            if (response.status() != HTTP_OK) {
                throw httpFailure(response.status(), text);
            }
        } catch (IOException e) {
            throw ioFailure(e, cancellation);
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

    private RawHttpRequest.Response open(Endpoint endpoint, String method, String path, Map<String, Object> body,
                                         int readTimeout, CancellationToken cancellation)
        throws IOException, OperationException {
        byte[] bytes = body == null ? null : Json.write(body).getBytes(StandardCharsets.UTF_8);
        return RawHttpRequest.open(endpoint, method, path, bytes, connectTimeoutMillis, readTimeout, cancellation);
    }

    private static void readPullStream(InputStream in, ProgressListener<TransferProgress> listener,
                                       CancellationToken cancellation) throws IOException, OperationException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        try {
            StringBuilder line = new StringBuilder();
            int c;
            while (true) {
                c = reader.read();
                if (c == -1 || c == '\n') {
                    if (line.toString().trim().length() > 0 && handlePullLine(line.toString(), listener)) {
                        return;
                    }
                    if (c == -1) {
                        cancellation.throwIfCancelled();
                        throw new OperationException(ErrorKind.IO, "pull stream ended before success");
                    }
                    line.setLength(0);
                    cancellation.throwIfCancelled();
                } else {
                    if (line.length() >= MAX_LINE_CHARS) {
                        throw new OperationException(ErrorKind.NOT_OLLAMA, "pull status line too long");
                    }
                    line.append((char) c);
                }
            }
        } finally {
            reader.close();
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

    static String readBody(InputStream in) throws IOException, OperationException {
        if (in == null) {
            return "";
        }
        try {
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
        } finally {
            in.close();
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

    static OperationException ioFailure(IOException e, CancellationToken cancellation) {
        if (cancellation.isCancelled()) {
            return new OperationCancelledException("operation cancelled");
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
}
