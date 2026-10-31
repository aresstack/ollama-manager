package com.aresstack.ollamamanager.swing;

import com.aresstack.ollamamanager.application.configuration.ConfigurationService;
import com.aresstack.ollamamanager.application.port.ConfigurationRepository;
import com.aresstack.ollamamanager.swing.job.JobManager;
import com.aresstack.ollamamanager.swing.job.UiDispatcher;
import com.aresstack.ollamamanager.swing.shell.ConsoleServices;
import com.aresstack.ollamamanager.swing.shell.ConsoleShell;

/**
 * Main window entry used by the composition root.
 */
final class MainFrame extends ConsoleShell {

    private static final long serialVersionUID = 1L;

    /**
     * Transitional constructor for the current composition root, which only provides the repository. It creates
     * the one configuration service of this process; once the root passes {@link ConsoleServices}, remove it.
     */
    MainFrame(ConfigurationRepository configurationRepository, BackgroundTasks tasks) {
        this(ConsoleServices.builder(new ConfigurationService(configurationRepository)).build(), tasks);
    }

    MainFrame(ConsoleServices services, BackgroundTasks tasks) {
        super(services, new JobManager(tasks, UiDispatcher.swing()), tasks);
    }
}
