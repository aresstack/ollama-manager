package com.aresstack.ollamamanager.infrastructure.runtime;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Lists candidate Ollama executables in a fixed search order without running them.
 */
final class BinaryLocator {

    private final boolean windows;
    private final Map<String, String> environment;

    BinaryLocator(boolean windows, Map<String, String> environment) {
        this.windows = windows;
        this.environment = environment;
    }

    static boolean isWindows(String osName) {
        return osName != null && osName.toLowerCase(Locale.ROOT).startsWith("windows");
    }

    String executableName() {
        return windows ? "ollama.exe" : "ollama";
    }

    File executableIn(File directory) {
        return new File(directory, executableName());
    }

    /** Return existing executables, deduplicated by canonical path: additional directories first, then defaults. */
    List<File> candidates(List<File> additionalDirectories) {
        List<File> directories = new ArrayList<File>(additionalDirectories);
        String localAppData = variable("LOCALAPPDATA");
        if (windows && localAppData != null) {
            directories.add(new File(new File(new File(localAppData), "Programs"), "Ollama"));
        }
        String path = variable("PATH");
        if (path != null) {
            for (String entry : path.split(windows ? ";" : ":")) {
                String trimmed = stripQuotes(entry.trim());
                if (!trimmed.isEmpty()) {
                    directories.add(new File(trimmed));
                }
            }
        }
        Set<String> seen = new LinkedHashSet<String>();
        List<File> result = new ArrayList<File>();
        for (File directory : directories) {
            if (directory == null || !directory.isAbsolute()) {
                continue;
            }
            File executable = executableIn(directory);
            if (!executable.isFile()) {
                continue;
            }
            File canonical = canonical(executable);
            String key = windows ? canonical.getPath().toLowerCase(Locale.ROOT) : canonical.getPath();
            if (seen.add(key)) {
                result.add(canonical);
            }
        }
        return result;
    }

    private String variable(String name) {
        for (Map.Entry<String, String> entry : environment.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static String stripQuotes(String text) {
        if (text.length() >= 2 && text.startsWith("\"") && text.endsWith("\"")) {
            return text.substring(1, text.length() - 1);
        }
        return text;
    }

    static File canonical(File file) {
        try {
            return file.getCanonicalFile();
        } catch (IOException e) {
            return file.getAbsoluteFile();
        }
    }
}
