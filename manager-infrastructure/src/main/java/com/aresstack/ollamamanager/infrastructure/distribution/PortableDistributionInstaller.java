package com.aresstack.ollamamanager.infrastructure.distribution;

import com.aresstack.ollamamanager.application.common.CancellationToken;
import com.aresstack.ollamamanager.application.common.ErrorKind;
import com.aresstack.ollamamanager.application.common.OperationCancelledException;
import com.aresstack.ollamamanager.application.common.OperationException;
import com.aresstack.ollamamanager.application.common.ProgressListener;
import com.aresstack.ollamamanager.application.common.TransferProgress;
import com.aresstack.ollamamanager.application.port.DistributionInstallerPort;
import com.aresstack.ollamamanager.domain.distribution.DistributionPackage;
import com.aresstack.ollamamanager.domain.distribution.OllamaRelease;
import com.aresstack.ollamamanager.domain.distribution.ReleaseAsset;
import com.aresstack.ollamamanager.domain.server.Endpoint;
import com.aresstack.ollamamanager.domain.server.InstallationKind;
import com.aresstack.ollamamanager.domain.server.OllamaInstallation;
import com.aresstack.ollamamanager.infrastructure.json.Json;
import com.aresstack.ollamamanager.infrastructure.persistence.AtomicFileStore;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Installs official Ollama Windows ZIPs below an install root:
 * <pre>
 * installRoot/versions/&lt;tag&gt;[+rocm][+mlx]/   activated versions, each with a manager marker file
 * installRoot/downloads/                     partial and verified downloads (resumable)
 * installRoot/staging/                       extraction in progress
 * installRoot/active-version.txt             directory name of the active version
 * </pre>
 * A new version is extracted into staging, validated and moved into place before the active pointer changes,
 * so a failed install leaves the previous version active. Old versions stay for rollback.
 */
public final class PortableDistributionInstaller implements DistributionInstallerPort {

    static final String MARKER = ".ollama-manager-install.json";
    static final String NOTICE = "OLLAMA-NOTICE.txt";
    static final String EXECUTABLE = "ollama.exe";
    static final String ACTIVE_FILE = "active-version.txt";
    /** Extracted size is estimated as this multiple of the compressed size. */
    static final int EXTRACTION_FACTOR = 3;
    private static final Logger LOG = Logger.getLogger(PortableDistributionInstaller.class.getName());

    /** Reports free space; replaceable in tests. */
    public interface DiskSpace {
        long usableBytes(File directory);
    }

    private final HttpTransport http;
    private final Platform platform;
    private final DiskSpace diskSpace;

    public PortableDistributionInstaller(HttpTransport http, Platform platform, DiskSpace diskSpace) {
        this.http = http;
        this.platform = platform;
        this.diskSpace = diskSpace;
    }

    public static PortableDistributionInstaller defaults() {
        return new PortableDistributionInstaller(HttpTransport.defaults(), Platform.current(), File::getUsableSpace);
    }

