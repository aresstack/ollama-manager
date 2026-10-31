package com.aresstack.ollamamanager.application.hardware;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Manual support decisions keyed by GPU name (case-insensitive), taking precedence over the rules.
 */
public final class SupportOverrides {

    private static final SupportOverrides NONE = new SupportOverrides(Collections.<String, GpuSupport>emptyMap());

    private final Map<String, GpuSupport> byName;

    private SupportOverrides(Map<String, GpuSupport> byName) {
        this.byName = byName;
    }

    public static SupportOverrides none() {
        return NONE;
    }

    public SupportOverrides with(String gpuName, GpuSupport support) {
        if (gpuName == null || gpuName.trim().isEmpty() || support == null) {
            throw new IllegalArgumentException("gpuName and support must be set");
        }
        Map<String, GpuSupport> copy = new LinkedHashMap<String, GpuSupport>(byName);
        copy.put(key(gpuName), support);
        return new SupportOverrides(Collections.unmodifiableMap(copy));
    }

    /** Return the forced support, or {@code null} if the user did not override this GPU. */
    public GpuSupport find(String gpuName) {
        return byName.get(key(gpuName));
    }

    private static String key(String gpuName) {
        return gpuName.trim().toLowerCase(Locale.ROOT);
    }
}
