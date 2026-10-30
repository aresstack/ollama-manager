package com.aresstack.ollamamanager.application.installation;

import com.aresstack.ollamamanager.application.common.CancellationToken;
import com.aresstack.ollamamanager.application.common.ProgressListener;
import com.aresstack.ollamamanager.application.common.TransferProgress;
import com.aresstack.ollamamanager.application.port.DistributionInstallerPort;
import com.aresstack.ollamamanager.application.port.ReleaseCatalogPort;
import com.aresstack.ollamamanager.domain.distribution.DistributionPackage;
import com.aresstack.ollamamanager.domain.distribution.OllamaRelease;
import com.aresstack.ollamamanager.domain.server.Endpoint;
import com.aresstack.ollamamanager.domain.server.InstallationKind;
import com.aresstack.ollamamanager.domain.server.OllamaInstallation;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PortableInstallationServiceTest {

    private final List<OllamaInstallation> installed = new ArrayList<OllamaInstallation>();
    private final File root = new File("portable").getAbsoluteFile();

    private final ReleaseCatalogPort catalog = new ReleaseCatalogPort() {
        @Override
        public OllamaRelease latestStable(CancellationToken cancellation) {
            return release("v0.12.10");
        }

        @Override
        public OllamaRelease findRelease(String version, CancellationToken cancellation) {
            return release(version);
        }
    };

    private final DistributionInstallerPort installer = new DistributionInstallerPort() {
        @Override
        public OllamaInstallation install(OllamaRelease release, Set<DistributionPackage> packages, File installRoot,
                                          ProgressListener<TransferProgress> listener, CancellationToken cancellation) {
            OllamaInstallation installation = portable(release.version());
            installed.add(0, installation);
            return installation;
        }

        @Override
        public List<OllamaInstallation> installedVersions(File installRoot) {
            return installed;
        }

        @Override
        public void uninstall(OllamaInstallation installation, File installRoot) {
            installed.remove(installation);
        }
    };

    private final PortableInstallationService service = new PortableInstallationService(catalog, installer, root);

    @Test
    public void reportsUpdateWhenNothingIsInstalled() throws Exception {
        assertTrue(service.checkForUpdate(CancellationToken.none()).updateAvailable());
    }

    @Test
    public void comparesActiveVersionNumerically() throws Exception {
        service.installVersion("v0.12.9", Collections.singleton(DistributionPackage.BASE),
            ProgressListener.<TransferProgress>ignoring(), CancellationToken.none());
        assertTrue(service.checkForUpdate(CancellationToken.none()).updateAvailable());

        service.installLatest(Collections.singleton(DistributionPackage.BASE),
            ProgressListener.<TransferProgress>ignoring(), CancellationToken.none());
        UpdateCheck check = service.checkForUpdate(CancellationToken.none());
        assertEquals("v0.12.10", check.active().version());
        assertFalse(check.updateAvailable());
    }

    @Test
    public void ordersReleaseVersions() {
        assertTrue(ReleaseVersions.compare("v0.12.10", "v0.12.9") > 0);
        assertTrue(ReleaseVersions.compare("0.9.0", "v0.12.0") < 0);
        assertEquals(0, ReleaseVersions.compare("v1.0", "1.0.0"));
    }

    private static OllamaRelease release(String version) {
        return new OllamaRelease(version, Collections.emptyList());
    }

    private OllamaInstallation portable(String version) {
        return new OllamaInstallation(InstallationKind.MANAGED_PORTABLE, version, new File(root, version),
            Endpoint.DEFAULT);
    }
}
