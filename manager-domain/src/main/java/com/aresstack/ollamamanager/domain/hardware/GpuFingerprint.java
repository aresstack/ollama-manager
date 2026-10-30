package com.aresstack.ollamamanager.domain.hardware;

import com.aresstack.ollamamanager.domain.common.Preconditions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Stable textual key of the GPU set, used to scope measured profiles. Driver versions are part of the key.
 */
public final class GpuFingerprint {

    public static final GpuFingerprint CPU_ONLY = new GpuFingerprint("cpu-only");

    private final String value;

    public GpuFingerprint(String value) {
        this.value = Preconditions.requireNonBlank(value, "value");
    }

    public static GpuFingerprint of(List<GpuDescriptor> gpus) {
        List<String> parts = new ArrayList<String>();
        for (GpuDescriptor gpu : gpus) {
            if (gpu.isDedicated()) {
                parts.add(gpu.vendor() + "|" + gpu.name() + "|" + nullToDash(gpu.vramBytes()) + "|"
                    + nullToDash(gpu.driverVersion()));
            }
        }
        if (parts.isEmpty()) {
            return CPU_ONLY;
        }
        Collections.sort(parts);
        StringBuilder joined = new StringBuilder();
        for (String part : parts) {
            joined.append(joined.length() == 0 ? "" : ";").append(part);
        }
        return new GpuFingerprint(joined.toString());
    }

    private static String nullToDash(Object value) {
        return value == null ? "-" : value.toString();
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof GpuFingerprint && value.equals(((GpuFingerprint) other).value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }
}
