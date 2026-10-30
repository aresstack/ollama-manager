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
    public void olderNvidiaGenerations() {
        GpuCompatibilityRules rules = new GpuCompatibilityRules();
        assertEquals(GpuSupport.SUPPORTED, rules.assess(nvidia("NVIDIA GeForce GTX 1060 6GB", "552.22")).support());
        assertEquals(GpuSupport.SUPPORTED, rules.assess(nvidia("NVIDIA GeForce GTX 750 Ti", "552.22")).support());
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
    public void integratedGraphicsOnlyIsCpu() {
        PackageRecommendation r = recommend(windows(
            new GpuDescriptor(GpuVendor.INTEL, "Intel(R) UHD Graphics 770", null, "31.0", false),
            amd("AMD Radeon(TM) Graphics", false)));
        assertEquals(EnumSet.of(DistributionPackage.BASE), r.packages());
        assertTrue(r.assessments().isEmpty());
        assertEquals(Confidence.HIGH, r.confidence());
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
