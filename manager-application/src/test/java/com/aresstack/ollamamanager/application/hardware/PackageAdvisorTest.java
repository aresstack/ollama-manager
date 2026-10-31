package com.aresstack.ollamamanager.application.hardware;

import com.aresstack.ollamamanager.domain.distribution.DistributionPackage;
import com.aresstack.ollamamanager.domain.hardware.GpuDescriptor;
import com.aresstack.ollamamanager.domain.hardware.GpuVendor;
import com.aresstack.ollamamanager.domain.hardware.HardwareSnapshot;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PackageAdvisorTest {

    private static final long GIB = 1024L * 1024L * 1024L;
    private final PackageAdvisor advisor = new PackageAdvisor(new GpuCompatibilityRules());

    private static GpuDescriptor nvidia(String name, String driver) {
        return new GpuDescriptor(GpuVendor.NVIDIA, name, 16 * GIB, driver, true);
    }

    private static GpuDescriptor amd(String name, boolean dedicated) {
        return new GpuDescriptor(GpuVendor.AMD, name, dedicated ? 24 * GIB : null, "31.0.1", dedicated);
    }

    private static HardwareSnapshot windows(GpuDescriptor... gpus) {
        return new HardwareSnapshot("Windows 11", "amd64", "CPU", 16, 64 * GIB, Arrays.asList(gpus));
    }

    private PackageRecommendation recommend(HardwareSnapshot snapshot) {
        return advisor.recommend(snapshot, SupportOverrides.none(), false);
    }

    @Test
    public void rtx5080WithCurrentDriverUsesBaseOnly() {
        PackageRecommendation r = recommend(windows(nvidia("NVIDIA GeForce RTX 5080", "576.88")));
        assertEquals(EnumSet.of(DistributionPackage.BASE), r.packages());
        assertEquals(GpuSupport.SUPPORTED, r.assessments().get(0).support());
        assertEquals(Confidence.HIGH, r.confidence());
        assertTrue(r.reasons().contains(PackageAdvisor.RUNTIME_NOTE));
    }

    @Test
    public void rtx5080WithOldDriverNeedsManualDriverUpdate() {
        PackageRecommendation r = recommend(windows(nvidia("NVIDIA GeForce RTX 5080", "560.94")));
        assertEquals(GpuSupport.DRIVER_UPDATE_REQUIRED, r.assessments().get(0).support());
        assertEquals(Confidence.LOW, r.confidence());
        assertEquals(EnumSet.of(DistributionPackage.BASE), r.packages());
    }

    @Test
    public void normalisesWindowsDriverVersionsFromCim() {
        GpuCompatibilityRules rules = new GpuCompatibilityRules();
        assertEquals(GpuSupport.SUPPORTED,
            rules.assess(nvidia("NVIDIA GeForce RTX 5080", "32.0.15.7688")).support());
        assertEquals(GpuSupport.DRIVER_UPDATE_REQUIRED,
            rules.assess(nvidia("NVIDIA GeForce RTX 5080", "32.0.15.6094")).support());
    }

    @Test
    public void appliesDocumentedMinimumDriverBoundaries() {
        GpuCompatibilityRules rules = new GpuCompatibilityRules();
        assertEquals(GpuSupport.DRIVER_UPDATE_REQUIRED,
            rules.assess(nvidia("NVIDIA GeForce RTX 4090", "549.99")).support());
        assertEquals(GpuSupport.DRIVER_UPDATE_REQUIRED,
            rules.assess(nvidia("NVIDIA GeForce RTX 4090", "540.12")).support());
        assertEquals(GpuSupport.SUPPORTED, rules.assess(nvidia("NVIDIA GeForce RTX 4090", "550.00")).support());
        assertEquals(GpuSupport.DRIVER_UPDATE_REQUIRED,
            rules.assess(nvidia("NVIDIA GeForce GTX 1060", "569.99")).support());
        assertEquals(GpuSupport.SUPPORTED, rules.assess(nvidia("NVIDIA GeForce GTX 1060", "570.00")).support());
    }

    @Test
    public void rtx5080NeedsFirstSupportingDriverComparedOnMinor() {
        GpuCompatibilityRules rules = new GpuCompatibilityRules();
        for (String tooOld : new String[] {"570.00", "572.15", "32.0.15.7215"}) {
            assertEquals(tooOld, GpuSupport.DRIVER_UPDATE_REQUIRED,
                rules.assess(nvidia("NVIDIA GeForce RTX 5080", tooOld)).support());
        }
        for (String ok : new String[] {"572.16", "576.88", "32.0.15.7216", "32.0.15.7688"}) {
            assertEquals(ok, GpuSupport.SUPPORTED, rules.assess(nvidia("NVIDIA GeForce RTX 5080", ok)).support());
        }
        assertEquals(GpuSupport.SUPPORTED, rules.assess(nvidia("NVIDIA GeForce RTX 5090", "572.16")).support());
        assertEquals(Integer.valueOf(57688), GpuCompatibilityRules.driverRelease("32.0.15.7688"));
        assertEquals("576.88", GpuCompatibilityRules.format(57688));
    }

    @Test
    public void otherRtx50ModelsWithoutVerifiedDriverStayUnknown() {
        GpuCompatibilityRules rules = new GpuCompatibilityRules();
        assertEquals(GpuSupport.UNKNOWN, rules.assess(nvidia("NVIDIA GeForce RTX 5070 Ti", "576.88")).support());
        assertEquals(GpuSupport.UNKNOWN, rules.assess(nvidia("NVIDIA GeForce RTX 5060", "580.10")).support());
    }

    @Test
    public void prefersReportedComputeCapabilityOverNameHeuristic() {
        GpuCompatibilityRules rules = new GpuCompatibilityRules();
        GpuDescriptor quadroPascal = new GpuDescriptor(GpuVendor.NVIDIA, "NVIDIA Quadro P4000", 8 * GIB, "560.94",
            true, "6.1");
        assertEquals(GpuSupport.DRIVER_UPDATE_REQUIRED, rules.assess(quadroPascal).support());
        GpuDescriptor quadroTuring = new GpuDescriptor(GpuVendor.NVIDIA, "NVIDIA Quadro T1000", 4 * GIB, "550.00",
            true, "7.5");
        assertEquals(GpuSupport.SUPPORTED, rules.assess(quadroTuring).support());
        GpuDescriptor kepler = new GpuDescriptor(GpuVendor.NVIDIA, "NVIDIA Tesla K80", 12 * GIB, "570.00", true,
            "3.7");
        assertEquals(GpuSupport.UNSUPPORTED, rules.assess(kepler).support());
        GpuDescriptor rtx5080 = new GpuDescriptor(GpuVendor.NVIDIA, "NVIDIA GeForce RTX 5080", 16 * GIB, "610.88",
            true, "12.0");
        assertEquals(GpuSupport.SUPPORTED, rules.assess(rtx5080).support());
        GpuDescriptor rtx5070 = new GpuDescriptor(GpuVendor.NVIDIA, "NVIDIA GeForce RTX 5070", 12 * GIB, "610.88",
            true, "12.0");
        assertEquals(GpuSupport.UNKNOWN, rules.assess(rtx5070).support());
    }

    @Test
    public void olderNvidiaGenerations() {
        GpuCompatibilityRules rules = new GpuCompatibilityRules();
        assertEquals(GpuSupport.DRIVER_UPDATE_REQUIRED,
            rules.assess(nvidia("NVIDIA GeForce GTX 1060 6GB", "552.22")).support());
        assertEquals(GpuSupport.SUPPORTED, rules.assess(nvidia("NVIDIA GeForce GTX 1060 6GB", "570.00")).support());
        assertEquals(GpuSupport.SUPPORTED, rules.assess(nvidia("NVIDIA GeForce GTX 750 Ti", "572.16")).support());
        assertEquals(GpuSupport.SUPPORTED, rules.assess(nvidia("NVIDIA GeForce GTX 1660 SUPER", "550.00")).support());
        assertEquals(GpuSupport.UNSUPPORTED, rules.assess(nvidia("NVIDIA GeForce GTX 770", "472.12")).support());
        assertEquals(GpuSupport.UNKNOWN, rules.assess(nvidia("NVIDIA Quadro P4000", "552.22")).support());
        assertEquals(GpuSupport.UNKNOWN, rules.assess(nvidia("NVIDIA GeForce RTX 3060", null)).support());
    }

    @Test
    public void compatibleAmdAddsRocm() {
        PackageRecommendation r = recommend(windows(amd("AMD Radeon RX 7900 XTX", true)));
        assertEquals(EnumSet.of(DistributionPackage.BASE, DistributionPackage.ROCM), r.packages());
        assertEquals(Confidence.HIGH, r.confidence());
    }

    @Test
    public void rx7900XtIsNotConfusedWithXtx() {
        GpuCompatibilityRules rules = new GpuCompatibilityRules();
        assertEquals(GpuSupport.SUPPORTED, rules.assess(amd("AMD Radeon RX 7900 XT", true)).support());
        assertEquals(GpuSupport.SUPPORTED, rules.assess(amd("AMD Radeon RX 7600", true)).support());
        assertEquals(GpuSupport.UNKNOWN, rules.assess(amd("AMD Radeon RX 6800 XT", true)).support());
        assertEquals(GpuSupport.UNKNOWN, rules.assess(amd("AMD Radeon RX 76000", true)).support());
    }

    @Test
    public void unknownAmdStaysBaseUntilManuallyEnabled() {
        HardwareSnapshot snapshot = windows(amd("AMD Radeon RX 9070 XT", true));
        PackageRecommendation r = recommend(snapshot);
        assertEquals(GpuSupport.UNKNOWN, r.assessments().get(0).support());
        assertEquals(EnumSet.of(DistributionPackage.BASE), r.packages());
        assertEquals(Confidence.LOW, r.confidence());

        PackageRecommendation forced = advisor.recommend(snapshot,
            SupportOverrides.none().with("amd radeon rx 9070 xt", GpuSupport.SUPPORTED), false);
        assertEquals(EnumSet.of(DistributionPackage.BASE, DistributionPackage.ROCM), forced.packages());
        assertTrue(forced.assessments().get(0).isOverridden());
        assertEquals(Confidence.MEDIUM, forced.confidence());
    }

    @Test
    public void integratedGraphicsAreReportedAsVulkanUnknownNotCpuOnly() {
        PackageRecommendation r = recommend(windows(
            new GpuDescriptor(GpuVendor.INTEL, "Intel(R) UHD Graphics 770", null, "31.0", false),
            amd("AMD Radeon(TM) Graphics", false)));
        assertEquals(EnumSet.of(DistributionPackage.BASE), r.packages());
        assertEquals(2, r.assessments().size());
        for (GpuAssessment assessment : r.assessments()) {
            assertEquals(GpuBackend.VULKAN, assessment.backend());
            assertEquals(GpuSupport.UNKNOWN, assessment.support());
        }
        assertEquals(Confidence.LOW, r.confidence());
        for (String reason : r.reasons()) {
            assertFalse(reason, reason.contains("runs on the CPU"));
        }
    }

    @Test
    public void intelArcIsVulkanUnknown() {
        GpuAssessment arc = new GpuCompatibilityRules().assess(
            new GpuDescriptor(GpuVendor.INTEL, "Intel(R) Arc(TM) A770 Graphics", 16 * GIB, "32.0.101.6078", true));
        assertEquals(GpuBackend.VULKAN, arc.backend());
        assertEquals(GpuSupport.UNKNOWN, arc.support());
        assertTrue(arc.reason().contains("Vulkan"));
        PackageRecommendation r = recommend(windows(arc.gpu()));
        assertEquals(EnumSet.of(DistributionPackage.BASE), r.packages());
        assertEquals(Confidence.LOW, r.confidence());
    }

    @Test
    public void noGpuAtAllIsCpu() {
        PackageRecommendation r = recommend(windows());
        assertEquals(EnumSet.of(DistributionPackage.BASE), r.packages());
    }

    @Test
    public void multiGpuCombinesBackends() {
        PackageRecommendation r = advisor.recommend(windows(nvidia("NVIDIA GeForce RTX 4090", "560.94"),
            amd("AMD Radeon RX 7800 XT", true)), SupportOverrides.none(), true);
        assertEquals(EnumSet.allOf(DistributionPackage.class), r.packages());
        assertEquals(2, r.assessments().size());
    }

    @Test
    public void mlxOnlyWhenRequestedAndNvidiaSupported() {
        assertFalse(recommend(windows(nvidia("NVIDIA GeForce RTX 4090", "560.94")))
            .packages().contains(DistributionPackage.MLX));
        PackageRecommendation amdOnly = advisor.recommend(windows(amd("AMD Radeon RX 7900 XTX", true)),
            SupportOverrides.none(), true);
        assertFalse(amdOnly.packages().contains(DistributionPackage.MLX));
    }

    @Test
    public void rejectsUnsupportedPlatform() {
        PackageRecommendation r = advisor.recommend(new HardwareSnapshot("Windows 11", "aarch64", null, 8, null,
            Collections.<GpuDescriptor>emptyList()), SupportOverrides.none(), false);
        assertFalse(r.isPlatformSupported());
        assertTrue(r.packages().isEmpty());
        assertFalse(advisor.validateSelection(EnumSet.of(DistributionPackage.BASE), r).isEmpty());
    }

    @Test
    public void validatesSelections() {
        PackageRecommendation r = recommend(windows(nvidia("NVIDIA GeForce RTX 4090", "560.94")));
        assertTrue(advisor.validateSelection(EnumSet.of(DistributionPackage.BASE, DistributionPackage.MLX), r)
            .isEmpty());
        assertEquals(1, advisor.validateSelection(EnumSet.of(DistributionPackage.ROCM), r).size());
        assertEquals(1, advisor.validateSelection(EnumSet.of(DistributionPackage.BASE, DistributionPackage.ROCM), r)
            .size());
    }
}
