package com.aresstack.ollamamanager.domain.server;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EndpointTest {

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
