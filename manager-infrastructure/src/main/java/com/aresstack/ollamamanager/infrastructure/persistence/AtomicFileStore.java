package com.aresstack.ollamamanager.infrastructure.persistence;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Writes a text file by replacing it atomically and keeps the previous version as {@code .bak}
 * so a damaged primary file can be recovered.
 */
public final class AtomicFileStore {

    private final File file;
    private final File backup;

    public AtomicFileStore(File file) {
        this.file = file.getAbsoluteFile();
        this.backup = new File(this.file.getPath() + ".bak");
    }

    public File file() {
        return file;
    }

    public File backup() {
        return backup;
    }

    /** Return the primary file content, or {@code null} if it does not exist. */
    public String readPrimary() throws IOException {
        return read(file);
    }

    /** Return the backup content, or {@code null} if it does not exist. */
    public String readBackup() throws IOException {
        return read(backup);
    }

    /**
     * Write to a temporary file in the same directory, flush it to disk, keep the old file as backup and
     * move the new file into place. Pass {@code rotateBackup = false} when the current primary is damaged, so
     * the last valid backup survives.
     */
    public void write(String content, boolean rotateBackup) throws IOException {
        File directory = file.getParentFile();
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("cannot create directory " + directory);
        }
        File temp = File.createTempFile(file.getName(), ".tmp", directory);
        try {
            writeSynced(temp, content);
            if (rotateBackup && file.exists()) {
                Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            move(temp, file);
        } finally {
            Files.deleteIfExists(temp.toPath());
        }
    }

    private static void writeSynced(File target, String content) throws IOException {
        FileOutputStream stream = new FileOutputStream(target);
        try {
            stream.write(content.getBytes(StandardCharsets.UTF_8));
            stream.getFD().sync();
        } finally {
            stream.close();
        }
    }

    private static void move(File source, File target) throws IOException {
        try {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String read(File source) throws IOException {
        if (!source.isFile()) {
            return null;
        }
        return new String(Files.readAllBytes(source.toPath()), StandardCharsets.UTF_8);
    }
}