    @Override
    public OllamaInstallation install(OllamaRelease release, Set<DistributionPackage> packages, File installRoot,
                                      ProgressListener<TransferProgress> listener, CancellationToken cancellation)
        throws OperationException {
        if (!platform.isWindowsX64()) {
            throw new OperationException(ErrorKind.UNSUPPORTED_PLATFORM,
                "portable Ollama ZIPs are only supported on Windows x64, not " + platform);
        }
        try {
            DistributionPackage.requireValidSelection(packages);
        } catch (IllegalArgumentException e) {
            throw new OperationException(ErrorKind.INVALID_CONFIGURATION, e.getMessage(), e);
        }
        List<DistributionPackage> ordered = new ArrayList<DistributionPackage>(EnumSet.copyOf(packages));
        List<ReleaseAsset> assets = new ArrayList<ReleaseAsset>();
        long totalBytes = 0;
        for (DistributionPackage pkg : ordered) {
            ReleaseAsset asset = release.assetFor(pkg);
            if (asset == null) {
                throw new OperationException(ErrorKind.NOT_FOUND,
                    release.version() + " does not publish " + pkg.assetName());
            }
            if (asset.sha256() == null) {
                throw new OperationException(ErrorKind.CHECKSUM_MISMATCH,
                    "no official SHA-256 for " + asset.name() + " in " + release.version());
            }
            if (!asset.downloadUrl().contains("/download/" + release.version() + "/")) {
                throw new OperationException(ErrorKind.INVALID_STATE, asset.name()
                    + " does not belong to release " + release.version() + ": " + asset.downloadUrl());
            }
            assets.add(asset);
            totalBytes += asset.sizeBytes();
        }
        Layout layout = new Layout(installRoot);
        String directoryName = directoryName(release.version(), ordered);
        File versionDirectory = new File(layout.versions, directoryName);
        if (versionDirectory.exists()) {
            throw new OperationException(ErrorKind.INVALID_STATE, directoryName + " is already installed");
        }
        layout.create();
        requireSpace(layout.root, totalBytes * (1 + EXTRACTION_FACTOR));

        List<File> downloads = new ArrayList<File>();
        File staging = new File(layout.staging, directoryName + "-" + System.nanoTime());
        boolean done = false;
        try {
            long completedBefore = 0;
            for (ReleaseAsset asset : assets) {
                downloads.add(download(asset, layout, completedBefore, totalBytes, listener, cancellation));
                completedBefore += asset.sizeBytes();
            }
            if (!staging.mkdirs()) {
                throw new OperationException(ErrorKind.IO, "cannot create " + staging);
            }
            List<String> files = new ArrayList<String>();
            for (int i = 0; i < assets.size(); i++) {
                listener.onProgress(new TransferProgress("extracting " + assets.get(i).name(), totalBytes, totalBytes));
                for (String file : SafeZipExtractor.extract(downloads.get(i), staging, cancellation)) {
                    if (!files.contains(file)) {
                        files.add(file);
                    }
                }
            }
            File executable = new File(staging, EXECUTABLE);
            if (!executable.isFile() || executable.length() == 0) {
                throw new OperationException(ErrorKind.INVALID_ARCHIVE,
                    assets.get(0).name() + " does not contain " + EXECUTABLE + " at its root");
            }
            cancellation.throwIfCancelled();
            writeText(new File(staging, NOTICE), notice(release, assets));
            writeText(new File(staging, MARKER), marker(release, ordered, assets, files));
            listener.onProgress(new TransferProgress("activating " + directoryName, totalBytes, totalBytes));
            // Commit point: cancellation is honoured up to here; once the move starts the install completes.
            cancellation.throwIfCancelled();
            move(staging, versionDirectory);
            try {
                writeActive(layout, directoryName);
            } catch (OperationException e) {
                deleteTree(versionDirectory);
                throw e;
            }
            done = true;
            LOG.info("activated portable Ollama " + directoryName + " in " + versionDirectory);
            return installation(release.version(), versionDirectory);
        } finally {
            deleteTreeQuietly(staging);
            if (done || cancellation.isCancelled()) {
                for (ReleaseAsset asset : assets) {
                    layout.download(asset).delete();
                    layout.partial(asset).delete();
                }
            }
        }
    }

    @Override
    public List<OllamaInstallation> installedVersions(File installRoot) throws OperationException {
        Layout layout = new Layout(installRoot);
        File[] children = layout.versions.listFiles();
        if (children == null) {
            return Collections.emptyList();
        }
        String active = readActive(layout);
        List<String> names = new ArrayList<String>();
        for (File child : children) {
            if (child.isDirectory() && new File(child, MARKER).isFile()) {
                names.add(child.getName());
            }
        }
        Collections.sort(names, Collections.reverseOrder());
        if (active != null && names.remove(active)) {
            names.add(0, active);
        }
        List<OllamaInstallation> result = new ArrayList<OllamaInstallation>();
        for (String name : names) {
            File directory = new File(layout.versions, name);
            result.add(installation(String.valueOf(readMarker(directory).get("version")), directory));
        }
        return result;
    }

    @Override
    public void uninstall(OllamaInstallation installation, File installRoot) throws OperationException {
        Layout layout = new Layout(installRoot);
        File directory = requireManaged(installation, layout);
        if (directory.getName().equals(readActive(layout))) {
            throw new OperationException(ErrorKind.INVALID_STATE,
                "cannot remove the active version " + directory.getName() + "; activate another one first");
        }
        Object files = readMarker(directory).get("files");
        List<String> remaining = new ArrayList<String>();
        if (files instanceof List) {
            for (Object file : (List<?>) files) {
                File target = new File(directory, String.valueOf(file).replace('/', File.separatorChar));
                if (isInside(directory, target) && !Files.isSymbolicLink(target.toPath())
                    && target.exists() && !target.delete()) {
                    remaining.add(String.valueOf(file));
                }
            }
        }
        if (!remaining.isEmpty()) {
            // Keep the marker so the version stays listed and the uninstall can be retried.
            throw new OperationException(ErrorKind.IO, "cannot delete " + remaining.size() + " files in "
                + directory + " (in use?), for example " + remaining.get(0));
        }
        new File(directory, NOTICE).delete();
        new File(directory, MARKER).delete();
        deleteEmptyDirectories(directory);
        if (directory.exists()) {
            LOG.warning("kept " + directory + " because it contains files the manager did not create");
        }
    }

