package com.aresstack.ollamamanager.application.hardware;

import com.aresstack.ollamamanager.domain.hardware.GpuDescriptor;
import com.aresstack.ollamamanager.domain.hardware.GpuVendor;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Versioned compatibility rules for Windows, derived from https://docs.ollama.com/gpu and NVIDIA driver notes.
 * <p>
 * NVIDIA applies two independent minimums and requires the higher one:
 * <ul>
 *   <li><b>Ollama minimum driver</b> by compute capability: 7.0+ needs 550.00+, 5.0-6.2 needs 570.00+.</li>
 *   <li><b>Device-specific NVIDIA support</b>: the first driver that supports the product at all
 *   (RTX 5080/5090: 572.16). RTX 50 models without a verified entry stay {@link GpuSupport#UNKNOWN}.</li>
 * </ul>
 * Compute capability is inferred from the product name. This heuristic is a fallback with limited reliability;
 * unmatched names stay UNKNOWN and can be overridden manually. Versions are compared on major and minor;
 * Windows driver versions such as {@code 32.0.15.7688} are normalised to the NVIDIA release {@code 576.88}.
 * <p>
 * AMD: only the Windows ROCm list (not the longer Linux list) counts as supported; other Radeon models stay
 * UNKNOWN and can be enabled through a manual override.
 */
public final class GpuCompatibilityRules {

    public static final String VERSION = "ollama-docs-gpu-2026-10-30b-windows";

    /** Driver releases encoded as {@code major * 100 + minor}. */
    static final int OLLAMA_MIN_DRIVER = 55000;
    static final int OLLAMA_LEGACY_MIN_DRIVER = 57000;
    private static final int UNSUPPORTED_GENERATION = 0;

    private static final Map<Pattern, Integer> DEVICE_FIRST_DRIVER = deviceFirstDrivers();

    private static final List<String> AMD_WINDOWS_ROCM = Collections.unmodifiableList(Arrays.asList(
        "rx 7900 xtx", "rx 7900 xt", "rx 7900 gre", "rx 7800 xt", "rx 7700 xt", "rx 7600 xt", "rx 7600",
        "pro w7900", "pro w7800", "pro w7700", "pro w7600", "pro w7500"));

    private static final Pattern GTX_MODEL = Pattern.compile("\\bgtx\\s*(\\d{3,4})\\b");
    private static final Pattern GT_MODEL = Pattern.compile("\\bgt\\s*(\\d{3,4})\\b");
    private static final Pattern RTX_50 = Pattern.compile("\\brtx\\s*50\\d\\d\\b");
    private static final Pattern DRIVER_RELEASE = Pattern.compile("^(\\d+)(?:\\.(\\d{1,2}))?");
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

    private static Map<Pattern, Integer> deviceFirstDrivers() {
        Map<Pattern, Integer> drivers = new LinkedHashMap<Pattern, Integer>();
        // NVIDIA Game Ready 572.16 added GeForce RTX 5080 and 5090 (nvidia.com driver details 240823).
        drivers.put(Pattern.compile("\\brtx\\s*50[89]0\\b"), 57216);
        return Collections.unmodifiableMap(drivers);
    }

    private GpuAssessment assessNvidia(GpuDescriptor gpu) {
        String name = gpu.name().toLowerCase(Locale.ROOT);
        Integer ollamaMinimum = ollamaMinDriver(name);
        if (ollamaMinimum == null) {
            return new GpuAssessment(gpu, GpuBackend.CUDA, GpuSupport.UNKNOWN, false,
                "Compute capability of this NVIDIA model is not known to rules " + VERSION + ".");
        }
        if (ollamaMinimum == UNSUPPORTED_GENERATION) {
            return new GpuAssessment(gpu, GpuBackend.CUDA, GpuSupport.UNSUPPORTED, false,
                "NVIDIA GPU older than compute capability 5.0; Ollama does not support it.");
        }
        Integer deviceMinimum = deviceFirstDriver(name);
        if (RTX_50.matcher(name).find() && deviceMinimum == null) {
            return new GpuAssessment(gpu, GpuBackend.CUDA, GpuSupport.UNKNOWN, false,
                "No verified first NVIDIA driver for this RTX 50 model in rules " + VERSION
                    + "; enable it manually if it works.");
        }
        int required = deviceMinimum == null ? ollamaMinimum : Math.max(ollamaMinimum, deviceMinimum);
        String requirement = "Ollama minimum " + format(ollamaMinimum)
            + (deviceMinimum == null ? "" : ", device support from " + format(deviceMinimum));
        Integer driver = driverRelease(gpu.driverVersion());
        if (driver == null) {
            return new GpuAssessment(gpu, GpuBackend.CUDA, GpuSupport.UNKNOWN, false,
                "Supported NVIDIA model, but the driver version is unknown (" + requirement + ").");
        }
        if (driver < required) {
            return new GpuAssessment(gpu, GpuBackend.CUDA, GpuSupport.DRIVER_UPDATE_REQUIRED, false,
                "Driver " + format(driver) + " is too old (" + requirement + "); install NVIDIA driver "
                    + format(required) + " or newer manually.");
        }
        return new GpuAssessment(gpu, GpuBackend.CUDA, GpuSupport.SUPPORTED, false,
            "Supported NVIDIA GPU with driver " + format(driver) + " (" + requirement + ").");
    }

    private static Integer deviceFirstDriver(String name) {
        for (Map.Entry<Pattern, Integer> entry : DEVICE_FIRST_DRIVER.entrySet()) {
            if (entry.getKey().matcher(name).find()) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * Return the Ollama minimum driver for the model's compute capability, {@link #UNSUPPORTED_GENERATION}
     * below 5.0, or {@code null} when the name heuristic does not recognise the model.
     */
    private static Integer ollamaMinDriver(String name) {
        if (name.contains("rtx") || name.contains("titan v")) {
            return OLLAMA_MIN_DRIVER;
        }
        if (name.contains("titan x")) {
            return OLLAMA_LEGACY_MIN_DRIVER;
        }
        Matcher gtx = GTX_MODEL.matcher(name);
        if (gtx.find()) {
            int model = Integer.parseInt(gtx.group(1));
            if (model >= 1600 && model < 1700) {
                return OLLAMA_MIN_DRIVER;
            }
            // GTX 745/750, 9xx and 10xx are Maxwell or Pascal (5.0-6.2); the rest of 7xx and older are Kepler.
            return model >= 900 || model == 745 || model == 750 ? OLLAMA_LEGACY_MIN_DRIVER : UNSUPPORTED_GENERATION;
        }
        Matcher gt = GT_MODEL.matcher(name);
        if (gt.find() && Integer.parseInt(gt.group(1)) >= 1000) {
            return OLLAMA_LEGACY_MIN_DRIVER;
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

    /** Return the NVIDIA release as {@code major * 100 + minor}, or {@code null} if it cannot be read. */
    static Integer driverRelease(String driverVersion) {
        if (driverVersion == null) {
            return null;
        }
        Matcher windows = WINDOWS_DRIVER.matcher(driverVersion.trim());
        if (windows.find()) {
            // The last five digits of the Windows version are the NVIDIA release: 32.0.15.7688 -> 576.88.
            String digits = windows.group(1) + windows.group(2);
            return digits.length() < 5 ? null : Integer.valueOf(digits.substring(digits.length() - 5));
        }
        Matcher matcher = DRIVER_RELEASE.matcher(driverVersion.trim());
        if (!matcher.find()) {
            return null;
        }
        int minor = matcher.group(2) == null ? 0 : Integer.parseInt(matcher.group(2));
        return Integer.parseInt(matcher.group(1)) * 100 + minor;
    }

    static String format(int release) {
        return String.format(Locale.ROOT, "%d.%02d", release / 100, release % 100);
    }
}
