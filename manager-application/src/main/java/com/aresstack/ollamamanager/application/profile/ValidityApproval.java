package com.aresstack.ollamamanager.application.profile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The user's explicit confirmation, from a dialog that showed the findings, to activate a profile that is
 * outdated or unverified. It is accepted only if profile, revision, result and findings all equal the
 * evaluation made at activation time, so a confirmation never covers a situation the user has not seen. It is
 * independent of the restart confirmation.
 */
public final class ValidityApproval {

    private final String profileId;
    private final int revision;
    private final ProfileValidity.Result result;
    private final List<String> findings;

    public ValidityApproval(String profileId, int revision, ProfileValidity.Result result, List<String> findings) {
        if (profileId == null || result == null || findings == null) {
            throw new IllegalArgumentException("profileId, result and findings must not be null");
        }
        this.profileId = profileId;
        this.revision = revision;
        this.result = result;
        this.findings = Collections.unmodifiableList(new ArrayList<String>(findings));
    }

    /** Return whether this approval covers {@code validity} of revision {@code revision} of {@code profileId}. */
    public boolean covers(String id, int currentRevision, ProfileValidity validity) {
        return profileId.equals(id) && revision == currentRevision && result == validity.result()
            && findings.equals(validity.findings());
    }

    public String profileId() {
        return profileId;
    }

    public int revision() {
        return revision;
    }

    public ProfileValidity.Result result() {
        return result;
    }

    public List<String> findings() {
        return findings;
    }
}
