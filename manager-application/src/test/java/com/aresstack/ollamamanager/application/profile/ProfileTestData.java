package com.aresstack.ollamamanager.application.profile;

import com.aresstack.ollamamanager.domain.hardware.GpuFingerprint;
import com.aresstack.ollamamanager.domain.model.ModelIdentity;
import com.aresstack.ollamamanager.domain.profile.ProfileScope;

final class ProfileTestData {

    static final ProfileScope SCOPE = new ProfileScope(new ModelIdentity("llama3.1:8b", "aaa", "Q4_K_M"),
        GpuFingerprint.CPU_ONLY, "0.12.6", "chat");

    private ProfileTestData() {
    }
}
