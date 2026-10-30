package com.aresstack.ollamamanager.infrastructure.persistence;

import com.aresstack.ollamamanager.application.common.ErrorKind;
import com.aresstack.ollamamanager.application.common.OperationException;
import com.aresstack.ollamamanager.application.port.ConfigurationRepository;
import com.aresstack.ollamamanager.domain.configuration.ManagerConfiguration;
import com.aresstack.ollamamanager.infrastructure.json.Json;

import java.io.File;
import java.io.IOException;

/**
 * Stores the configuration as {@code configuration.json} with atomic replacement. A damaged primary file
 * is recovered from the backup written by the previous save.
 */
public final class JsonConfigurationRepository implements ConfigurationRepository {

    public static final String FILE_NAME = "configuration.json";

    private final AtomicFileStore store;

    public JsonConfigurationRepository(File dataDirectory) {
        this.store = new AtomicFileStore(new File(dataDirectory, FILE_NAME));
    }

    @Override
    public synchronized ManagerConfiguration load() throws OperationException {
        String primary = read(true);
        if (primary == null && read(false) == null) {
            return ManagerConfiguration.defaults();
        }
        try {
            return decode(primary);
        } catch (RuntimeException primaryFailure) {
            return recoverFromBackup(primaryFailure);
        }
    }

    @Override
    public synchronized void save(ManagerConfiguration configuration) throws OperationException {
        try {
            store.write(Json.write(ConfigurationDocumentMapper.toDocument(configuration)) + "\n");
        } catch (IOException e) {
            throw new OperationException(ErrorKind.IO, "cannot save configuration to " + store.file(), e);
        }
    }

    private ManagerConfiguration recoverFromBackup(RuntimeException primaryFailure)
        throws OperationException {
        try {
            return decode(read(false));
        } catch (RuntimeException backupFailure) {
            throw new OperationException(ErrorKind.INVALID_CONFIGURATION,
                "configuration " + store.file() + " is damaged and no valid backup exists: "
                    + primaryFailure.getMessage(), primaryFailure);
        }
    }

    private static ManagerConfiguration decode(String content) {
        if (content == null) {
            throw new IllegalArgumentException("file is missing");
        }
        return ConfigurationDocumentMapper.fromDocument(Json.parse(content));
    }

    private String read(boolean primary) throws OperationException {
        try {
            return primary ? store.readPrimary() : store.readBackup();
        } catch (IOException e) {
            throw new OperationException(ErrorKind.IO, "cannot read configuration " + store.file(), e);
        }
    }
}
