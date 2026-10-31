package com.aresstack.ollamamanager.infrastructure.distribution;

import com.aresstack.ollamamanager.application.common.CancellationToken;
import com.aresstack.ollamamanager.application.common.ErrorKind;
import com.aresstack.ollamamanager.application.common.OperationException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/**
 * Extracts a ZIP archive into a directory and rejects absolute paths, traversal, drive letters, alternate data
 * streams and entries that occur twice in one archive (compared case-insensitively, as on Windows). Only regular
 * files and directories are created, so symlink entries can never become links.
 */
final class SafeZipExtractor {

    static final int MAX_ENTRIES = 100000;
    static final long MAX_TOTAL_BYTES = 64L * 1024 * 1024 * 1024;

    private SafeZipExtractor() {
    }

    /**
     * Extract {@code archive} into {@code target}. Files from an earlier archive may be overwritten (add-on overlay).
     *
     * @return relative paths of the files written, using {@code /} as separator
     */
    static Set<String> extract(File archive, File target, CancellationToken cancellation) throws OperationException {
        Set<String> written = new LinkedHashSet<String>();
        Set<String> seen = new HashSet<String>();
        File root;
        try {
            root = target.getCanonicalFile();
        } catch (IOException e) {
            throw new OperationException(ErrorKind.IO, "cannot resolve " + target, e);
        }
        ZipFile zip;
        try {
            zip = new ZipFile(archive);
        } catch (ZipException e) {
            throw new OperationException(ErrorKind.INVALID_ARCHIVE, "not a valid ZIP: " + archive.getName(), e);
        } catch (IOException e) {
            throw new OperationException(ErrorKind.IO, "cannot open " + archive, e);
        }
        try {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            int count = 0;
            long total = 0;
            byte[] buffer = new byte[65536];
            while (entries.hasMoreElements()) {
                cancellation.throwIfCancelled();
                ZipEntry entry = entries.nextElement();
                if (++count > MAX_ENTRIES) {
                    throw invalid(archive, "too many entries");
                }
                String name = normalize(entry.getName(), archive);
                if (name.isEmpty()) {
                    continue;
                }
                if (!seen.add(name.toLowerCase(Locale.ROOT))) {
                    throw invalid(archive, "duplicate entry " + name);
                }
                File destination = new File(root, name.replace('/', File.separatorChar));
                requireInside(root, destination, archive, name);
                if (entry.isDirectory()) {
                    if (!destination.isDirectory() && !destination.mkdirs()) {
                        throw new OperationException(ErrorKind.IO, "cannot create " + destination);
                    }
                    continue;
                }
                File parent = destination.getParentFile();
                if (!parent.isDirectory() && !parent.mkdirs()) {
                    throw new OperationException(ErrorKind.IO, "cannot create " + parent);
                }
                if (destination.isDirectory()) {
                    throw invalid(archive, "file replaces directory " + name);
                }
                total = copy(zip, entry, destination, buffer, total, archive, cancellation);
                written.add(name);
            }
            return written;
        } finally {
            try {
                zip.close();
            } catch (IOException ignored) {
                // Read-only handle.
            }
        }
    }

    private static long copy(ZipFile zip, ZipEntry entry, File destination, byte[] buffer, long total, File archive,
                             CancellationToken cancellation) throws OperationException {
        try {
            InputStream in = zip.getInputStream(entry);
            try {
                OutputStream out = new FileOutputStream(destination);
                try {
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        total += read;
                        if (total > MAX_TOTAL_BYTES) {
                            throw invalid(archive, "uncompressed size exceeds limit");
                        }
                        out.write(buffer, 0, read);
                        cancellation.throwIfCancelled();
                    }
                } finally {
                    out.close();
                }
            } finally {
                in.close();
            }
            return total;
        } catch (ZipException e) {
            throw new OperationException(ErrorKind.INVALID_ARCHIVE, "damaged entry " + entry.getName(), e);
        } catch (IOException e) {
            throw new OperationException(ErrorKind.IO, "cannot write " + destination + ": " + e.getMessage(), e);
        }
    }

    /** Return a relative {@code /}-separated path, or the empty string for the archive root. */
    static String normalize(String rawName, File archive) throws OperationException {
        if (rawName.indexOf('\0') >= 0) {
            throw invalid(archive, "NUL in entry name");
        }
        String name = rawName.replace('\\', '/');
        if (name.startsWith("/") || name.matches("^[A-Za-z]:.*")) {
            throw invalid(archive, "absolute entry " + rawName);
        }
        StringBuilder result = new StringBuilder();
        for (String segment : name.split("/")) {
            if (segment.isEmpty() || segment.equals(".")) {
                continue;
            }
            if (segment.equals("..") || segment.indexOf(':') >= 0 || segment.trim().isEmpty()
                || segment.endsWith(".") || segment.endsWith(" ")) {
                throw invalid(archive, "unsafe entry " + rawName);
            }
            if (result.length() > 0) {
                result.append('/');
            }
            result.append(segment);
        }
        return result.toString();
    }

    private static void requireInside(File root, File destination, File archive, String name)
        throws OperationException {
        try {
            String canonical = destination.getCanonicalPath();
            if (!canonical.startsWith(root.getPath() + File.separator)) {
                throw invalid(archive, "entry escapes target " + name);
            }
        } catch (IOException e) {
            throw new OperationException(ErrorKind.IO, "cannot resolve " + destination, e);
        }
    }

    private static OperationException invalid(File archive, String message) {
        return new OperationException(ErrorKind.INVALID_ARCHIVE, archive.getName() + ": " + message);
    }
}
