package com.aresstack.ollamamanager.application.server;

import com.aresstack.ollamamanager.application.common.CancellationToken;
import com.aresstack.ollamamanager.application.common.ErrorKind;
import com.aresstack.ollamamanager.application.common.LifecycleLock;
import com.aresstack.ollamamanager.application.common.OperationException;
import com.aresstack.ollamamanager.application.configuration.ConfigurationService;
import com.aresstack.ollamamanager.application.configuration.ExistingSettingsImport;
import com.aresstack.ollamamanager.domain.server.RuntimeOwnership;
import com.aresstack.ollamamanager.domain.server.ServerSettingCatalog;
import com.aresstack.ollamamanager.domain.server.ServerSettings;
import com.aresstack.ollamamanager.domain.server.ServerState;
import com.aresstack.ollamamanager.domain.server.ServerStatus;

/**
 * Raise {@code OLLAMA_NUM_PARALLEL} of the running server so a benchmark can measure as many parallel chats as
 * asked for: the value is stored as the desired setting and the server is restarted with it. An attached external
 * server is taken over the way the dashboard's restart does it, keeping the settings it was configured with.
 * <p>
 * Methods block and must run off the UI thread. The store and the restart run under the lifecycle lock, so no
 * other stop, restart or profile activation interleaves. Callers must ask the user before calling, because a
 * restart aborts running requests.
 */
public final class ServerParallelismService {

    private final ServerLifecycleService lifecycle;
    private final ConfigurationService configuration;
    private final ExistingSettingsImport existing;
    private final LifecycleLock lock;

    /**
     * @param existing import of an external server's configured settings before it is taken over; {@code null}
     *                 when the manager cannot read them
     * @param lock     the lock the lifecycle service uses
     */
    public ServerParallelismService(ServerLifecycleService lifecycle, ConfigurationService configuration,
                                    ExistingSettingsImport existing, LifecycleLock lock) {
        if (lifecycle == null || configuration == null || lock == null) {
            throw new IllegalArgumentException("lifecycle, configuration and lock must not be null");
        }
        this.lifecycle = lifecycle;
        this.configuration = configuration;
        this.existing = existing;
        this.lock = lock;
    }

    /** @return whether the server in {@code status} can be restarted with another value by this service */
    public boolean canApply(ServerStatus status) {
        if (status == null || status.state() != ServerState.RUNNING || status.installation() == null) {
            return false;
        }
        return status.ownership() == RuntimeOwnership.OWNED
            || status.ownership() == RuntimeOwnership.EXTERNAL && lifecycle.canControlExternal();
    }

    /**
     * Store {@code numParallel} as the desired {@code OLLAMA_NUM_PARALLEL} and restart the running server with the
     * stored settings. The value is stored first, so a failed restart leaves it to apply on the next start.
     *
     * @return the status after the restart
     * @throws OperationException {@code INVALID_CONFIGURATION} for a value outside 1-256, {@code INVALID_STATE}
     *                            without a running server or while another lifecycle operation holds the lock,
     *                            {@code NOT_OWNED} for an external server the manager cannot control, or the
     *                            failure of the restart
     */
    public ServerStatus apply(final int numParallel, final CancellationToken cancellation) throws OperationException {
        String invalid = ServerSettingCatalog.PARALLELISM.validate(numParallel);
        if (invalid != null) {
            throw new OperationException(ErrorKind.INVALID_CONFIGURATION, invalid);
        }
        return lock.run("set OLLAMA_NUM_PARALLEL=" + numParallel, () -> {
            ServerStatus status = lifecycle.status();
            if (status.state() != ServerState.RUNNING || status.installation() == null) {
                throw new OperationException(ErrorKind.INVALID_STATE, "no running server to restart");
            }
            if (status.ownership() == RuntimeOwnership.EXTERNAL) {
                if (!lifecycle.canControlExternal()) {
                    throw new OperationException(ErrorKind.NOT_OWNED,
                        "this server was not started by the manager and cannot be restarted from here");
                }
                if (existing != null) {
                    // The replacement must keep what the installation is configured with, above all its model folder.
                    existing.importUnchosen(cancellation);
                }
            }
            cancellation.throwIfCancelled();
            ServerSettings target = configuration.update(current -> current.withDesiredServerSettings(
                current.desiredServerSettings().with(ServerSettingCatalog.PARALLELISM, numParallel)))
                .desiredServerSettings();
            return lifecycle.restart(target, cancellation);
        });
    }
}
