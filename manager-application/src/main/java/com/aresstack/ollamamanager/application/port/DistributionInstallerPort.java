package com.aresstack.ollamamanager.application.port;

import com.aresstack.ollamamanager.application.common.CancellationToken;
import com.aresstack.ollamamanager.application.common.OperationException;
import com.aresstack.ollamamanager.application.common.ProgressListener;
import com.aresstack.ollamamanager.application.common.TransferProgress;
import com.aresstack.ollamamanager.domain.distribution.DistributionPackage;
import com.aresstack.ollamamanager.domain.distribution.OllamaRelease;
import com.aresstack.ollamamanager.domain.server.OllamaInstallation;

import java.io.File;
import java.util.List;
import java.util.Set;

/**
 * Downloads, verifies and activates portable distributions below an install root. Implemented in issue #4.
 * Never starts the installed binary.
 */
public interface DistributionInstallerPort {

    /**
     * Download, verify and extract the packages into a fresh versioned directory and activate it.
     * Returns a {@code MANAGED_PORTABLE} installation.
     */
    OllamaInstallation install(OllamaRelease release, Set<DistributionPackage> packages, File installRoot,
                               ProgressListener<TransferProgress> listener, CancellationToken cancellation)
        throws OperationException;

    /** Return all portable versions present below the install root, active one first. */
    List<OllamaInstallation> installedVersions(File installRoot) throws OperationException;

    /** Remove one manager-created version directory. Never touches model folders. */
    void uninstall(OllamaInstallation installation, File installRoot) throws OperationException;
}
