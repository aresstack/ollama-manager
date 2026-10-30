package com.aresstack.ollamamanager.swing;

import com.aresstack.ollamamanager.application.port.ConfigurationRepository;
import com.aresstack.ollamamanager.domain.configuration.ManagerConfiguration;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * Minimal application shell. The full console follows in issue #7.
 */
final class MainFrame extends JFrame {

    private static final long serialVersionUID = 1L;

    private final JLabel statusLabel = new JLabel("Loading configuration…");

    MainFrame(ConfigurationRepository configurationRepository, BackgroundTasks tasks) {
        super("Ollama Manager");
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        JPanel content = new JPanel(new BorderLayout());
        content.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        content.add(statusLabel, BorderLayout.CENTER);
        setContentPane(content);
        setMinimumSize(new Dimension(480, 200));
        setLocationRelativeTo(null);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent event) {
                tasks.close();
            }
        });
        tasks.run(configurationRepository::load, this::showConfiguration,
            error -> statusLabel.setText("Configuration could not be loaded: " + error.getMessage()));
    }

    private void showConfiguration(ManagerConfiguration configuration) {
        statusLabel.setText("Server address: " + configuration.desiredServerSettings().bind()
            + " — no server started yet.");
    }
}
