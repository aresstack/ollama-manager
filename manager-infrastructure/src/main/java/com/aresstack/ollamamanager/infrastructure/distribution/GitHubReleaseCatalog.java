package com.aresstack.ollamamanager.infrastructure.distribution;

import com.aresstack.ollamamanager.application.common.CancellationToken;
import com.aresstack.ollamamanager.application.common.ErrorKind;
import com.aresstack.ollamamanager.application.common.OperationException;
import com.aresstack.ollamamanager.application.port.ReleaseCatalogPort;
import com.aresstack.ollamamanager.domain.distribution.OllamaRelease;
import com.aresstack.ollamamanager.domain.distribution.ReleaseAsset;
import com.aresstack.ollamamanager.infrastructure.json.Json;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Reads stable Ollama releases from the GitHub REST API and fills asset digests from {@code sha256sum.txt}.
 */
public final class GitHubReleaseCatalog implements ReleaseCatalogPort {

    public static final String DEFAULT_API = "https://api.github.com/repos/ollama/ollama";
    static final String MANIFEST_NAME = "sha256sum.txt";
    private static final Logger LOG = Logger.getLogger(GitHubReleaseCatalog.class.getName());
    private static final int MAX_JSON_BYTES = 4 * 1024 * 1024;
    private static final int MAX_MANIFEST_BYTES = 256 * 1024;

    private final HttpTransport http;
    private final String apiBase;

    public GitHubReleaseCatalog(HttpTransport http, String apiBase) {
        this.http = http;
        this.apiBase = apiBase.endsWith("/") ? apiBase.substring(0, apiBase.length() - 1) : apiBase;
    }

    public static GitHubReleaseCatalog defaults() {
        return new GitHubReleaseCatalog(HttpTransport.defaults(), DEFAULT_API);
    }

    @Override
    public OllamaRelease latestStable(CancellationToken cancellation) throws OperationException {
        return load(apiBase + "/releases/latest", cancellation);
    }

    @Override
    public OllamaRelease findRelease(String version, CancellationToken cancellation) throws OperationException {
        if (version == null || version.trim().isEmpty()) {
            throw new OperationException(ErrorKind.NOT_FOUND, "no version given");
        }
        try {
            return load(apiBase + "/releases/tags/" + URLEncoder.encode(version.trim(), "UTF-8"), cancellation);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private OllamaRelease load(String url, CancellationToken cancellation) throws OperationException {
        String body = http.getText(url, HttpTransport.headers(
            "Accept", "application/vnd.github+json", "X-GitHub-Api-Version", "2022-11-28"),
            MAX_JSON_BYTES, cancellation);
        Map<?, ?> release = asMap(parse(body, url), url);
        if (Boolean.TRUE.equals(release.get("draft")) || Boolean.TRUE.equals(release.get("prerelease"))) {
            throw new OperationException(ErrorKind.NOT_FOUND, "not a stable release: " + release.get("tag_name"));
        }
        Object tag = release.get("tag_name");
        if (!(tag instanceof String) || ((String) tag).trim().isEmpty()) {
            throw new OperationException(ErrorKind.HTTP_ERROR, "release without tag_name from " + url);
        }
        List<ReleaseAsset> assets = new ArrayList<ReleaseAsset>();
        Object rawAssets = release.get("assets");
        if (rawAssets instanceof List) {
            for (Object item : (List<?>) rawAssets) {
                ReleaseAsset asset = toAsset(item);
                if (asset != null) {
                    assets.add(asset);
                }
            }
        }
        return new OllamaRelease((String) tag, withDigests(assets, (String) tag, cancellation));
    }

    private List<ReleaseAsset> withDigests(List<ReleaseAsset> assets, String tag, CancellationToken cancellation)
        throws OperationException {
        ReleaseAsset manifest = null;
        for (ReleaseAsset asset : assets) {
            if (asset.name().equals(MANIFEST_NAME)) {
                manifest = asset;
            }
        }
        if (manifest == null) {
            LOG.warning("release " + tag + " has no " + MANIFEST_NAME + "; assets stay unverifiable");
            return assets;
        }
        Map<String, String> digests;
        try {
            digests = Sha256Manifest.parse(http.getText(manifest.downloadUrl(),
                HttpTransport.headers("Accept", "application/octet-stream"), MAX_MANIFEST_BYTES, cancellation));
        } catch (IllegalArgumentException e) {
            throw new OperationException(ErrorKind.HTTP_ERROR, "invalid " + MANIFEST_NAME + " for " + tag, e);
        }
        LOG.info("read " + digests.size() + " digests for " + tag + " from " + manifest.downloadUrl());
        List<ReleaseAsset> result = new ArrayList<ReleaseAsset>();
        for (ReleaseAsset asset : assets) {
            String digest = digests.get(asset.name());
            result.add(digest == null ? asset : asset.withSha256(digest));
        }
        return result;
    }

    private static ReleaseAsset toAsset(Object item) {
        if (!(item instanceof Map)) {
            return null;
        }
        Map<?, ?> map = (Map<?, ?>) item;
        Object name = map.get("name");
        Object url = map.get("browser_download_url");
        Object size = map.get("size");
        if (!(name instanceof String) || !(url instanceof String) || !((String) url).startsWith("https://")) {
            return null;
        }
        long bytes = size instanceof Number ? ((Number) size).longValue() : 0L;
        return new ReleaseAsset((String) name, (String) url, Math.max(0L, bytes), null);
    }

    private static Object parse(String body, String url) throws OperationException {
        try {
            return Json.parse(body);
        } catch (IllegalArgumentException e) {
            throw new OperationException(ErrorKind.HTTP_ERROR, "invalid JSON from " + url, e);
        }
    }

    private static Map<?, ?> asMap(Object value, String url) throws OperationException {
        if (!(value instanceof Map)) {
            throw new OperationException(ErrorKind.HTTP_ERROR, "expected a JSON object from " + url);
        }
        return (Map<?, ?>) value;
    }
}
