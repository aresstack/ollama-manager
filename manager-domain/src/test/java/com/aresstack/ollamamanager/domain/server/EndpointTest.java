package com.aresstack.ollamamanager.domain.server;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EndpointTest {

    @Test
    public void parsesEveryOllamaHostForm() {
        assertEquals(new Endpoint("0.0.0.0", 11434), Endpoint.parseOllamaHost("0.0.0.0"));
        assertEquals(new Endpoint("127.0.0.1", 8080), Endpoint.parseOllamaHost(":8080"));
        assertEquals(new Endpoint("host", 9), Endpoint.parseOllamaHost("http://host:9/"));
        assertEquals(new Endpoint("[::1]", 11434), Endpoint.parseOllamaHost("[::1]"));
        assertEquals(new Endpoint("[::1]", 5), Endpoint.parseOllamaHost("[::1]:5"));
    }

    @Test
    public void wildcardBindIsReachedThroughLoopback() {
        assertEquals("http://127.0.0.1:11434", Endpoint.parse("0.0.0.0:11434").baseUrl());
        assertEquals("http://[::1]:11434", Endpoint.parse("[::]:11434").baseUrl());
        assertEquals("0.0.0.0:11434", Endpoint.parse("0.0.0.0:11434").toHostValue());
    }

    @Test
    public void parsesHostValue() {
        Endpoint endpoint = Endpoint.parse("127.0.0.1:11500");

        assertEquals("127.0.0.1", endpoint.host());
        assertEquals(11500, endpoint.port());
        assertEquals("http://127.0.0.1:11500", endpoint.baseUrl());
    }

    @Test
    public void detectsLoopback() {
        assertTrue(Endpoint.parse("localhost:1").isLoopback());
        assertFalse(Endpoint.parse("0.0.0.0:11434").isLoopback());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsMissingPort() {
        Endpoint.parse("127.0.0.1");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsPortOutOfRange() {
        new Endpoint("127.0.0.1", 70000);
    }
}
