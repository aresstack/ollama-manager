package com.aresstack.ollamamanager.swing;

import com.aresstack.ollamamanager.application.port.ConfigurationRepository;
import com.aresstack.ollamamanager.domain.server.ServerSettings;
import com.aresstack.ollamamanager.infrastructure.persistence.JsonConfigurationRepository;
import com.aresstack.ollamamanager.infrastructure.persistence.UserDataDirectory;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.util.Arrays;

/**
 * Entry point and composition root: wires adapters to ports and opens the Swing shell.
 */
public final class OllamaManagerApp {

    static final String SMOKE_TEST_FLAG = "--smoke-test";

    private OllamaManagerApp() {
    }

    public static void main(String[] args) {
        if (Arrays.asList(args).contains(SMOKE_TEST_FLAG)) {
            System.out.println(smokeTestReport());
            return;
        }
        ConfigurationRepository repository = new JsonConfigurationRepository(UserDataDirectory.resolve());
        SwingUtilities.invokeLater(() -> openMainFrame(repository));
    }

    /**
     * Verify that all modules load on the current JVM without opening a window.
     */
    static String smokeTestReport() {
        return "Ollama Manager smoke test OK"
            + " | java.version=" + System.getProperty("java.version")
            + " | default environment=" + ServerSettings.defaults().toEnvironment()
            + " | repository=" + JsonConfigurationRepository.class.getSimpleName();
    }

    private static void openMainFrame(ConfigurationRepository repository) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // Keep the default look and feel.
        }
        new MainFrame(repository, new BackgroundTasks()).setVisible(true);
    }
}
