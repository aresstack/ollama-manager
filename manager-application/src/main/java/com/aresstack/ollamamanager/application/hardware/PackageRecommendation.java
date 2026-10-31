package com.aresstack.ollamamanager.application.hardware;

import com.aresstack.ollamamanager.domain.distribution.DistributionPackage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Suggested Ollama packages with confidence and reasons. Empty packages mean the platform is unsupported.
 */
public final class PackageRecommendation {

    private final boolean platformSupported;
    private final Set<DistributionPackage> packages;
    private final Confidence confidence;
    private final List<GpuAssessment> assessments;
    private final List<String> reasons;

    PackageRecommendation(boolean platformSupported, Set<DistributionPackage> packages, Confidence confidence,
                          List<GpuAssessment> assessments, List<String> reasons) {
        this.platformSupported = platformSupported;
        this.packages = packages.isEmpty() ? Collections.<DistributionPackage>emptySet()
            : Collections.unmodifiableSet(EnumSet.copyOf(packages));
        this.confidence = confidence;
        this.assessments = Collections.unmodifiableList(new ArrayList<GpuAssessment>(assessments));
        this.reasons = Collections.unmodifiableList(new ArrayList<String>(reasons));
    }

    public boolean isPlatformSupported() {
        return platformSupported;
    }

    public Set<DistributionPackage> packages() {
        return packages;
    }

    public Confidence confidence() {
        return confidence;
    }

    public List<GpuAssessment> assessments() {
        return assessments;
    }

    /** Return explanations in display order. */
    public List<String> reasons() {
        return reasons;
    }
}
