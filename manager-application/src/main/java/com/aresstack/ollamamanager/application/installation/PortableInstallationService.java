package com.aresstack.ollamamanager.application.installation;

import com.aresstack.ollamamanager.application.common.CancellationToken;
import com.aresstack.ollamamanager.application.common.ErrorKind;
import com.aresstack.ollamamanager.application.common.LifecycleLock;
import com.aresstack.ollamamanager.application.common.OperationException;
import com.aresstack.ollamamanager.application.common.ProgressListener;
import com.aresstack.ollamamanager.application.common.TransferProgress;
import com.aresstack.ollamamanager.application.port.DistributionInstallerPort;
import com.aresstack.ollamamanager.application.port.ReleaseCatalogPort;
import com.aresstack.ollamamanager.domain.distribution.DistributionPackage;
import com.aresstack.ollamamanager.domain.distribution.OllamaRelease;
import com.aresstack.ollamamanager.domain.server.OllamaInstallation;

import java.io.File;
import java.util.List;
import java.util.Set;

/**
 * Use cases for the portable Ollama distribution: check for updates, install the latest or a specific release.
 * Installing never starts the binary; starting stays an explicit user action.
 */
public final class PortableInstallationService {

    private final ReleaseCatalogPort catalog;
    private final DistributionInstallerPort installer;
    private final File installRoot;
    private final InstallationUsage usage;
    private final LifecycleLock lock;

    public PortableInstallationService(ReleaseCatalogPort catalog, DistributionInstallerPort installer,
                                       File installRoot, InstallationUsage usage) {
        this(catalog, installer, installRoot, usage, new LifecycleLock());
    }

    /**
     * @param lock the process-wide lock shared with the server lifecycle; uninstall checks usage and deletes under
     *             it, downloads and installs never take it
     */
    public PortableInstallationService(ReleaseCatalogPort catalog, DistributionInstallerPort installer,
                                       File installRoot, InstallationUsage usage, LifecycleLock lock) {
        if (lock == null) {
            throw new IllegalArgumentException("lock must not be null");
        }
        this.lock = lock;
        this.catalog = catalog;
        this.installer = installer;
        this.installRoot = installRoot;
        this.usage = usage;
    }

    public UpdateCheck checkForUpdate(CancellationToken cancellation) throws OperationException {
        OllamaRelease latest = catalog.latestStable(cancellation);
        List<OllamaInstallation> installed = installer.installedVersions(installRoot);
        return new UpdateCheck(latest, installed.isEmpty() ? null : installed.get(0));
    }

    /** Install and activate the latest stable release; the previous version stays for rollback. */
    public OllamaInstallation installLatest(Set<DistributionPackage> packages,
                                            ProgressListener<TransferProgress> listener,
                                            CancellationToken cancellation) throws OperationException {
        return installer.install(catalog.latestStable(cancellation), packages, installRoot, listener, cancellation);
    }

    /** Install and activate one release tag, for example to pin a version known to work. */
    public OllamaInstallation installVersion(String version, Set<DistributionPackage> packages,
                                             ProgressListener<TransferProgress> listener,
                                             CancellationToken cancellation) throws OperationException {
        return installer.install(catalog.findRelease(version, cancellation), packages, installRoot, listener,
            cancellation);
    }

    public List<OllamaInstallation> installedVersions() throws OperationException {
        return installer.installedVersions(installRoot);
    }

    /**
     * Remove a version that no manager-owned process uses. Installing or activating another version never
     * changes existing version directories, so only uninstall needs this check.
     */
    public void uninstall(OllamaInstallation installation) throws OperationException {
        lock.run("uninstall " + installation.version(), () -> {
            if (usage.isInUse(installation)) {
                throw new OperationException(ErrorKind.INVALID_STATE,
                    installation.version() + " is used by a running Ollama server; stop it first");
            }
            installer.uninstall(installation, installRoot);
            return null;
        });
    }
}
