package com.aresstack.ollamamanager.application.profile;

import com.aresstack.ollamamanager.application.common.CancellationToken;
import com.aresstack.ollamamanager.application.common.OperationCancelledException;
import com.aresstack.ollamamanager.application.common.OperationException;
import com.aresstack.ollamamanager.application.model.ModelEntry;
import com.aresstack.ollamamanager.application.model.ModelService;
import com.aresstack.ollamamanager.application.port.HardwareProbePort;
import com.aresstack.ollamamanager.application.server.ServerLifecycleService;
import com.aresstack.ollamamanager.domain.hardware.GpuFingerprint;
import com.aresstack.ollamamanager.domain.model.ModelIdentity;
import com.aresstack.ollamamanager.domain.server.ServerState;
import com.aresstack.ollamamanager.domain.server.ServerStatus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Captures what a profile's validity depends on: the GPU fingerprint, the version of the running Ollama server
 * and the identities (digest, quantization) of its installed models. Values that cannot be determined are
 * {@code null}, which {@link ProfileValidity} reports as unverified, never as valid.
 */
public interface ProfileEnvironment {

    /** Capture the current environment; only cancellation propagates, every other failure yields unknowns. */
    Snapshot capture(CancellationToken cancellation) throws OperationCancelledException;

    /** Build the environment from the hardware probe, the server lifecycle and the model inventory. */
    static ProfileEnvironment live(HardwareProbePort hardware, ServerLifecycleService lifecycle, ModelService models) {
        if (hardware == null || lifecycle == null || models == null) {
            throw new IllegalArgumentException("hardware, lifecycle and models must not be null");
        }
        return cancellation -> {
            GpuFingerprint fingerprint;
            try {
                fingerprint = hardware.probe(cancellation).gpuFingerprint();
            } catch (OperationCancelledException e) {
                throw e;
            } catch (OperationException e) {
                fingerprint = null;
            }
            ServerStatus status = lifecycle.status();
            if (status.state() != ServerState.RUNNING || status.installation() == null) {
                return new Snapshot(fingerprint, null, null);
            }
            List<ModelIdentity> installed;
            try {
                installed = new ArrayList<ModelIdentity>();
                for (ModelEntry entry : models.inventory(status.installation().endpoint(), cancellation)) {
                    if (entry.isInstalled()) {
                        installed.add(entry.identity());
                    }
                }
            } catch (OperationCancelledException e) {
                throw e;
            } catch (OperationException e) {
                installed = null;
            }
            return new Snapshot(fingerprint, status.reportedVersion(), installed);
        };
    }

    /** Immutable values captured at one point in time; each may be {@code null} when unknown. */
    final class Snapshot {
        private final GpuFingerprint gpuFingerprint;
        private final String ollamaVersion;
        private final List<ModelIdentity> installedModels;

        public Snapshot(GpuFingerprint gpuFingerprint, String ollamaVersion, List<ModelIdentity> installedModels) {
            this.gpuFingerprint = gpuFingerprint;
            this.ollamaVersion = ollamaVersion;
            this.installedModels = installedModels == null ? null
                : Collections.unmodifiableList(new ArrayList<ModelIdentity>(installedModels));
        }

        public GpuFingerprint gpuFingerprint() {
            return gpuFingerprint;
        }

        public String ollamaVersion() {
            return ollamaVersion;
        }

        public List<ModelIdentity> installedModels() {
            return installedModels;
        }
    }
}
