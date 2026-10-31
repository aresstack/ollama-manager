package com.aresstack.ollamamanager.domain.hardware;

import com.aresstack.ollamamanager.domain.common.Preconditions;

/**
 * A detected GPU. Unknown properties stay {@code null}; presence never implies Ollama uses it.
 * An unknown compute capability must never be presented as confirmed GPU support.
 */
public final class GpuDescriptor {

    private final GpuVendor vendor;
    private final String name;
    private final Long vramBytes;
    private final String driverVersion;
    private final boolean dedicated;
    private final String computeCapability;

    public GpuDescriptor(GpuVendor vendor, String name, Long vramBytes, String driverVersion, boolean dedicated) {
        this(vendor, name, vramBytes, driverVersion, dedicated, null);
    }

    /**
     * @param computeCapability NVIDIA compute capability such as {@code 12.0}, or {@code null} if unknown
     */
    public GpuDescriptor(GpuVendor vendor, String name, Long vramBytes, String driverVersion, boolean dedicated,
                         String computeCapability) {
        this.vendor = Preconditions.requireNonNull(vendor, "vendor");
        this.name = Preconditions.requireNonBlank(name, "name");
        if (vramBytes != null) {
            Preconditions.requireNonNegative(vramBytes, "vramBytes");
        }
        this.vramBytes = vramBytes;
        this.driverVersion = driverVersion;
        this.dedicated = dedicated;
        if (computeCapability != null && !computeCapability.trim().matches("\\d+\\.\\d+")) {
            throw new IllegalArgumentException("computeCapability must look like 8.9: " + computeCapability);
        }
        this.computeCapability = computeCapability == null ? null : computeCapability.trim();
    }

    public GpuVendor vendor() {
        return vendor;
    }

    public String name() {
        return name;
    }

    /** Return dedicated video memory in bytes, or {@code null} if unknown. */
    public Long vramBytes() {
        return vramBytes;
    }

    /** Return the driver version, or {@code null} if unknown. */
    public String driverVersion() {
        return driverVersion;
    }

    public boolean isDedicated() {
        return dedicated;
    }

    /** Return the compute capability such as {@code 12.0}, or {@code null} if unknown. */
    public String computeCapability() {
        return computeCapability;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof GpuDescriptor)) {
            return false;
        }
        GpuDescriptor that = (GpuDescriptor) other;
        return vendor == that.vendor && name.equals(that.name) && dedicated == that.dedicated
            && (vramBytes == null ? that.vramBytes == null : vramBytes.equals(that.vramBytes))
            && (driverVersion == null ? that.driverVersion == null : driverVersion.equals(that.driverVersion))
            && (computeCapability == null ? that.computeCapability == null
                : computeCapability.equals(that.computeCapability));
    }

    @Override
    public int hashCode() {
        int result = vendor.hashCode();
        result = 31 * result + name.hashCode();
        result = 31 * result + (vramBytes == null ? 0 : vramBytes.hashCode());
        result = 31 * result + (driverVersion == null ? 0 : driverVersion.hashCode());
        return 31 * result + (computeCapability == null ? 0 : computeCapability.hashCode());
    }

    @Override
    public String toString() {
        return vendor + " " + name + (vramBytes == null ? "" : " " + vramBytes + "B")
            + (driverVersion == null ? "" : " driver " + driverVersion);
    }
}
