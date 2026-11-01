package com.aresstack.ollamamanager.swing.section;

import com.aresstack.askai.java8.config.AppConfigurationRepository;
import com.aresstack.askai.java8.service.DefaultAskAiService;
import com.aresstack.askai.java8.ui.ModelSearchPanel;
import com.aresstack.ollamamanager.application.server.ServerLifecycleService;
import com.aresstack.ollamamanager.domain.server.Endpoint;
import com.aresstack.ollamamanager.domain.server.ServerState;
import com.aresstack.ollamamanager.domain.server.ServerStatus;
import com.aresstack.ollamamanager.swing.component.EmptyState;
import com.aresstack.ollamamanager.swing.component.SectionPanel;
import com.aresstack.ollamamanager.swing.shell.ConsoleServices;
import com.aresstack.ollamamanager.swing.shell.Section;

import javax.swing.JPanel;
import java.awt.CardLayout;
import java.awt.event.HierarchyEvent;
import java.io.File;

/**
 * Install models: the model search and installer of askai-java8, unchanged ({@code ModelSearchPanel} with the
 * "Hugging Face" tab for search, GGUF download and {@code /api/create}, and the "Ollama Library" tab). The only
 * adaptation is that askai's Ollama URL points at the server Ollama Manager reports as running.
 */
public final class InstallModelsPanel extends SectionPanel {

    private static final long serialVersionUID = 1L;
    private static final String UNAVAILABLE = "unavailable";
    private static final String INSTALLER = "installer";

    private final transient ConsoleServices services;
    private final CardLayout cards = new CardLayout();
    private final JPanel body = new JPanel(cards);
    private ModelSearchPanel installer;
    private transient DefaultAskAiService askAiService;
    private transient Endpoint builtFor;

    public InstallModelsPanel(ConsoleServices services) {
        super(Section.INSTALL.title(), Section.INSTALL.description());
        this.services = services;
        body.setOpaque(false);
        body.add(new EmptyState("No running Ollama server", "Start or attach a server in Setup first."), UNAVAILABLE);
        setBody(body);
        cards.show(body, UNAVAILABLE);
        addHierarchyListener(event -> {
            if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && isShowing()) {
                showCurrent();
            }
        });
    }

    /** Show the installer for the running server; a different server gets a fresh installer. */
    void showCurrent() {
        Endpoint endpoint = runningEndpoint();
        File configuration = services.modelInstallConfiguration();
        if (endpoint == null || configuration == null) {
            cards.show(body, UNAVAILABLE);
            return;
        }
        if (installer == null || !endpoint.equals(builtFor)) {
            if (installer != null) {
                body.remove(installer);
                askAiService.shutdown();
            }
            ModelInstallConfiguration.targetServer(configuration, endpoint);
            AppConfigurationRepository repository = new AppConfigurationRepository(configuration);
            askAiService = new DefaultAskAiService(repository);
            installer = new ModelSearchPanel(repository, askAiService);
            body.add(installer, INSTALLER);
            builtFor = endpoint;
        }
        cards.show(body, INSTALLER);
    }

    ModelSearchPanel installer() {
        return installer;
    }

    private Endpoint runningEndpoint() {
        ServerLifecycleService lifecycle = services.serverLifecycle();
        if (lifecycle == null) {
            return null;
        }
        ServerStatus status = lifecycle.status();
        return status.state() == ServerState.RUNNING && status.installation() != null
            ? status.installation().endpoint() : null;
    }
}
