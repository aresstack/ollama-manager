package com.aresstack.ollamamanager.swing.shell;

import java.awt.event.KeyEvent;

/**
 * Top-level areas of the console, in navigation order. Ctrl+1 … Ctrl+7 select them.
 */
public enum Section {
    DASHBOARD("Dashboard", "Server status, owner and hardware at a glance.", KeyEvent.VK_1),
    SETUP("Setup", "Use an existing Ollama installation or install a portable one.", KeyEvent.VK_2),
    MODELS("Models", "Installed and loaded models: pull, load, unload and delete.", KeyEvent.VK_3),
    SERVER_SETTINGS("Server settings", "Server-wide options. Changes take effect after a restart.", KeyEvent.VK_4),
    PROFILES("Profiles", "Model-specific optimisation profiles and scenario presets.", KeyEvent.VK_5),
    JOBS("Jobs & logs", "Running and finished operations, progress and server log.", KeyEvent.VK_6),
    BENCHMARKS("Benchmarks", "Measure and compare configurations (issue #8).", KeyEvent.VK_7);

    private final String title;
    private final String description;
    private final int shortcutKey;

    Section(String title, String description, int shortcutKey) {
        this.title = title;
        this.description = description;
        this.shortcutKey = shortcutKey;
    }

    public String title() {
        return title;
    }

    public String description() {
        return description;
    }

    public int shortcutKey() {
        return shortcutKey;
    }

    @Override
    public String toString() {
        return title;
    }
}
