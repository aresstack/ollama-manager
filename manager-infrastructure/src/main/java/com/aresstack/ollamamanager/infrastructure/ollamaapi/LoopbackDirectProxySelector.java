package com.aresstack.ollamamanager.infrastructure.ollamaapi;

import java.io.IOException;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Proxy policy for Ollama requests: loopback hosts are always reached directly, so a system or JVM proxy
 * can never intercept the local server. Every other host follows the JVM default {@link ProxySelector},
 * which keeps proxy support for remote endpoints.
 */
final class LoopbackDirectProxySelector extends ProxySelector {

    private static final List<Proxy> DIRECT = Collections.singletonList(Proxy.NO_PROXY);

    @Override
    public List<Proxy> select(URI uri) {
        if (uri == null || isLoopback(uri.getHost())) {
            return DIRECT;
        }
        ProxySelector fallback = ProxySelector.getDefault();
        if (fallback == null || fallback == this) {
            return DIRECT;
        }
        List<Proxy> proxies = fallback.select(uri);
        return proxies == null || proxies.isEmpty() ? DIRECT : proxies;
    }

    @Override
    public void connectFailed(URI uri, SocketAddress address, IOException e) {
        ProxySelector fallback = ProxySelector.getDefault();
        if (fallback != null && fallback != this && uri != null && !isLoopback(uri.getHost())) {
            fallback.connectFailed(uri, address, e);
        }
    }

    static boolean isLoopback(String host) {
        if (host == null) {
            return false;
        }
        String normalized = host.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("[") && normalized.endsWith("]")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        return normalized.equals("localhost") || normalized.startsWith("127.") || normalized.equals("::1")
            || normalized.equals("0:0:0:0:0:0:0:1");
    }
}
