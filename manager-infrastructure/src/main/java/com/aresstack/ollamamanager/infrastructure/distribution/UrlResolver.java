package com.aresstack.ollamamanager.infrastructure.distribution;

/**
 * Maps a public https URL to the URL actually requested. Production uses {@link #identity()}; tests map
 * public hosts to a local fake server. TLS verification is never disabled.
 */
public interface UrlResolver {

    String resolve(String publicUrl);

    static UrlResolver identity() {
        return url -> url;
    }
}
