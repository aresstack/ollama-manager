package com.aresstack.ollamamanager.swing.section;

import com.aresstack.askai.java8.config.AppConfigurationRepository;
import com.aresstack.askai.java8.service.DefaultAskAiService;
import com.aresstack.askai.java8.ui.ModelSearchPanel;
import com.aresstack.ollamamanager.domain.server.Endpoint;

import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.Properties;

/**
 * Hosts the model search and installer from askai-java8 unchanged: the "Hugging Face" tab (search, filters,
 * repository analysis, GGUF download, {@code /api/create}) and the "Ollama Library" tab (search, tag, pull).
 * The only adaptation is the target server: before the panel is created, its {@code ollama.baseUrl} setting is
 * pointed at the server Ollama Manager currently reports as running.
 */
final class ModelInstallDialog extends JDialog {

    private static final long serialVersionUID = 1L;
    static final String BASE_URL_KEY = "ollama.baseUrl";

    private final transient DefaultAskAiService service;

    ModelInstallDialog(Component owner, Endpoint endpoint, File configurationFile) {
        super(owner == null || owner instanceof Window ? (Window) owner : SwingUtilities.getWindowAncestor(owner),
            "Install model on " + endpoint.baseUrl(), ModalityType.MODELESS);
        targetServer(configurationFile, endpoint);
        AppConfigurationRepository repository = new AppConfigurationRepository(configurationFile);
        service = new DefaultAskAiService(repository);
        setContentPane(new ModelSearchPanel(repository, service));
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        getRootPane().registerKeyboardAction(event -> dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_IN_FOCUSED_WINDOW);
        setPreferredSize(new Dimension(1100, 760));
        pack();
        setLocationRelativeTo(owner);
    }

    @Override
    public void dispose() {
        service.shutdown();
        super.dispose();
    }

    /** Store the running server as the installer's Ollama URL, keeping every other askai-java8 setting. */
    static void targetServer(File configurationFile, Endpoint endpoint) {
        Properties properties = new Properties();
        if (configurationFile.isFile()) {
            try (InputStream in = new FileInputStream(configurationFile)) {
                properties.load(in);
            } catch (IOException e) {
                throw new UncheckedIOException("cannot read " + configurationFile, e);
            }
        }
        properties.setProperty(BASE_URL_KEY, endpoint.baseUrl());
        File directory = configurationFile.getParentFile();
        if (directory != null && !directory.isDirectory() && !directory.mkdirs()) {
            throw new UncheckedIOException(new IOException("cannot create " + directory));
        }
        try (OutputStream out = new FileOutputStream(configurationFile)) {
            properties.store(out, "Model installer settings (askai-java8)");
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write " + configurationFile, e);
        }
    }
}