    /** Make an installed version the active one, for example to roll back after an update. */
    public void activate(OllamaInstallation installation, File installRoot) throws OperationException {
        Layout layout = new Layout(installRoot);
        File directory = requireManaged(installation, layout);
        if (!new File(directory, EXECUTABLE).isFile()) {
            throw new OperationException(ErrorKind.INVALID_STATE, directory + " has no " + EXECUTABLE);
        }
        writeActive(layout, directory.getName());
    }

    /** Return the active installation, or {@code null} if none is active. */
    public OllamaInstallation active(File installRoot) throws OperationException {
        List<OllamaInstallation> all = installedVersions(installRoot);
        String active = readActive(new Layout(installRoot));
        if (!all.isEmpty() && active != null && all.get(0).binaryDirectory().getName().equals(active)) {
            return all.get(0);
        }
        return null;
    }

    private File download(ReleaseAsset asset, Layout layout, long completedBefore, long totalBytes,
                          ProgressListener<TransferProgress> listener, CancellationToken cancellation)
        throws OperationException {
        File target = layout.download(asset);
        File partial = layout.partial(asset);
        if (target.isFile()) {
            if (sha256(target, cancellation).equals(asset.sha256())) {
                LOG.info("reusing verified " + target);
                return target;
            }
            target.delete();
        }
        long existing = partial.isFile() ? partial.length() : 0;
        if (existing > 0 && existing >= asset.sizeBytes() && asset.sizeBytes() > 0) {
            // A complete partial cannot be resumed with a Range request; verify it locally instead.
            if (existing == asset.sizeBytes() && sha256(partial, cancellation).equals(asset.sha256())) {
                LOG.info("verified " + asset.name() + " sha256 " + asset.sha256() + " from earlier download");
                move(partial, target);
                return target;
            }
            partial.delete();
            existing = 0;
        }
        String phase = "downloading " + asset.name();
        LOG.info("downloading " + asset.downloadUrl() + (existing > 0 ? " from byte " + existing : ""));
        HttpTransport.Response response = http.get(asset.downloadUrl(), existing > 0
            ? HttpTransport.headers("Accept", "application/octet-stream", "Range", "bytes=" + existing + "-")
            : HttpTransport.headers("Accept", "application/octet-stream"), cancellation);
        try {
            boolean append = existing > 0 && response.status() == 206
                && String.valueOf(response.header("Content-Range")).startsWith("bytes " + existing + "-");
            if (!append) {
                existing = 0;
            }
            long expectedEnd = response.contentLength() < 0 ? -1 : existing + response.contentLength();
            long written = existing;
            OutputStream out = openPartial(partial, append);
            try {
                InputStream in = response.body();
                byte[] buffer = new byte[65536];
                int read;
                long lastReport = 0;
                while ((read = in.read(buffer)) != -1) {
                    cancellation.throwIfCancelled();
                    out.write(buffer, 0, read);
                    written += read;
                    if (written - lastReport >= 1 << 20) {
                        lastReport = written;
                        listener.onProgress(new TransferProgress(phase, completedBefore + written, totalBytes));
                    }
                }
            } catch (IOException | RuntimeException e) {
                if (cancellation.isCancelled()) {
                    throw new OperationCancelledException("download cancelled: " + asset.name());
                }
                throw writeOrReadFailure(e, partial, asset);
            } finally {
                closeQuietly(out);
            }
            cancellation.throwIfCancelled();
            if ((expectedEnd >= 0 && written != expectedEnd)
                || (asset.sizeBytes() > 0 && written != asset.sizeBytes())) {
                throw new OperationException(ErrorKind.CONNECTION_FAILED, "download of " + asset.name()
                    + " ended after " + written + " of " + asset.sizeBytes() + " bytes; retry resumes it");
            }
        } finally {
            response.close();
        }
        listener.onProgress(new TransferProgress("verifying sha256 " + asset.name(),
            completedBefore + asset.sizeBytes(), totalBytes));
        String actual = sha256(partial, cancellation);
        if (!actual.equals(asset.sha256())) {
            partial.delete();
            throw new OperationException(ErrorKind.CHECKSUM_MISMATCH, asset.name() + ": expected sha256 "
                + asset.sha256() + " but downloaded " + actual);
        }
        LOG.info("verified " + asset.name() + " sha256 " + actual + " from " + asset.downloadUrl());
        move(partial, target);
        return target;
    }

