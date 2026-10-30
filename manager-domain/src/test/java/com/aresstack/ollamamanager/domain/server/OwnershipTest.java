package com.aresstack.ollamamanager.domain.server;

import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class OwnershipTest {

    private static final File BINARIES = new File("ollama").getAbsoluteFile();

    @Test
    public void externalServerCannotBeStopped() {
        ServerStatus status = new ServerStatus(ServerState.RUNNING, RuntimeOwnership.EXTERNAL,
            OllamaInstallation.external("0.12.0", Endpoint.DEFAULT), "0.12.0", "");

        assertFalse(status.canBeStoppedByManager());
        assertNull(status.installation().binaryDirectory());
    }

    @Test
    public void ownedRunningServerCanBeStopped() {
        OllamaInstallation installation = new OllamaInstallation(InstallationKind.MANAGED_PORTABLE, "0.12.0",
            BINARIES, Endpoint.DEFAULT);

        assertTrue(new ServerStatus(ServerState.RUNNING, RuntimeOwnership.OWNED, installation, "0.12.0", "")
            .canBeStoppedByManager());
    }

    @Test(expected = IllegalArgumentException.class)
    public void externalInstallationCannotBeOwned() {
        new ServerStatus(ServerState.RUNNING, RuntimeOwnership.OWNED,
            OllamaInstallation.external("0.12.0", Endpoint.DEFAULT), null, "");
    }

    @Test(expected = IllegalArgumentException.class)
    public void managedInstallationRequiresAbsoluteBinaryDirectory() {
        new OllamaInstallation(InstallationKind.MANAGED_INSTALLED, "0.12.0", new File("relative"), Endpoint.DEFAULT);
    }
}
