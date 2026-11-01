package com.aresstack.ollamamanager.swing.section;

import com.aresstack.askai.java8.config.AppConfigurationRepository;
import com.aresstack.askai.java8.service.AskAiService;
import com.aresstack.askai.java8.service.DefaultAskAiService;
import com.aresstack.ollamamanager.domain.server.Endpoint;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * End-to-end check of the installer taken over from askai-java8: it targets the running server's endpoint and
 * installs a local GGUF through {@code /api/blobs} and {@code /api/create}. Uses a fake Ollama on a random port,
 * never a real server.
 */
public class ModelInstallerTest {

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    private HttpServer server;
    private final List<String> requests = Collections.synchronizedList(new ArrayList<String>());
    private final AtomicReference<String> createBody = new AtomicReference<String>();

    @Before
    public void startFakeOllama() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    @After
    public void stop() {
        server.stop(0);
    }

    private Endpoint endpoint() {
        return new Endpoint("127.0.0.1", server.getAddress().getPort());
    }

    @Test
    public void installsLocalGgufOnTheRunningServerThroughBlobsAndCreate() throws Exception {
        File configuration = new File(folder.getRoot(), "model-install/askai-java8.properties");
        ModelInstallConfiguration.targetServer(configuration, endpoint());
        AppConfigurationRepository repository = new AppConfigurationRepository(configuration);
        assertEquals(endpoint().baseUrl(), repository.load().getOllamaBaseUrl());

        File gguf = folder.newFile("tiny-Q4_K_M.gguf");
        try (OutputStream out = new FileOutputStream(gguf)) {
            out.write(minimalGguf());
        }
        DefaultAskAiService service = new DefaultAskAiService(repository);
        final CountDownLatch done = new CountDownLatch(1);
        final AtomicReference<String> result = new AtomicReference<String>();
        final AtomicReference<Exception> failure = new AtomicReference<Exception>();
        final List<String> phases = Collections.synchronizedList(new ArrayList<String>());
        try {
            service.installGgufFileWithCompanions("tiny:test", gguf, Collections.<File>emptyList(),
                new AskAiService.InstallListener() {
                    @Override
                    public void onProgress(String phase, long completed, long total) {
                        phases.add(phase);
                    }

                    @Override
                    public void onComplete(String message) {
                        result.set(message);
                        done.countDown();
                    }

                    @Override
                    public void onError(Exception ex) {
                        failure.set(ex);
                        done.countDown();
                    }
                });
            assertTrue("install did not finish", done.await(20, TimeUnit.SECONDS));
        } finally {
            service.shutdown();
        }

        assertNull(String.valueOf(failure.get()), failure.get());
        assertEquals("Installed tiny:test on remote Ollama.", result.get());
        assertTrue(requests.toString(), requests.contains("POST /api/blobs"));
        assertTrue(requests.toString(), requests.contains("POST /api/create"));
        assertTrue(createBody.get(), createBody.get().contains("\"tiny:test\""));
        assertTrue(createBody.get(), createBody.get().contains("tiny-Q4_K_M.gguf"));
        assertTrue(phases.toString(), !phases.isEmpty());
    }

    @Test
    public void installSectionShowsAskAiInstallerForTheRunningServer() throws Exception {
        Assume.assumeFalse("needs a display", GraphicsEnvironment.isHeadless());
        final File configuration = new File(folder.getRoot(), "askai-java8.properties");
        final com.aresstack.ollamamanager.swing.shell.ConsoleServices services = TestServices.builder()
            .serverLifecycle(TestServices.runningLifecycle()).modelInstallConfiguration(configuration).build();
        final AtomicReference<InstallModelsPanel> panel = new AtomicReference<InstallModelsPanel>();
        SwingUtilities.invokeAndWait(() -> {
            panel.set(new InstallModelsPanel(services));
            panel.get().showCurrent();
        });
        assertEquals("Hugging Face", panel.get().installer().tabTitles().get(0));
        assertEquals("Ollama Library", panel.get().installer().tabTitles().get(1));
        assertEquals(TestServices.ENDPOINT.baseUrl(),
            new AppConfigurationRepository(configuration).load().getOllamaBaseUrl());
    }

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
        String body = read(exchange.getRequestBody());
        requests.add(method + " " + (path.startsWith("/api/blobs/") ? "/api/blobs" : path));
        try {
            if (path.startsWith("/api/blobs/")) {
                respond(exchange, "HEAD".equals(method) ? 404 : 201, "");
            } else if ("/api/create".equals(path)) {
                createBody.set(body);
                respond(exchange, 200, "{\"status\":\"parsing GGUF\"}\n{\"status\":\"success\"}\n");
            } else if ("/api/show".equals(path)) {
                respond(exchange, 200, "{\"capabilities\":[\"completion\"],\"details\":{\"format\":\"gguf\"},"
                    + "\"model_info\":{}}");
            } else if ("/api/version".equals(path)) {
                respond(exchange, 200, "{\"version\":\"0.12.6\"}");
            } else {
                respond(exchange, 404, "{}");
            }
        } finally {
            exchange.close();
        }
    }

    /** A valid GGUF v3 header without tensors and with one key: general.architecture = llama. */
    private static byte[] minimalGguf() {
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(256).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        buffer.put("GGUF".getBytes(StandardCharsets.US_ASCII)).putInt(3).putLong(0).putLong(1);
        byte[] key = "general.architecture".getBytes(StandardCharsets.UTF_8);
        byte[] value = "llama".getBytes(StandardCharsets.UTF_8);
        buffer.putLong(key.length).put(key).putInt(8).putLong(value.length).put(value);
        // Tensor data starts at the next 32-byte boundary after the header, so pad the file to it.
        int length = (buffer.position() + 31) / 32 * 32;
        byte[] bytes = new byte[length];
        int header = buffer.position();
        buffer.flip();
        buffer.get(bytes, 0, header);
        return bytes;
    }

    private static String read(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int n;
        while ((n = in.read(buffer)) > 0) {
            out.write(buffer, 0, n);
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            exchange.getResponseBody().write(bytes);
        }
    }
}
