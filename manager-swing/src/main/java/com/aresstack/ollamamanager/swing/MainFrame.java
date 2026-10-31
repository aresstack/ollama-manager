package com.aresstack.ollamamanager.swing;

import com.aresstack.ollamamanager.swing.job.JobManager;
import com.aresstack.ollamamanager.swing.job.UiDispatcher;
import com.aresstack.ollamamanager.swing.shell.ConsoleServices;
import com.aresstack.ollamamanager.swing.shell.ConsoleShell;

/**
 * Main window entry used by the composition root.
 */
final class MainFrame extends ConsoleShell {

    private static final long serialVersionUID = 1L;

    MainFrame(ConsoleServices services, BackgroundTasks tasks) {
        super(services, new JobManager(tasks, UiDispatcher.swing()), tasks);
    }
}
