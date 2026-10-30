package com.aresstack.ollamamanager.infrastructure.persistence;

import java.io.File;
import java.util.Locale;
import java.util.Map;

/**
 * Resolves the user-local data directory: {@code %LOCALAPPDATA%\AresStack\OllamaManager} on Windows,
 * {@code ~/.ollama-manager} elsewhere.
 */
public final class UserDataDirectory {

    private UserDataDirectory() {
    }

    public static File resolve() {
        return resolve(System.getProperty("os.name", ""), System.getenv(), System.getProperty("user.home"));
    }

    static File resolve(String osName, Map<String, String> environment, String userHome) {
        String localAppData = environment.get("LOCALAPPDATA");
        if (osName.toLowerCase(Locale.ROOT).startsWith("windows") && localAppData != null
            && !localAppData.trim().isEmpty()) {
            return new File(new File(localAppData, "AresStack"), "OllamaManager");
        }
        return new File(userHome, ".ollama-manager");
    }
}
