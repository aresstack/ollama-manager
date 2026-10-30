package com.aresstack.ollamamanager.infrastructure.distribution;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Local HTTP server for tests. Public https URLs are mapped to it through {@link #resolver()}.
 */
final class FakeHttpServer implements AutoCloseable {

    interface Handler {
        void handle(HttpExchange exchange) throws IOException;
    }

    private final HttpServer server;
    private final Map<String, Handler> routes = new ConcurrentHashMap<String, Handler>();
    final List<String> requests = Collections.synchronizedList(new ArrayList<String>());
    final List<String> rangeHeaders = Collections.synchronizedList(new ArrayList<String>());

    FakeHttpServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getRawPath();
            requests.add(path);
            String range = exchange.getRequestHeaders().getFirst("Range");
            if (range != null) {
                rangeHeaders.add(range);
            }
            Handler handler = routes.get(path);
            try {
                if (handler == null) {
                    respond(exchange, 404, "not found".getBytes(StandardCharsets.UTF_8));
                } else {
                    handler.handle(exchange);
                }
            } finally {
                exchange.close();
            }
        });
        server.start();
    }

    UrlResolver resolver() {
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        return url -> base + url.substring(url.indexOf('/', "https://".length()));
    }

    void route(String path, Handler handler) {
        routes.put(path, handler);
    }

    void bytes(String path, byte[] body) {
        route(path, exchange -> respond(exchange, 200, body));
    }

    void text(String path, String body) {
        bytes(path, body.getBytes(StandardCharsets.UTF_8));
    }

    static void respond(HttpExchange exchange, int status, byte[] body) throws IOException {
        exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
        if (body.length > 0) {
            OutputStream out = exchange.getResponseBody();
            out.write(body);
            out.close();
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
