package com.aresstack.ollamamanager.domain.model;

import com.aresstack.ollamamanager.domain.common.Preconditions;

/**
 * Exact identity of a model: name with tag, plus digest and quantization when known.
 */
public final class ModelIdentity {

    private final String name;
    private final String digest;
    private final String quantization;

    public ModelIdentity(String name, String digest, String quantization) {
        String trimmed = Preconditions.requireNonBlank(name, "name");
        this.name = trimmed.contains(":") ? trimmed : trimmed + ":latest";
        this.digest = blankToNull(digest);
        this.quantization = blankToNull(quantization);
    }

    public static ModelIdentity named(String name) {
        return new ModelIdentity(name, null, null);
    }

    /** Return the name including tag, for example {@code llama3.1:8b}. */
    public String name() {
        return name;
    }

    /** Return the content digest, or {@code null} if unknown. */
    public String digest() {
        return digest;
    }

    /** Return the quantization level such as {@code Q4_K_M}, or {@code null} if unknown. */
    public String quantization() {
        return quantization;
    }

    /** Return whether the digest is known, which is required to verify measurements against this model. */
    public boolean isVerifiable() {
        return digest != null;
    }

    /**
     * Return whether both identities provably denote the same model artifact: same name, both digests known
     * and equal, and quantization equal where both are known. A missing digest means "not verified".
     */
    public boolean sameArtifactAs(ModelIdentity other) {
        if (!name.equals(other.name) || !isVerifiable() || !other.isVerifiable() || !digest.equals(other.digest)) {
            return false;
        }
        return quantization == null || other.quantization == null || quantization.equals(other.quantization);
    }

    private static String blankToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof ModelIdentity)) {
            return false;
        }
        ModelIdentity that = (ModelIdentity) other;
        return name.equals(that.name) && equal(digest, that.digest) && equal(quantization, that.quantization);
    }

    private static boolean equal(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + (digest == null ? 0 : digest.hashCode());
        return 31 * result + (quantization == null ? 0 : quantization.hashCode());
    }

    @Override
    public String toString() {
        return name + (digest == null ? "" : "@" + digest) + (quantization == null ? "" : " " + quantization);
    }
}
