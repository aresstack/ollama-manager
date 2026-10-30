package com.aresstack.ollamamanager.domain.configuration;

import com.aresstack.ollamamanager.domain.hardware.GpuFingerprint;
import com.aresstack.ollamamanager.domain.model.ModelIdentity;
import com.aresstack.ollamamanager.domain.model.ModelOptions;
import com.aresstack.ollamamanager.domain.profile.ModelProfile;
import com.aresstack.ollamamanager.domain.profile.ProfileScope;
import com.aresstack.ollamamanager.domain.server.InstallationKind;
import com.aresstack.ollamamanager.domain.server.ServerSettings;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ManagerConfigurationTest {

    private static final ModelProfile PROFILE = new ModelProfile("p1", "Chat", 1,
        new ProfileScope(ModelIdentity.named("a"), GpuFingerprint.CPU_ONLY, "0.12.0", "chat"),
        ServerSettings.defaults(), ModelOptions.none());

    @Test
    public void defaultsAreLoopbackWithoutProfiles() {
        ManagerConfiguration defaults = ManagerConfiguration.defaults();

        assertTrue(defaults.desiredServerSettings().bind().isLoopback());
        assertTrue(defaults.profiles().isEmpty());
    }

    @Test
    public void withProfileReplacesById() {
        ManagerConfiguration configuration = ManagerConfiguration.defaults().withProfile(PROFILE)
            .withProfile(PROFILE.revise(ServerSettings.defaults(), ModelOptions.none()));

        assertEquals(1, configuration.profiles().size());
        assertEquals(2, configuration.findProfile("p1").revision());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void profilesAreImmutable() {
        ManagerConfiguration.defaults().profiles().add(PROFILE);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsUnknownActiveProfile() {
        ManagerConfiguration.defaults().withActiveProfileId("missing");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsExternalSelection() {
        ManagerConfiguration.defaults().withSelectedInstallation(InstallationKind.EXTERNAL_ATTACHED,
            new File("x").getAbsoluteFile());
    }
}
