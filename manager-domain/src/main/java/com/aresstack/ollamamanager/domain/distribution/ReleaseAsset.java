package com.aresstack.ollamamanager.domain.distribution;

import com.aresstack.ollamamanager.domain.common.Preconditions;

/**
 * One downloadable file of a release, with the SHA-256 from the release manifest when known.
 */
public final class ReleaseAsset {

    private final String name;
    private final String downloadUrl;
    private final long sizeBytes;
    private final String sha256;

    public ReleaseAsset(String name, String downloadUrl, long sizeBytes, String sha256) {
        this.name = Preconditions.requireNonBlank(name, "name");
        this.downloadUrl = Preconditions.requireNonBlank(downloadUrl, "downloadUrl");
        if (!this.downloadUrl.startsWith("https://")) {
            throw new IllegalArgumentException("downloadUrl must use https: " + downloadUrl);
        }
        this.sizeBytes = Preconditions.requireNonNegative(sizeBytes, "sizeBytes");
        if (sha256 != null && !sha256.matches("[0-9a-fA-F]{64}")) {
            throw new IllegalArgumentException("sha256 must be 64 hex characters: " + sha256);
        }
        this.sha256 = sha256 == null ? null : sha256.toLowerCase(java.util.Locale.ROOT);
    }

    public String name() {
        return name;
    }

    public String downloadUrl() {
        return downloadUrl;
    }

    public long sizeBytes() {
        return sizeBytes;
    }

    /** Return the expected lowercase SHA-256, or {@code null} if the manifest has not been read. */
    public String sha256() {
        return sha256;
    }

    public ReleaseAsset withSha256(String value) {
        return new ReleaseAsset(name, downloadUrl, sizeBytes, value);
    }
}
