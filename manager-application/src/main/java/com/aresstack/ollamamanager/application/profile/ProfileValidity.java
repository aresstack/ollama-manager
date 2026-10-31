package com.aresstack.ollamamanager.application.profile;

import com.aresstack.ollamamanager.domain.hardware.GpuFingerprint;
import com.aresstack.ollamamanager.domain.model.ModelIdentity;
import com.aresstack.ollamamanager.domain.profile.ModelProfile;
import com.aresstack.ollamamanager.domain.profile.ProfileScope;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Whether a profile still matches the current model artifact, GPU fingerprint and Ollama version. Anything
 * that cannot be verified counts as a finding, so an unverified profile is never reported as valid.
 */
public final class ProfileValidity {

    /** Overall result. */
    public enum Result {
        VALID("valid"),
        OUTDATED("outdated"),
        UNVERIFIED("not verifiable");

        private final String label;

        Result(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private final Result result;
    private final List<String> findings;

    private ProfileValidity(Result result, List<String> findings) {
        this.result = result;
        this.findings = Collections.unmodifiableList(findings);
    }

    /** Evaluate {@code profile} against the captured environment. */
    public static ProfileValidity evaluate(ModelProfile profile, ProfileEnvironment.Snapshot environment) {
        return of(profile, environment.gpuFingerprint(), environment.ollamaVersion(), environment.installedModels());
    }

    /**
     * @param fingerprint current GPU fingerprint, or {@code null} when unknown
     * @param version     version of the running server, or {@code null} when no server runs
     * @param installed   models of the running server, or {@code null} when unknown
     */
    static ProfileValidity of(ModelProfile profile, GpuFingerprint fingerprint, String version,
                              List<ModelIdentity> installed) {
        ProfileScope scope = profile.scope();
        List<String> outdated = new ArrayList<String>();
        List<String> unknown = new ArrayList<String>();

        ModelIdentity current = null;
        if (installed == null) {
            unknown.add("model " + scope.model().name() + " cannot be checked without a running server");
        } else {
            for (ModelIdentity identity : installed) {
                if (identity.name().equals(scope.model().name())) {
                    current = identity;
                }
            }
            if (current == null) {
                outdated.add("model " + scope.model().name() + " is not installed");
            } else if (!scope.model().isVerifiable() || !current.isVerifiable()) {
                unknown.add("model digest of " + scope.model().name() + " is not known");
            } else if (!scope.model().sameArtifactAs(current)) {
                outdated.add("model " + scope.model().name() + " has a different digest or quantization");
            }
        }
        if (fingerprint == null) {
            unknown.add("GPU configuration is not known");
        } else if (!fingerprint.equals(scope.gpuFingerprint())) {
            outdated.add("GPU configuration changed (" + scope.gpuFingerprint() + " → " + fingerprint + ")");
        }
        if (version == null) {
            unknown.add("Ollama version cannot be checked without a running server");
        } else if (!version.equals(scope.ollamaVersion())) {
            outdated.add("Ollama version changed (" + scope.ollamaVersion() + " → " + version + ")");
        }

        List<String> findings = new ArrayList<String>(outdated);
        findings.addAll(unknown);
        if (!outdated.isEmpty()) {
            return new ProfileValidity(Result.OUTDATED, findings);
        }
        if (!unknown.isEmpty()) {
            return new ProfileValidity(Result.UNVERIFIED, findings);
        }
        // Cross-check with the domain rule so both definitions of "valid" cannot drift apart.
        ProfileScope now = new ProfileScope(current, fingerprint, version, scope.workload());
        return scope.isValidFor(now) ? new ProfileValidity(Result.VALID, findings)
            : new ProfileValidity(Result.OUTDATED, Collections.singletonList("profile scope does not match"));
    }

    public Result result() {
        return result;
    }

    public List<String> findings() {
        return findings;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof ProfileValidity)) {
            return false;
        }
        ProfileValidity that = (ProfileValidity) other;
        return result == that.result && findings.equals(that.findings);
    }

    @Override
    public int hashCode() {
        return Objects.hash(result, findings);
    }

    @Override
    public String toString() {
        return result.label() + (findings.isEmpty() ? "" : " " + findings);
    }
}
