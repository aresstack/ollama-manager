package com.aresstack.ollamamanager.infrastructure.ollamaapi;

import com.aresstack.ollamamanager.domain.server.Endpoint;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

/**
 * Local HTTP server that answers like Ollama with scripted responses and records every request.
 */
final class FakeOllamaServer implements AutoCloseable {

    interface Handler {
        void handle(HttpExchange exchange, String body) throws IOException;
    }

    static final class Request {
        final String method;
        final String path;
        final String body;

        Request(String method, String path, String body) {
            this.method = method;
            this.path = path;
            this.body = body;
        }
    }

    private final HttpServer server;
    private final Map<String, Handler> handlers = new ConcurrentHashMap<String, Handler>();
    final List<Request> requests = new CopyOnWriteArrayList<Request>();

    FakeOllamaServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/", exchange -> {
            try {
                String body = read(exchange.getRequestBody());
                String path = exchange.getRequestURI().getPath();
                requests.add(new Request(exchange.getRequestMethod(), path, body));
                Handler handler = handlers.get(exchange.getRequestMethod() + " " + path);
                if (handler == null) {
                    respond(exchange, 404, "{\"error\":\"no route\"}");
                } else {
                    handler.handle(exchange, body);
                }
            } finally {
                exchange.close();
            }
        });
        server.start();
    }

    Endpoint endpoint() {
        return new Endpoint("127.0.0.1", server.getAddress().getPort());
    }

    FakeOllamaServer on(String method, String path, Handler handler) {
        handlers.put(method + " " + path, handler);
        return this;
    }

    FakeOllamaServer json(String method, String path, int status, String body) {
        return on(method, path, (exchange, request) -> respond(exchange, status, body));
    }

    Request last() {
        return requests.get(requests.size() - 1);
    }

    static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            OutputStream out = exchange.getResponseBody();
            out.write(bytes);
            out.close();
        }
    }

    /** Send lines with chunked transfer encoding, flushing after each piece. */
    static void streamChunks(HttpExchange exchange, String... pieces) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/x-ndjson");
        exchange.sendResponseHeaders(200, 0);
        OutputStream out = exchange.getResponseBody();
        for (String piece : pieces) {
            out.write(piece.getBytes(StandardCharsets.UTF_8));
            out.flush();
        }
        out.close();
    }

    private static String read(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int n;
        while ((n = in.read(buffer)) != -1) {
            out.write(buffer, 0, n);
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
