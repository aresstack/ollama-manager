package com.aresstack.ollamamanager.infrastructure.hardware;

import com.aresstack.ollamamanager.application.common.CancellationToken;
import com.aresstack.ollamamanager.application.hardware.GpuAssessment;
import com.aresstack.ollamamanager.application.hardware.GpuCompatibilityRules;
import com.aresstack.ollamamanager.application.hardware.PackageAdvisor;
import com.aresstack.ollamamanager.application.hardware.PackageRecommendation;
import com.aresstack.ollamamanager.application.hardware.SupportOverrides;
import com.aresstack.ollamamanager.domain.hardware.GpuDescriptor;
import com.aresstack.ollamamanager.domain.hardware.HardwareSnapshot;

/**
 * Manual diagnostic for native acceptance tests: runs the real probe and advisor and prints the result.
 * Downloads and installs nothing.
 */
public final class HardwareProbeMain {

    private HardwareProbeMain() {
    }

    public static void main(String[] args) throws Exception {
        long started = System.currentTimeMillis();
        HardwareSnapshot snapshot = SystemHardwareProbe.forCurrentSystem().probe(CancellationToken.none());
        System.out.println("java.version=" + System.getProperty("java.version"));
        System.out.println("probe.millis=" + (System.currentTimeMillis() - started));
        System.out.println("os=" + snapshot.operatingSystem() + " arch=" + snapshot.architecture());
        System.out.println("cpu=" + snapshot.cpuName() + " logicalProcessors=" + snapshot.logicalProcessors()
            + " memoryBytes=" + snapshot.totalMemoryBytes());
        for (GpuDescriptor gpu : snapshot.gpus()) {
            System.out.println("gpu vendor=" + gpu.vendor() + " name=" + gpu.name() + " vramBytes="
                + gpu.vramBytes() + " driver=" + gpu.driverVersion() + " dedicated=" + gpu.isDedicated());
        }
        System.out.println("fingerprint=" + snapshot.gpuFingerprint());
        PackageRecommendation recommendation = new PackageAdvisor(new GpuCompatibilityRules())
            .recommend(snapshot, SupportOverrides.none(), false);
        System.out.println("rules=" + GpuCompatibilityRules.VERSION);
        System.out.println("packages=" + recommendation.packages() + " confidence=" + recommendation.confidence());
        for (GpuAssessment assessment : recommendation.assessments()) {
            System.out.println("assessment " + assessment);
        }
        for (String reason : recommendation.reasons()) {
            System.out.println("reason " + reason);
        }
    }
}
