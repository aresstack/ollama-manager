package com.aresstack.ollamamanager.infrastructure.ollamaapi;

import org.junit.After;
import org.junit.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LoopbackDirectProxySelectorTest {

    private static final Proxy CORPORATE = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("10.0.0.1", 3128));

    private final ProxySelector previous = ProxySelector.getDefault();
    private final LoopbackDirectProxySelector selector = new LoopbackDirectProxySelector();

    @After
    public void restore() {
        ProxySelector.setDefault(previous);
    }

    @Test
    public void loopbackIsAlwaysDirect() {
        installDefault();
        for (String url : new String[] {"http://127.0.0.1:11434/", "http://localhost:11434/", "http://[::1]:11434/",
            "http://127.0.0.2:8080/"}) {
            assertEquals(url, Collections.singletonList(Proxy.NO_PROXY), selector.select(URI.create(url)));
        }
    }

    @Test
    public void remoteHostsFollowJvmDefault() {
        installDefault();
        assertEquals(Collections.singletonList(CORPORATE), selector.select(URI.create("http://gpu-box:11434/")));
    }

    @Test
    public void recognizesLoopbackSpellings() {
        assertTrue(LoopbackDirectProxySelector.isLoopback("LOCALHOST"));
        assertTrue(LoopbackDirectProxySelector.isLoopback("0:0:0:0:0:0:0:1"));
        assertFalse(LoopbackDirectProxySelector.isLoopback("128.0.0.1"));
        assertFalse(LoopbackDirectProxySelector.isLoopback("localhost.example.com"));
    }

    private static void installDefault() {
        ProxySelector.setDefault(new ProxySelector() {
            @Override
            public List<Proxy> select(URI uri) {
                return Collections.singletonList(CORPORATE);
            }

            @Override
            public void connectFailed(URI uri, SocketAddress address, IOException e) {
            }
        });
    }
}
