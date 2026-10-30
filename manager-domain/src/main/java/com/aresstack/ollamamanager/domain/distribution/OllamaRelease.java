package com.aresstack.ollamamanager.domain.distribution;

import com.aresstack.ollamamanager.domain.common.Preconditions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A published Ollama release and its assets.
 */
public final class OllamaRelease {

    private final String version;
    private final List<ReleaseAsset> assets;

    public OllamaRelease(String version, List<ReleaseAsset> assets) {
        this.version = Preconditions.requireNonBlank(version, "version");
        this.assets = Collections.unmodifiableList(new ArrayList<ReleaseAsset>(
            Preconditions.requireNonNull(assets, "assets")));
    }

    /** Return the release tag, for example {@code v0.12.6}. */
    public String version() {
        return version;
    }

    public List<ReleaseAsset> assets() {
        return assets;
    }

    /**
     * Return the asset for a package, or {@code null} if this release does not publish it.
     */
    public ReleaseAsset assetFor(DistributionPackage distributionPackage) {
        for (ReleaseAsset asset : assets) {
            if (asset.name().equals(distributionPackage.assetName())) {
                return asset;
            }
        }
        return null;
    }
}
