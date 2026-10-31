package com.aresstack.ollamamanager.application.setup;

import com.aresstack.ollamamanager.application.common.CancellationToken;
import com.aresstack.ollamamanager.application.common.ErrorKind;
import com.aresstack.ollamamanager.application.common.OperationCancelledException;
import com.aresstack.ollamamanager.application.common.OperationException;
import com.aresstack.ollamamanager.application.common.ProgressListener;
import com.aresstack.ollamamanager.application.common.TransferProgress;
import com.aresstack.ollamamanager.application.hardware.PackageAdvisor;
import com.aresstack.ollamamanager.application.hardware.PackageRecommendation;
import com.aresstack.ollamamanager.application.hardware.SupportOverrides;
import com.aresstack.ollamamanager.application.installation.PortableInstallationService;
import com.aresstack.ollamamanager.application.port.HardwareProbePort;
import com.aresstack.ollamamanager.application.port.OllamaApiPort;
import com.aresstack.ollamamanager.application.port.OllamaRuntimePort;
import com.aresstack.ollamamanager.application.server.ServerLifecycleService;
import com.aresstack.ollamamanager.domain.distribution.DistributionPackage;
import com.aresstack.ollamamanager.domain.server.Endpoint;
import com.aresstack.ollamamanager.domain.server.OllamaInstallation;
import com.aresstack.ollamamanager.domain.server.ServerStatus;

import java.io.File;
import java.util.List;
import java.util.Set;

/**
 * Coordinates the setup assistant. It holds no process, installation or hardware logic of its own and delegates
 * every step to the existing use cases and ports; lifecycle and uninstall safety comes from the shared
 * {@code LifecycleLock} inside those services, so it also holds for callers that bypass this class.
 */
public final class SetupService {

    private final OllamaRuntimePort runtime;
    private final OllamaApiPort api;
    private final ServerLifecycleService lifecycle;
    private final HardwareProbePort hardware;
    private final PackageAdvisor advisor;
    private final PortableInstallationService installations;

    public SetupService(OllamaRuntimePort runtime, OllamaApiPort api, ServerLifecycleService lifecycle,
                        HardwareProbePort hardware, PackageAdvisor advisor,
                        PortableInstallationService installations) {
        this.runtime = require(runtime, "runtime");
        this.api = require(api, "api");
        this.lifecycle = require(lifecycle, "lifecycle");
        this.hardware = require(hardware, "hardware");
        this.advisor = require(advisor, "advisor");
        this.installations = require(installations, "installations");
    }

    /** Find startable Ollama installations on disk; a running server is detected separately. */
    public List<OllamaInstallation> detectExisting(List<File> additionalDirectories, CancellationToken cancellation)
        throws OperationException {
        return runtime.discover(additionalDirectories, cancellation);
    }

    /**
     * Return the version reported by an Ollama server at {@code endpoint}, or {@code null} if nothing answers
     * there. Any other failure, for example a non-Ollama service on the port, propagates.
     */
    public String detectRunning(Endpoint endpoint, CancellationToken cancellation) throws OperationException {
        try {
            return api.version(endpoint, cancellation);
        } catch (OperationCancelledException e) {
            throw e;
        } catch (OperationException e) {
            if (e.kind() == ErrorKind.CONNECTION_FAILED || e.kind() == ErrorKind.TIMEOUT) {
                return null;
            }
            throw e;
        }
    }

    /** Verify a running server through its API and attach it as external; it is shown but never controlled. */
    public ServerStatus attachExisting(Endpoint endpoint, CancellationToken cancellation) throws OperationException {
        String version = api.version(endpoint, cancellation);
        lifecycle.attachExternal(OllamaInstallation.external(version, endpoint), version);
        return lifecycle.status();
    }

    /** Probe the hardware and recommend portable packages with the existing advisor. */
    public PackageRecommendation recommendPackages(SupportOverrides overrides, boolean mlxRequested,
                                                   CancellationToken cancellation) throws OperationException {
        return advisor.recommend(hardware.probe(cancellation), overrides, mlxRequested);
    }

    /** Install and activate the latest stable release; does not take the lifecycle lock. */
    public OllamaInstallation installLatest(Set<DistributionPackage> packages,
                                            ProgressListener<TransferProgress> listener,
                                            CancellationToken cancellation) throws OperationException {
        return installations.installLatest(packages, listener, cancellation);
    }

    /** Remove a version; refused while a manager-owned server uses it or a lifecycle operation runs. */
    public void uninstall(OllamaInstallation installation) throws OperationException {
        installations.uninstall(installation);
    }

    private static <T> T require(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
        return value;
    }
}