    private static OutputStream openPartial(File partial, boolean append) throws OperationException {
        try {
            return new FileOutputStream(partial, append);
        } catch (IOException e) {
            throw new OperationException(ErrorKind.IO, "cannot write " + partial, e);
        }
    }

    private OperationException writeOrReadFailure(Exception e, File partial, ReleaseAsset asset) {
        File directory = partial.getParentFile();
        if (diskSpace.usableBytes(directory) < 1024 * 1024) {
            return new OperationException(ErrorKind.INSUFFICIENT_DISK, "disk full while downloading " + asset.name(), e);
        }
        return new OperationException(ErrorKind.CONNECTION_FAILED,
            "download of " + asset.name() + " interrupted: " + e.getMessage() + "; retry resumes it", e);
    }

    private void requireSpace(File directory, long required) throws OperationException {
        long usable = diskSpace.usableBytes(directory);
        if (usable < required) {
            throw new OperationException(ErrorKind.INSUFFICIENT_DISK, "need about " + required
                + " bytes free in " + directory + " but only " + usable + " are available");
        }
    }

    static String directoryName(String version, List<DistributionPackage> packages) throws OperationException {
        if (!version.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")) {
            throw new OperationException(ErrorKind.INVALID_CONFIGURATION, "unsafe release tag " + version);
        }
        StringBuilder name = new StringBuilder(version);
        for (DistributionPackage pkg : packages) {
            if (pkg.isAddOn()) {
                name.append('+').append(pkg.name().toLowerCase(java.util.Locale.ROOT));
            }
        }
        return name.toString();
    }

    private static OllamaInstallation installation(String version, File directory) {
        return new OllamaInstallation(InstallationKind.MANAGED_PORTABLE, version, directory.getAbsoluteFile(),
            Endpoint.DEFAULT);
    }

    private static File requireManaged(OllamaInstallation installation, Layout layout) throws OperationException {
        if (installation.kind() != InstallationKind.MANAGED_PORTABLE || installation.binaryDirectory() == null) {
            throw new OperationException(ErrorKind.NOT_OWNED, "not a portable installation: " + installation);
        }
        File directory;
        try {
            directory = installation.binaryDirectory().getCanonicalFile();
            File parent = directory.getParentFile();
            if (parent == null || !parent.equals(layout.versions.getCanonicalFile())
                || !new File(directory, MARKER).isFile()) {
                throw new OperationException(ErrorKind.NOT_OWNED,
                    installation.binaryDirectory() + " was not created by the manager below " + layout.root);
            }
        } catch (IOException e) {
            throw new OperationException(ErrorKind.IO, "cannot resolve " + installation.binaryDirectory(), e);
        }
        return directory;
    }

    private static Map<?, ?> readMarker(File directory) throws OperationException {
        try {
            Object value = Json.parse(new String(Files.readAllBytes(new File(directory, MARKER).toPath()),
                StandardCharsets.UTF_8));
            if (value instanceof Map) {
                return (Map<?, ?>) value;
            }
        } catch (IOException | IllegalArgumentException e) {
            throw new OperationException(ErrorKind.IO, "unreadable marker in " + directory, e);
        }
        throw new OperationException(ErrorKind.IO, "unreadable marker in " + directory);
    }

    private static String marker(OllamaRelease release, List<DistributionPackage> packages, List<ReleaseAsset> assets,
                                 List<String> files) {
        Map<String, Object> root = new LinkedHashMap<String, Object>();
        root.put("formatVersion", 1L);
        root.put("version", release.version());
        List<String> packageNames = new ArrayList<String>();
        for (DistributionPackage pkg : packages) {
            packageNames.add(pkg.name());
        }
        root.put("packages", packageNames);
        List<Object> sources = new ArrayList<Object>();
        for (ReleaseAsset asset : assets) {
            Map<String, Object> source = new LinkedHashMap<String, Object>();
            source.put("name", asset.name());
            source.put("url", asset.downloadUrl());
            source.put("sizeBytes", asset.sizeBytes());
            source.put("sha256", asset.sha256());
            sources.add(source);
        }
        root.put("sources", sources);
        root.put("installedAt", java.time.Instant.now().toString());
        root.put("files", files);
        return Json.write(root);
    }

