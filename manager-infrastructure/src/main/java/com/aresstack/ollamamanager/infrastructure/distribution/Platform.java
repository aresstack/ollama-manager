package com.aresstack.ollamamanager.infrastructure.distribution;

import java.util.Locale;

/**
 * Operating system and CPU architecture the portable distribution must match.
 */
public final class Platform {

    private final String osName;
    private final String arch;

    public Platform(String osName, String arch) {
        this.osName = osName == null ? "" : osName;
        this.arch = arch == null ? "" : arch;
    }

    public static Platform current() {
        return new Platform(System.getProperty("os.name"), System.getProperty("os.arch"));
    }

    /** Return whether the official Windows AMD64 ZIPs run here. ARM64 is not supported yet. */
    public boolean isWindowsX64() {
        String os = osName.toLowerCase(Locale.ROOT);
        String cpu = arch.toLowerCase(Locale.ROOT);
        return os.startsWith("windows") && (cpu.equals("amd64") || cpu.equals("x86_64"));
    }

    @Override
    public String toString() {
        return osName + "/" + arch;
    }
}
