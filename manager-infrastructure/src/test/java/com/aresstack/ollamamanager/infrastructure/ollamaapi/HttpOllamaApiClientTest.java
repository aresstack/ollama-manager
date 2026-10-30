package com.aresstack.ollamamanager.infrastructure.ollamaapi;

import com.aresstack.ollamamanager.application.common.CancellationToken;
import com.aresstack.ollamamanager.application.common.ErrorKind;
import com.aresstack.ollamamanager.application.common.OperationCancelledException;
import com.aresstack.ollamamanager.application.common.OperationException;
import com.aresstack.ollamamanager.application.common.TransferProgress;
import com.aresstack.ollamamanager.domain.model.InstalledModel;
import com.aresstack.ollamamanager.domain.model.LoadedModel;
import com.aresstack.ollamamanager.domain.model.ModelDetails;
import com.aresstack.ollamamanager.domain.model.ModelOptions;
import com.aresstack.ollamamanager.domain.server.Endpoint;
import com.aresstack.ollamamanager.infrastructure.json.Json;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class HttpOllamaApiClientTest {

    private static final String TAGS = "{\"models\":[{\"name\":\"llama3.1:8b\",\"model\":\"llama3.1:8b\","
        + "\"modified_at\":\"2026-10-01T10:00:00Z\",\"size\":4920753328,\"digest\":\"46e0c10c039e\","
        + "\"details\":{\"format\":\"gguf\",\"family\":\"llama\",\"parameter_size\":\"8.0B\","
        + "\"quantization_level\":\"Q4_K_M\"}}]}";

    private static final String PS = "{\"models\":[{\"name\":\"llama3.1:8b\",\"model\":\"llama3.1:8b\","
        + "\"size\":6000000000,\"digest\":\"46e0c10c039e\",\"details\":{\"quantization_level\":\"Q4_K_M\"},"
        + "\"expires_at\":\"2026-10-30T18:00:00Z\",\"size_vram\":4500000000,\"context_length\":8192}]}";

    private FakeOllamaServer server;
    private HttpOllamaApiClient client;
    private Endpoint endpoint;

    @Before
    public void setUp() throws Exception {
        server = new FakeOllamaServer();
        endpoint = server.endpoint();
        client = new HttpOllamaApiClient(2000, 2000, 2000);
    }

    @After
    public void tearDown() {
        server.close();
    }

    @Test
    public void readsVersion() throws Exception {
        server.json("GET", "/api/version", 200, "{\"version\":\"0.12.6\"}");
        assertEquals("0.12.6", client.version(endpoint, CancellationToken.none()));
    }

    @Test
    public void ignoresConfiguredProxyForOllamaRequests() throws Exception {
        server.json("GET", "/api/version", 200, "{\"version\":\"0.12.6\"}");
        server.json("DELETE", "/api/delete", 200, "");
        ProxySelector previous = ProxySelector.getDefault();
        AtomicInteger proxyLookups = new AtomicInteger();
        ProxySelector.setDefault(new ProxySelector() {
            @Override
            public List<Proxy> select(URI uri) {
                proxyLookups.incrementAndGet();
                return Collections.singletonList(new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", 9)));
            }

            @Override
            public void connectFailed(URI uri, SocketAddress address, IOException e) {
            }
        });
        try {
            assertEquals("0.12.6", client.version(endpoint, CancellationToken.none()));
            client.delete(endpoint, "x", CancellationToken.none());
        } finally {
            ProxySelector.setDefault(previous);
        }
        assertEquals(0, proxyLookups.get());
    }

    @Test
    public void readsInstalledModelsWithDigestAndQuantization() throws Exception {
        server.json("GET", "/api/tags", 200, TAGS);
        List<InstalledModel> models = client.installedModels(endpoint, CancellationToken.none());
        assertEquals(1, models.size());
        InstalledModel model = models.get(0);
        assertEquals("llama3.1:8b", model.identity().name());
        assertEquals("46e0c10c039e", model.identity().digest());
        assertEquals("Q4_K_M", model.identity().quantization());
        assertEquals(4920753328L, model.sizeBytes());
        assertEquals("llama", model.family());
        assertEquals("8.0B", model.parameterSize());
    }

    @Test
    public void readsLoadedModelsWithReportedPlacement() throws Exception {
        server.json("GET", "/api/ps", 200, PS);
        LoadedModel model = client.loadedModels(endpoint, CancellationToken.none()).get(0);
        assertEquals(6000000000L, model.sizeBytes());
        assertEquals(4500000000L, model.vramBytes());
        assertEquals(Integer.valueOf(8192), model.contextLength());
        assertEquals("46e0c10c039e", model.identity().digest());
    }

    @Test
    public void emptyModelListsAreEmpty() throws Exception {
        server.json("GET", "/api/ps", 200, "{\"models\":[]}");
        assertTrue(client.loadedModels(endpoint, CancellationToken.none()).isEmpty());
    }

    @Test
    public void showReadsDetailsWithoutDigest() throws Exception {
        server.json("POST", "/api/show", 200, "{\"modelfile\":\"FROM x\",\"details\":{\"format\":\"gguf\","
            + "\"family\":\"llama\",\"parameter_size\":\"8.0B\",\"quantization_level\":\"Q4_K_M\"},"
            + "\"model_info\":{\"general.architecture\":\"llama\",\"llama.context_length\":131072}}");
        ModelDetails details = client.show(endpoint, "llama3.1:8b", CancellationToken.none());
        assertEquals("llama3.1:8b", details.name());
        assertEquals("Q4_K_M", details.quantizationLevel());
        assertEquals(Integer.valueOf(131072), details.contextLength());
        assertEquals("llama3.1:8b", body(server.last()).get("model"));
    }

    @Test
    public void pullStreamsChunkedProgressUntilSuccess() throws Exception {
        server.on("POST", "/api/pull", (exchange, body) -> FakeOllamaServer.streamChunks(exchange,
            "{\"status\":\"pulling manifest\"}\n",
            "{\"status\":\"pulling 6a0746a1ec1a\",\"digest\":\"sha256:6a07\",\"total\":1000,\"comp",
            "leted\":250}\n{\"status\":\"pulling 6a0746a1ec1a\",\"digest\":\"sha256:6a07\",\"total\":1000,"
                + "\"completed\":1000}\n",
            "{\"status\":\"verifying sha256 digest\"}\n{\"status\":\"success\"}\n"));
        List<TransferProgress> progress = new CopyOnWriteArrayList<TransferProgress>();
        client.pull(endpoint, "llama3.1:8b", progress::add, CancellationToken.none());

        assertEquals(5, progress.size());
        assertEquals(-1, progress.get(0).totalBytes());
        assertEquals(250, progress.get(1).completedBytes());
        assertEquals(1000, progress.get(1).totalBytes());
        assertEquals("success", progress.get(4).phase());
        assertEquals(Boolean.TRUE, body(server.last()).get("stream"));
    }

    @Test
    public void pullReportsErrorLine() throws Exception {
        server.on("POST", "/api/pull", (exchange, body) -> FakeOllamaServer.streamChunks(exchange,
            "{\"status\":\"pulling manifest\"}\n{\"error\":\"pull model manifest: file does not exist\"}\n"));
        try {
            client.pull(endpoint, "missing:1b", progress -> { }, CancellationToken.none());
            fail();
        } catch (OperationException e) {
            assertEquals(ErrorKind.HTTP_ERROR, e.kind());
            assertTrue(e.getMessage().contains("file does not exist"));
        }
    }

    @Test
    public void pullFailsWhenStreamEndsWithoutSuccess() throws Exception {
        server.on("POST", "/api/pull", (exchange, body) -> FakeOllamaServer.streamChunks(exchange,
            "{\"status\":\"pulling manifest\"}\n"));
        assertKind(ErrorKind.IO, () -> client.pull(endpoint, "x", p -> { }, CancellationToken.none()));
    }

    @Test
    public void pullCancellationAbortsBlockedStream() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        server.on("POST", "/api/pull", (exchange, body) -> {
            exchange.sendResponseHeaders(200, 0);
            OutputStream out = exchange.getResponseBody();
            out.write("{\"status\":\"pulling manifest\"}\n".getBytes(StandardCharsets.UTF_8));
            out.flush();
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        CancellationToken token = new CancellationToken();
        HttpOllamaApiClient slowClient = new HttpOllamaApiClient(2000, 2000, 30000);
        long start = System.nanoTime();
        try {
            slowClient.pull(endpoint, "x", progress -> token.cancel(), token);
            fail();
        } catch (OperationCancelledException e) {
            assertEquals(ErrorKind.CANCELLED, e.kind());
        } finally {
            release.countDown();
        }
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 5000);
    }

    @Test
    public void cancellationFromAnotherThreadAbortsBlockedRead() throws Exception {
        CountDownLatch requestReceived = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        server.on("POST", "/api/pull", (exchange, body) -> {
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().flush();
            requestReceived.countDown();
            try {
                release.await(20, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        CancellationToken token = new CancellationToken();
        HttpOllamaApiClient slowClient = new HttpOllamaApiClient(2000, 2000, 30000);
        Thread canceller = new Thread(() -> {
            try {
                requestReceived.await(5, TimeUnit.SECONDS);
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            token.cancel();
        });
        canceller.start();
        long start = System.nanoTime();
        try {
            slowClient.pull(endpoint, "x", progress -> { }, token);
            fail();
        } catch (OperationCancelledException e) {
            assertEquals(ErrorKind.CANCELLED, e.kind());
        } finally {
            release.countDown();
        }
        canceller.join(5000);
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 5000);
    }

    @Test
    public void pullStreamsManyLinesWithoutBufferingLimit() throws Exception {
        int lines = 20000;
        server.on("POST", "/api/pull", (exchange, body) -> {
            exchange.sendResponseHeaders(200, 0);
            OutputStream out = exchange.getResponseBody();
            for (int i = 1; i <= lines; i++) {
                out.write(("{\"status\":\"pulling abc\",\"total\":" + lines + ",\"completed\":" + i + "}\n")
                    .getBytes(StandardCharsets.UTF_8));
            }
            out.write("{\"status\":\"success\"}\n".getBytes(StandardCharsets.UTF_8));
            out.close();
        });
        AtomicInteger updates = new AtomicInteger();
        client.pull(endpoint, "x", progress -> updates.incrementAndGet(), CancellationToken.none());
        assertEquals(lines + 1, updates.get());
    }

    @Test
    public void overlongPullLineIsRejected() {
        server.on("POST", "/api/pull", (exchange, body) -> {
            exchange.sendResponseHeaders(200, 0);
            OutputStream out = exchange.getResponseBody();
            byte[] filler = new byte[64 * 1024];
            Arrays.fill(filler, (byte) 'a');
            for (int i = 0; i < 20; i++) {
                out.write(filler);
            }
            out.close();
        });
        assertKind(ErrorKind.NOT_OLLAMA, () -> client.pull(endpoint, "x", p -> { }, CancellationToken.none()));
    }

    @Test
    public void oversizedBodyIsRejected() {
        server.on("GET", "/api/tags", (exchange, body) -> {
            exchange.sendResponseHeaders(200, 0);
            OutputStream out = exchange.getResponseBody();
            byte[] filler = new byte[1024 * 1024];
            try {
                for (int i = 0; i < 20; i++) {
                    out.write(filler);
                }
                out.close();
            } catch (IOException ignored) {
                // The client stops reading at the limit.
            }
        });
        assertKind(ErrorKind.NOT_OLLAMA, () -> client.installedModels(endpoint, CancellationToken.none()));
    }

    @Test
    public void parallelRequestsAreIndependent() throws Exception {
        server.on("GET", "/api/version", (exchange, body) -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            FakeOllamaServer.respond(exchange, 200, "{\"version\":\"0.12.6\"}");
        });
        server.on("POST", "/api/pull", (exchange, body) -> {
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().flush();
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        CancellationToken pullToken = new CancellationToken();
        ExecutorService pool = Executors.newFixedThreadPool(9);
        try {
            Future<?> pull = pool.submit(() -> {
                try {
                    client.pull(endpoint, "x", p -> { }, pullToken);
                    return null;
                } catch (OperationException e) {
                    return e.kind();
                }
            });
            List<Future<String>> versions = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                versions.add(pool.submit(() -> client.version(endpoint, CancellationToken.none())));
            }
            for (Future<String> version : versions) {
                assertEquals("0.12.6", version.get(5, TimeUnit.SECONDS));
            }
            pullToken.cancel();
            assertEquals(ErrorKind.CANCELLED, pull.get(5, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    public void cancelledTokenSendsNoRequest() {
        CancellationToken token = new CancellationToken();
        token.cancel();
        assertKind(ErrorKind.CANCELLED, () -> client.version(endpoint, token));
        assertTrue(server.requests.isEmpty());
    }

    @Test
    public void preloadSendsKeepAliveAndOptions() throws Exception {
        server.json("POST", "/api/generate", 200, "{\"model\":\"llama3.1:8b\",\"response\":\"\",\"done\":true}");
        client.preload(endpoint, "llama3.1:8b", new ModelOptions(8192, null, null, null), "30m",
            CancellationToken.none());
        Map<String, Object> request = body(server.last());
        assertEquals("30m", request.get("keep_alive"));
        assertEquals(Boolean.FALSE, request.get("stream"));
        assertEquals(8192L, ((Map<?, ?>) request.get("options")).get("num_ctx"));
        assertNull(request.get("prompt"));
    }

    @Test
    public void unloadSendsKeepAliveZero() throws Exception {
        server.json("POST", "/api/generate", 200, "{\"done\":true,\"done_reason\":\"unload\"}");
        client.unload(endpoint, "llama3.1:8b", CancellationToken.none());
        assertEquals(0L, body(server.last()).get("keep_alive"));
    }

    @Test
    public void deleteSendsBodyWithDeleteMethod() throws Exception {
        server.json("DELETE", "/api/delete", 200, "");
        client.delete(endpoint, "llama3.1:8b", CancellationToken.none());
        assertEquals("DELETE", server.last().method);
        assertEquals("llama3.1:8b", body(server.last()).get("model"));
    }

    @Test
    public void deleteOfUnknownModelIsNotFound() {
        server.json("DELETE", "/api/delete", 404, "{\"error\":\"model 'x:latest' not found\"}");
        OperationException e = assertKind(ErrorKind.NOT_FOUND,
            () -> client.delete(endpoint, "x", CancellationToken.none()));
        assertTrue(e.getMessage().contains("not found"));
    }

    @Test
    public void mapsHttpFailures() {
        server.json("POST", "/api/show", 404, "{\"error\":\"model 'x' not found\"}");
        server.json("GET", "/api/ps", 503, "{\"error\":\"server busy\"}");
        server.json("GET", "/api/tags", 500, "oops");
        assertKind(ErrorKind.NOT_FOUND, () -> client.show(endpoint, "x", CancellationToken.none()));
        assertKind(ErrorKind.SERVICE_UNAVAILABLE, () -> client.loadedModels(endpoint, CancellationToken.none()));
        assertKind(ErrorKind.HTTP_ERROR, () -> client.installedModels(endpoint, CancellationToken.none()));
    }

    @Test
    public void nonOllamaAnswersAreRejected() {
        server.json("GET", "/api/version", 200, "<html>router login</html>");
        server.json("GET", "/api/tags", 200, "{\"models\":[{\"size\":1}]}");
        assertKind(ErrorKind.NOT_OLLAMA, () -> client.version(endpoint, CancellationToken.none()));
        assertKind(ErrorKind.NOT_OLLAMA, () -> client.installedModels(endpoint, CancellationToken.none()));
    }

    @Test
    public void slowServerTimesOut() {
        server.on("GET", "/api/version", (exchange, body) -> {
            try {
                Thread.sleep(1500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        HttpOllamaApiClient impatient = new HttpOllamaApiClient(1000, 300, 300);
        assertKind(ErrorKind.TIMEOUT, () -> impatient.version(endpoint, CancellationToken.none()));
    }

    @Test
    public void closedPortFailsToConnect() throws Exception {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        Endpoint closed = new Endpoint("127.0.0.1", port);
        assertKind(ErrorKind.CONNECTION_FAILED, () -> client.version(closed, CancellationToken.none()));
        assertKind(ErrorKind.CONNECTION_FAILED, () -> client.delete(closed, "x", CancellationToken.none()));
    }

    private interface Call {
        void run() throws OperationException;
    }

    private static OperationException assertKind(ErrorKind kind, Call call) {
        try {
            call.run();
        } catch (OperationException e) {
            assertEquals(e.getMessage(), kind, e.kind());
            return e;
        }
        throw new AssertionError("expected " + kind);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> body(FakeOllamaServer.Request request) {
        return (Map<String, Object>) Json.parse(request.body);
    }
}