    private static String notice(OllamaRelease release, List<ReleaseAsset> assets) {
        StringBuilder text = new StringBuilder();
        text.append("This directory contains an unmodified official Ollama distribution (").append(release.version())
            .append(").\nOllama is Copyright (c) Ollama and licensed under the MIT License:\n")
            .append("https://github.com/ollama/ollama/blob/main/LICENSE\n\nSources verified by SHA-256:\n");
        for (ReleaseAsset asset : assets) {
            text.append(asset.sha256()).append("  ").append(asset.downloadUrl()).append('\n');
        }
        return text.toString();
    }

    private static String readActive(Layout layout) throws OperationException {
        try {
            String value = new AtomicFileStore(layout.activeFile).readPrimary();
            return value == null || value.trim().isEmpty() ? null : value.trim();
        } catch (IOException e) {
            throw new OperationException(ErrorKind.IO, "cannot read " + layout.activeFile, e);
        }
    }

    private static void writeActive(Layout layout, String directoryName) throws OperationException {
        try {
            new AtomicFileStore(layout.activeFile).write(directoryName + "\n", true);
        } catch (IOException e) {
            throw new OperationException(ErrorKind.IO, "cannot write " + layout.activeFile, e);
        }
    }

    static String sha256(File file, CancellationToken cancellation) throws OperationException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            InputStream in = new FileInputStream(file);
            try {
                byte[] buffer = new byte[65536];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                    cancellation.throwIfCancelled();
                }
            } finally {
                in.close();
            }
            StringBuilder hex = new StringBuilder();
            for (byte b : digest.digest()) {
                hex.append(String.format("%02x", b & 0xff));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        } catch (IOException e) {
            throw new OperationException(ErrorKind.IO, "cannot read " + file, e);
        }
    }

    private static void writeText(File file, String text) throws OperationException {
        try {
            Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new OperationException(ErrorKind.IO, "cannot write " + file, e);
        }
    }

    private static void move(File source, File target) throws OperationException {
        try {
            try {
                Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(source.toPath(), target.toPath());
            }
        } catch (IOException e) {
            throw new OperationException(ErrorKind.IO, "cannot move " + source + " to " + target, e);
        }
    }

    private static boolean isInside(File root, File candidate) {
        try {
            return candidate.getCanonicalPath().startsWith(root.getCanonicalPath() + File.separator);
        } catch (IOException e) {
            return false;
        }
    }

    private static void deleteEmptyDirectories(File directory) {
        File[] children = directory.listFiles();
        if (children != null) {
            for (File child : children) {
                if (child.isDirectory() && !Files.isSymbolicLink(child.toPath())) {
                    deleteEmptyDirectories(child);
                }
            }
        }
        directory.delete();
    }

    private static void deleteTree(File directory) throws OperationException {
        if (!directory.exists()) {
            return;
        }
        try {
            Files.walkFileTree(directory.toPath(), new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException failure) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new OperationException(ErrorKind.IO, "cannot delete " + directory, e);
        }
    }

    private static void deleteTreeQuietly(File directory) {
        try {
            deleteTree(directory);
        } catch (OperationException e) {
            LOG.warning(e.getMessage());
        }
    }

    private static void closeQuietly(OutputStream stream) {
        try {
            stream.close();
        } catch (IOException ignored) {
            // Data loss is caught by the size and digest checks.
        }
    }

    private static final class Layout {
        final File root;
        final File versions;
        final File downloads;
        final File staging;
        final File activeFile;

        Layout(File installRoot) {
            this.root = installRoot.getAbsoluteFile();
            this.versions = new File(root, "versions");
            this.downloads = new File(root, "downloads");
            this.staging = new File(root, "staging");
            this.activeFile = new File(root, ACTIVE_FILE);
        }

        void create() throws OperationException {
            for (File directory : new File[] {versions, downloads, staging}) {
                if (!directory.isDirectory() && !directory.mkdirs()) {
                    throw new OperationException(ErrorKind.IO, "cannot create " + directory);
                }
            }
        }

        File download(ReleaseAsset asset) {
            return new File(downloads, asset.sha256() + "-" + asset.name());
        }

        File partial(ReleaseAsset asset) {
            return new File(downloads, asset.sha256() + "-" + asset.name() + ".part");
        }
    }
}
