package com.aresstack.ollamamanager.domain.hardware;

import com.aresstack.ollamamanager.domain.common.Preconditions;

/**
 * A detected GPU. Unknown properties stay {@code null}; presence never implies Ollama uses it.
 */
public final class GpuDescriptor {

    private final GpuVendor vendor;
    private final String name;
    private final Long vramBytes;
    private final String driverVersion;
    private final boolean dedicated;

    public GpuDescriptor(GpuVendor vendor, String name, Long vramBytes, String driverVersion, boolean dedicated) {
        this.vendor = Preconditions.requireNonNull(vendor, "vendor");
        this.name = Preconditions.requireNonBlank(name, "name");
        if (vramBytes != null) {
            Preconditions.requireNonNegative(vramBytes, "vramBytes");
        }
        this.vramBytes = vramBytes;
        this.driverVersion = driverVersion;
        this.dedicated = dedicated;
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

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof GpuDescriptor)) {
            return false;
        }
        GpuDescriptor that = (GpuDescriptor) other;
        return vendor == that.vendor && name.equals(that.name) && dedicated == that.dedicated
            && (vramBytes == null ? that.vramBytes == null : vramBytes.equals(that.vramBytes))
            && (driverVersion == null ? that.driverVersion == null : driverVersion.equals(that.driverVersion));
    }

    @Override
    public int hashCode() {
        int result = vendor.hashCode();
        result = 31 * result + name.hashCode();
        result = 31 * result + (vramBytes == null ? 0 : vramBytes.hashCode());
        return 31 * result + (driverVersion == null ? 0 : driverVersion.hashCode());
    }

    @Override
    public String toString() {
        return vendor + " " + name + (vramBytes == null ? "" : " " + vramBytes + "B")
            + (driverVersion == null ? "" : " driver " + driverVersion);
    }
}
