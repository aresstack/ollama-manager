package com.aresstack.ollamamanager.infrastructure.runtime;

import com.aresstack.ollamamanager.domain.server.Endpoint;
import com.sun.net.httpserver.HttpServer;
import org.junit.Test;

import java.io.OutputStream;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;

public class HttpEndpointProbeTest {

    @Test
    public void classifiesResponses() {
        assertEquals(ProbeResult.Kind.OLLAMA, HttpEndpointProbe.classify("{\"version\":\"0.12.3\"}").kind());
        assertEquals("0.12.3", HttpEndpointProbe.classify("{\"version\":\"0.12.3\"}").version());
        assertEquals(ProbeResult.Kind.FOREIGN, HttpEndpointProbe.classify("{\"name\":\"x\"}").kind());
        assertEquals(ProbeResult.Kind.FOREIGN, HttpEndpointProbe.classify("<html></html>").kind());
    }

    @Test
    public void onlyExplicitRefusalMeansFree() {
        Endpoint loopback = new Endpoint("127.0.0.1", 11434);
        assertEquals(ProbeResult.Kind.FREE, new HttpEndpointProbe((endpoint, timeout) -> {
            throw new ConnectException("Connection refused");
        }).probe(loopback, 100).kind());
        assertEquals(ProbeResult.Kind.FOREIGN, new HttpEndpointProbe((endpoint, timeout) -> {
            throw new SocketTimeoutException("connect timed out");
        }).probe(loopback, 100).kind());
        assertEquals(ProbeResult.Kind.FOREIGN, new HttpEndpointProbe((endpoint, timeout) -> {
            throw new SocketException("Network is unreachable");
        }).probe(loopback, 100).kind());
    }

    @Test
    public void silentListenerIsForeign() throws Exception {
        try (ServerSocket silent = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))) {
            Endpoint endpoint = new Endpoint("127.0.0.1", silent.getLocalPort());
            ProbeResult result = new HttpEndpointProbe().probe(endpoint, 300);
            assertEquals(ProbeResult.Kind.FOREIGN, result.kind());
        }
    }

    @Test
    public void probesRealSockets() throws Exception {
        int freePort;
        try (ServerSocket socket = new ServerSocket(0)) {
            freePort = socket.getLocalPort();
        }
        HttpEndpointProbe probe = new HttpEndpointProbe();
        assertEquals(ProbeResult.Kind.FREE, probe.probe(new Endpoint("127.0.0.1", freePort), 1000).kind());

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/version", exchange -> {
            byte[] body = "{\"version\":\"0.12.3\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        try {
            Endpoint endpoint = new Endpoint("127.0.0.1", server.getAddress().getPort());
            assertEquals("0.12.3", probe.probe(endpoint, 1000).version());
            server.removeContext("/api/version");
            assertEquals(ProbeResult.Kind.FOREIGN, probe.probe(endpoint, 1000).kind());
        } finally {
            server.stop(0);
        }
    }
}
