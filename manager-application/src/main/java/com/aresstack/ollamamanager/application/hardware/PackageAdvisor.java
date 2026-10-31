package com.aresstack.ollamamanager.application.hardware;

import com.aresstack.ollamamanager.domain.distribution.DistributionPackage;
import com.aresstack.ollamamanager.domain.hardware.GpuDescriptor;
import com.aresstack.ollamamanager.domain.hardware.HardwareSnapshot;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Turns a hardware snapshot into a safe package suggestion and validates user selections.
 * <p>
 * NVIDIA and CPU use the base package (CUDA is included). Supported AMD adds ROCm. MLX is only added when
 * explicitly requested and a supported NVIDIA GPU exists; it never replaces CUDA in the base package.
 */
public final class PackageAdvisor {

    static final String RUNTIME_NOTE = "A detected GPU is not proof of acceleration; GPU use is confirmed only"
        + " after a model is loaded (VRAM reported by /api/ps).";

    private final GpuCompatibilityRules rules;

    public PackageAdvisor(GpuCompatibilityRules rules) {
        if (rules == null) {
            throw new IllegalArgumentException("rules must not be null");
        }
        this.rules = rules;
    }

    public PackageRecommendation recommend(HardwareSnapshot snapshot, SupportOverrides overrides,
                                           boolean mlxRequested) {
        List<String> reasons = new ArrayList<String>();
        if (!isSupportedPlatform(snapshot)) {
            reasons.add("Portable Ollama packages are offered for Windows x64 only; detected "
                + snapshot.operatingSystem() + " " + snapshot.architecture() + ".");
            return new PackageRecommendation(false, EnumSet.noneOf(DistributionPackage.class), Confidence.HIGH,
                new ArrayList<GpuAssessment>(), reasons);
        }
        List<GpuAssessment> assessments = assessAll(snapshot, overrides);
        Set<DistributionPackage> packages = EnumSet.of(DistributionPackage.BASE);
        Confidence confidence = Confidence.HIGH;
        boolean nvidiaSupported = false;
        for (GpuAssessment assessment : assessments) {
            if (assessment.isOverridden()) {
                confidence = lower(confidence, Confidence.MEDIUM);
            }
            if (assessment.support() == GpuSupport.UNKNOWN
                || assessment.support() == GpuSupport.DRIVER_UPDATE_REQUIRED) {
                confidence = Confidence.LOW;
            }
            if (assessment.support() != GpuSupport.SUPPORTED) {
                continue;
            }
            if (assessment.backend() == GpuBackend.ROCM) {
                packages.add(DistributionPackage.ROCM);
            } else if (assessment.backend() == GpuBackend.CUDA) {
                nvidiaSupported = true;
            }
        }
        reasons.add("Base package: CPU runtime and NVIDIA CUDA support.");
        if (packages.contains(DistributionPackage.ROCM)) {
            reasons.add("ROCm add-on: a supported AMD GPU was detected.");
        }
        if (mlxRequested) {
            if (nvidiaSupported) {
                packages.add(DistributionPackage.MLX);
                reasons.add("MLX add-on: explicitly requested; it is installed next to standard CUDA, not"
                    + " instead of it.");
            } else {
                reasons.add("MLX add-on not suggested: it needs a supported NVIDIA GPU.");
            }
        }
        for (GpuAssessment assessment : assessments) {
            if (assessment.backend() == GpuBackend.VULKAN) {
                reasons.add(assessment.gpu().name() + ": Vulkan support is not assessed; GPU use is possible but"
                    + " unconfirmed.");
            }
        }
        if (assessments.isEmpty()) {
            reasons.add("No GPU detected; Ollama runs on the CPU.");
        }
        reasons.add(RUNTIME_NOTE);
        return new PackageRecommendation(true, packages, confidence, assessments, reasons);
    }

    /** Return human-readable problems with a manual selection; an empty list means it is valid. */
    public List<String> validateSelection(Set<DistributionPackage> selection, PackageRecommendation context) {
        List<String> problems = new ArrayList<String>();
        if (!context.isPlatformSupported()) {
            problems.add("The platform does not support the portable Ollama packages.");
            return problems;
        }
        try {
            DistributionPackage.requireValidSelection(selection);
        } catch (IllegalArgumentException e) {
            problems.add("The base package is required; add-ons only extend it.");
            return problems;
        }
        if (selection.contains(DistributionPackage.ROCM) && !hasUsable(context, GpuBackend.ROCM)) {
            problems.add("ROCm add-on selected without a supported or manually enabled AMD GPU.");
        }
        if (selection.contains(DistributionPackage.MLX) && !hasUsable(context, GpuBackend.CUDA)) {
            problems.add("MLX add-on selected without a supported NVIDIA GPU.");
        }
        return problems;
    }

    private List<GpuAssessment> assessAll(HardwareSnapshot snapshot, SupportOverrides overrides) {
        List<GpuAssessment> assessments = new ArrayList<GpuAssessment>();
        for (GpuDescriptor gpu : snapshot.gpus()) {
            GpuAssessment assessment = rules.assess(gpu);
            GpuSupport forced = overrides.find(gpu.name());
            if (forced != null && assessment.backend() != GpuBackend.NONE) {
                assessment = new GpuAssessment(gpu, assessment.backend(), forced, true,
                    "Manually set to " + forced + " (rules said " + assessment.support() + ").");
            }
            assessments.add(assessment);
        }
        return assessments;
    }

    private static boolean hasUsable(PackageRecommendation context, GpuBackend backend) {
        for (GpuAssessment assessment : context.assessments()) {
            if (assessment.backend() == backend && assessment.support() == GpuSupport.SUPPORTED) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSupportedPlatform(HardwareSnapshot snapshot) {
        String os = snapshot.operatingSystem().toLowerCase(Locale.ROOT);
        String arch = snapshot.architecture().toLowerCase(Locale.ROOT);
        return os.startsWith("windows") && (arch.equals("amd64") || arch.equals("x86_64"));
    }

    private static Confidence lower(Confidence current, Confidence candidate) {
        return candidate.ordinal() > current.ordinal() ? candidate : current;
    }
}
