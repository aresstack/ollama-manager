package com.aresstack.ollamamanager.application.hardware;

import com.aresstack.ollamamanager.domain.hardware.GpuDescriptor;
import com.aresstack.ollamamanager.domain.hardware.GpuVendor;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Versioned, name-based compatibility rules derived from https://docs.ollama.com/gpu for Windows.
 * <p>
 * NVIDIA: compute capability 5.0+ (Maxwell and newer) with driver 531+; Blackwell (RTX 50xx) needs 570+.
 * Compute capability is inferred from the product name, so unmatched names stay {@link GpuSupport#UNKNOWN}.
 * Windows driver versions such as {@code 32.0.15.7688} are normalised to the NVIDIA release {@code 576.88}.
 * AMD: only the Windows ROCm list (not the longer Linux list) from the docs counts as supported; other Radeon models stay UNKNOWN
 * and can be enabled through a manual override.
 */
public final class GpuCompatibilityRules {

    public static final String VERSION = "ollama-docs-gpu-2026-10-windows";

    static final int NVIDIA_MIN_DRIVER = 531;
    static final int NVIDIA_BLACKWELL_MIN_DRIVER = 570;

    private static final List<String> AMD_WINDOWS_ROCM = Collections.unmodifiableList(Arrays.asList(
        "rx 7900 xtx", "rx 7900 xt", "rx 7900 gre", "rx 7800 xt", "rx 7700 xt", "rx 7600 xt", "rx 7600",
        "pro w7900", "pro w7800", "pro w7700", "pro w7600", "pro w7500"));

    private static final Pattern GTX_MODEL = Pattern.compile("\\bgtx\\s*(\\d{3,4})\\b");
    private static final Pattern GT_MODEL = Pattern.compile("\\bgt\\s*(\\d{3,4})\\b");
    private static final Pattern RTX_50 = Pattern.compile("\\brtx\\s*50\\d\\d\\b");
    private static final Pattern DRIVER_MAJOR = Pattern.compile("^(\\d+)");
    private static final Pattern WINDOWS_DRIVER = Pattern.compile("^\\d+\\.\\d+\\.(\\d+)\\.(\\d{4})$");

    public GpuAssessment assess(GpuDescriptor gpu) {
        if (gpu.vendor() == GpuVendor.NVIDIA) {
            return assessNvidia(gpu);
        }
        if (gpu.vendor() == GpuVendor.AMD) {
            return assessAmd(gpu);
        }
        return new GpuAssessment(gpu, GpuBackend.NONE, GpuSupport.UNSUPPORTED, false,
            "Ollama on Windows has no backend for " + gpu.vendor() + " GPUs; CPU is used.");
    }

    private GpuAssessment assessNvidia(GpuDescriptor gpu) {
        String name = gpu.name().toLowerCase(Locale.ROOT);
        Boolean capable = nvidiaComputeCapable(name);
        if (capable == null) {
            return new GpuAssessment(gpu, GpuBackend.CUDA, GpuSupport.UNKNOWN, false,
                "Compute capability of this NVIDIA model is not known to rules " + VERSION + ".");
        }
        if (!capable) {
            return new GpuAssessment(gpu, GpuBackend.CUDA, GpuSupport.UNSUPPORTED, false,
                "NVIDIA GPU older than compute capability 5.0; Ollama does not support it.");
        }
        int minDriver = RTX_50.matcher(name).find() ? NVIDIA_BLACKWELL_MIN_DRIVER : NVIDIA_MIN_DRIVER;
        Integer driver = driverMajor(gpu.driverVersion());
        if (driver == null) {
            return new GpuAssessment(gpu, GpuBackend.CUDA, GpuSupport.UNKNOWN, false,
                "Supported NVIDIA model, but the driver version is unknown (need " + minDriver + "+).");
        }
        if (driver < minDriver) {
            return new GpuAssessment(gpu, GpuBackend.CUDA, GpuSupport.DRIVER_UPDATE_REQUIRED, false,
                "Driver " + gpu.driverVersion() + " is too old; install NVIDIA driver " + minDriver
                    + " or newer manually.");
        }
        return new GpuAssessment(gpu, GpuBackend.CUDA, GpuSupport.SUPPORTED, false,
            "Supported NVIDIA GPU (compute capability 5.0+, driver " + gpu.driverVersion() + ").");
    }

    /** Return true for Maxwell+, false for older generations, null when the name is not recognised. */
    private static Boolean nvidiaComputeCapable(String name) {
        if (name.contains("rtx") || name.contains("titan v") || name.contains("titan x")) {
            return Boolean.TRUE;
        }
        Matcher gtx = GTX_MODEL.matcher(name);
        if (gtx.find()) {
            int model = Integer.parseInt(gtx.group(1));
            // GTX 745/750 are Maxwell; the rest of the 700 series and older are Kepler or Fermi.
            return Boolean.valueOf(model >= 900 || model == 745 || model == 750);
        }
        Matcher gt = GT_MODEL.matcher(name);
        if (gt.find()) {
            int model = Integer.parseInt(gt.group(1));
            return model >= 1000 ? Boolean.TRUE : null;
        }
        return null;
    }

    private GpuAssessment assessAmd(GpuDescriptor gpu) {
        String name = gpu.name().toLowerCase(Locale.ROOT);
        for (String model : AMD_WINDOWS_ROCM) {
            if (Pattern.compile("\\b" + Pattern.quote(model) + "\\b").matcher(name).find()) {
                return new GpuAssessment(gpu, GpuBackend.ROCM, GpuSupport.SUPPORTED, false,
                    "Listed for ROCm on Windows in rules " + VERSION + ".");
            }
        }
        if (!gpu.isDedicated()) {
            return new GpuAssessment(gpu, GpuBackend.ROCM, GpuSupport.UNSUPPORTED, false,
                "Integrated AMD graphics are not supported by Ollama ROCm on Windows.");
        }
        return new GpuAssessment(gpu, GpuBackend.ROCM, GpuSupport.UNKNOWN, false,
            "AMD GPU is not on the Windows ROCm list of rules " + VERSION
                + "; enable it manually only if you know it works.");
    }

    private static Integer driverMajor(String driverVersion) {
        if (driverVersion == null) {
            return null;
        }
        Matcher windows = WINDOWS_DRIVER.matcher(driverVersion.trim());
        if (windows.find()) {
            // The last five digits of the Windows version are the NVIDIA release: 32.0.15.7688 -> 576.88.
            String digits = windows.group(1) + windows.group(2);
            int end = digits.length();
            return end < 5 ? null : Integer.valueOf(digits.substring(end - 5, end - 2));
        }
        Matcher matcher = DRIVER_MAJOR.matcher(driverVersion.trim());
        return matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
    }

}
