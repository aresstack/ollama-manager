package com.aresstack.ollamamanager.acceptance;

import com.aresstack.ollamamanager.application.port.ManagedServer;
import com.aresstack.ollamamanager.domain.server.Endpoint;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Safety checks every write operation of the native acceptance run must pass. A failed check throws
 * {@link IllegalStateException} and aborts the run before any request or file change happens.
 */
final class AcceptanceGuard {

    static final String WORK_DIR_PREFIX = "ollama-acceptance-";

    private final File workDir;
    private final List<File> protectedRoots;
    private final int referencePort;

    /**
     * @param workDir        fresh directory that holds everything the run creates
     * @param protectedRoots model and install folders of the user; the run must never write inside them
     * @param referencePort  port of the read-only reference server; never a target of a start or write
     */
    AcceptanceGuard(File workDir, Collection<File> protectedRoots, int referencePort) {
        this.workDir = canonical(workDir);
        this.referencePort = referencePort;
        this.protectedRoots = new ArrayList<File>();
        for (File root : protectedRoots) {
            if (root != null) {
                this.protectedRoots.add(canonical(root));
            }
        }
        if (!this.workDir.getName().startsWith(WORK_DIR_PREFIX)) {
            throw new IllegalStateException("work directory name must start with " + WORK_DIR_PREFIX + ": " + workDir);
        }
        for (File root : this.protectedRoots) {
            if (overlaps(this.workDir, root)) {
                throw new IllegalStateException("work directory " + workDir + " overlaps protected folder " + root);
            }
        }
    }

    /** Default protected roots: {@code OLLAMA_MODELS} and {@code ~/.ollama}. */
    static List<File> defaultProtectedRoots(String ollamaModelsEnv, String userHome) {
        List<File> roots = new ArrayList<File>();
        if (ollamaModelsEnv != null && !ollamaModelsEnv.trim().isEmpty()) {
            roots.add(new File(ollamaModelsEnv.trim()));
        }
        if (userHome != null) {
            roots.add(new File(userHome, ".ollama"));
        }
        return roots;
    }

    File workDir() {
        return workDir;
    }

    /** Require a work directory that is missing or empty, so nothing pre-existing can be deleted. */
    void requireFreshWorkDir() {
        String[] entries = workDir.list();
        if (workDir.exists() && (entries == null || entries.length > 0)) {
            throw new IllegalStateException("work directory must be new or empty: " + workDir);
        }
    }

    /** Require a port for the test server that is not the default Ollama port. */
    static void requireTestPort(int port) {
        if (port == Endpoint.DEFAULT_PORT || port < 1024 || port > 65535) {
            throw new IllegalStateException("test port must be 1024-65535 and not " + Endpoint.DEFAULT_PORT + ": " + port);
        }
    }

    /** Require a folder below the work directory that is not protected. */
    File requireInsideWorkDir(File directory) {
        File candidate = canonical(directory);
        if (!isInside(candidate, workDir) || candidate.equals(workDir)) {
            throw new IllegalStateException(directory + " is not below the work directory " + workDir);
        }
        for (File root : protectedRoots) {
            if (overlaps(candidate, root)) {
                throw new IllegalStateException(directory + " overlaps protected folder " + root);
            }
        }
        return candidate;
    }

    /**
     * Require an endpoint that may receive a server start or a write request: loopback, a valid test port,
     * and neither the default port nor the reference port. Checked without probing, so a reference server
     * that is currently down or unreachable never makes its port usable.
     */
    void requireWritableEndpoint(Endpoint endpoint) {
        if (!endpoint.isLoopback()) {
            throw new IllegalStateException("test servers and write requests only use loopback: " + endpoint);
        }
        requireTestPort(endpoint.port());
        if (endpoint.port() == referencePort) {
            throw new IllegalStateException("port " + endpoint.port() + " is the read-only reference port");
        }
    }

    /**
     * Require that {@code endpoint} is writable and belongs to a live server this run started. Only such an
     * endpoint may receive pull, preload, unload or delete requests.
     */
    void requireOwnedTestEndpoint(Endpoint endpoint, ManagedServer owner) {
        requireWritableEndpoint(endpoint);
        if (owner == null || !owner.isAlive() || !endpoint.equals(owner.installation().endpoint())) {
            throw new IllegalStateException("no live test server owned by this run listens at " + endpoint);
        }
    }

    /**
     * Require that a model may be deleted: it did not exist before the run, the server uses a model root
     * below the work directory, and that root holds the model's manifest.
     */
    void requireDeletableTestModel(String modelName, Collection<String> namesBeforeRun, File modelRoot) {
        String normalized = normalizeName(modelName);
        for (String existing : namesBeforeRun) {
            if (normalizeName(existing).equals(normalized)) {
                throw new IllegalStateException(modelName + " existed before the run and must not be deleted");
            }
        }
        File root = requireInsideWorkDir(modelRoot);
        File manifest = manifestFile(root, normalized);
        if (!manifest.isFile()) {
            throw new IllegalStateException("manifest of " + modelName + " not found in test model root: " + manifest);
        }
    }

    /** Add the implicit {@code :latest} tag. */
    static String normalizeName(String name) {
        String trimmed = name.trim();
        int slash = trimmed.lastIndexOf('/');
        return trimmed.indexOf(':', slash + 1) < 0 ? trimmed + ":latest" : trimmed;
    }

    /** Map {@code model:tag} or {@code namespace/model:tag} to its manifest below the Ollama model root. */
    static File manifestFile(File modelRoot, String normalizedName) {
        int colon = normalizedName.lastIndexOf(':');
        String repository = normalizedName.substring(0, colon);
        String tag = normalizedName.substring(colon + 1);
        String[] parts = repository.split("/");
        String path;
        if (parts.length == 1) {
            path = "registry.ollama.ai/library/" + repository;
        } else if (parts.length == 2) {
            path = "registry.ollama.ai/" + repository;
        } else {
            path = repository;
        }
        return new File(new File(new File(modelRoot, "manifests"), path.replace('/', File.separatorChar)), tag);
    }

    private static boolean overlaps(File a, File b) {
        return isInside(a, b) || isInside(b, a);
    }

    private static boolean isInside(File candidate, File parent) {
        for (File current = candidate; current != null; current = current.getParentFile()) {
            if (current.equals(parent)) {
                return true;
            }
        }
        return false;
    }

    static File canonical(File file) {
        try {
            return file.getCanonicalFile();
        } catch (IOException e) {
            return file.getAbsoluteFile();
        }
    }
}
