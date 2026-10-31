package com.aresstack.ollamamanager.infrastructure.runtime;

import com.aresstack.ollamamanager.domain.server.Endpoint;

/**
 * Checks what answers on an endpoint without assuming anything about ownership.
 */
public interface EndpointProbe {

    ProbeResult probe(Endpoint endpoint, int timeoutMillis);
}
