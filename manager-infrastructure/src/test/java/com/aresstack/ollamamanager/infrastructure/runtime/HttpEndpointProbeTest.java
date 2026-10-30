package com.aresstack.ollamamanager.infrastructure.runtime;

import com.aresstack.ollamamanager.domain.server.Endpoint;
import com.sun.net.httpserver.HttpServer;
import org.junit.Assume;
import org.junit.Test;

import java.io.IOException;

import java.io.OutputStream;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HttpEndpointProbeTest {

    @Test
    public void classifiesResponses() {
        assertEquals(ProbeResult.Kind.OLLAMA, HttpEndpointProbe.classify("{\"version\":\"0.12.3\"}").kind());
        assertEquals("0.12.3", HttpEndpointProbe.classify("{\"version\":\"0.12.3\"}").version());
        assertEquals(ProbeResult.Kind.FOREIGN, HttpEndpointProbe.classify("{\"name\":\"x\"}").kind());
        assertEquals(ProbeResult.Kind.FOREIGN, HttpEndpointProbe.classify("<html></html>").kind());
    }

    @Test
    public void distinguishesRefusalTimeoutAndErrors() {
        Endpoint loopback = new Endpoint("127.0.0.1", 11434);
        assertEquals(ProbeResult.Kind.FREE, probe(new ConnectException("refused"), true).probe(loopback, 100).kind());
        assertEquals(ProbeResult.Kind.FREE,
            probe(new SocketTimeoutException("connect timed out"), true).probe(loopback, 100).kind());
        assertEquals(ProbeResult.Kind.FOREIGN,
            probe(new SocketTimeoutException("connect timed out"), false).probe(loopback, 100).kind());
        assertEquals(ProbeResult.Kind.FOREIGN,
            probe(new SocketException("Network is unreachable"), true).probe(loopback, 100).kind());
    }

    @Test
    public void exclusiveBindDetectsOccupiedPortsForIpv4AndIpv6() throws Exception {
        assertBindCheck("127.0.0.1", "127.0.0.1");
        try (ServerSocket probe = new ServerSocket(0, 1, InetAddress.getByName("::1"))) {
            probe.getLocalPort();
        } catch (IOException e) {
            Assume.assumeNoException("IPv6 loopback unavailable", e);
        }
        assertBindCheck("::1", "[::1]");
    }

    @Test
    public void bindConflictAfterTimeoutFailsClosed() throws Exception {
        try (ServerSocket occupied = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))) {
            Endpoint endpoint = new Endpoint("127.0.0.1", occupied.getLocalPort());
            HttpEndpointProbe timingOut = new HttpEndpointProbe((target, timeout) -> {
                throw new SocketTimeoutException("connect timed out");
            }, HttpEndpointProbe::bindExclusively);
            assertEquals(ProbeResult.Kind.FOREIGN, timingOut.probe(endpoint, 100).kind());
        }
    }

    private static void assertBindCheck(String address, String host) throws IOException {
        int port;
        try (ServerSocket occupied = new ServerSocket(0, 50, InetAddress.getByName(address))) {
            port = occupied.getLocalPort();
            assertFalse(HttpEndpointProbe.bindExclusively(new Endpoint(host, port)));
        }
        assertTrue(HttpEndpointProbe.bindExclusively(new Endpoint(host, port)));
    }

    private static HttpEndpointProbe probe(IOException connectFailure, boolean bindable) {
        return new HttpEndpointProbe((endpoint, timeout) -> {
            throw connectFailure;
        }, endpoint -> bindable);
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
