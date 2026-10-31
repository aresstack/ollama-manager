package com.aresstack.ollamamanager.swing.component;

import javax.swing.JOptionPane;
import java.awt.Component;

/**
 * Uniform dialogs. All texts that warn about destructive effects live here so they read the same everywhere.
 */
public final class Dialogs {

    /** Shown before stopping a server the manager owns. On Windows, stop is a hard kill. */
    public static final String STOP_WARNING = "Stopping the server ends the Ollama process immediately.\n"
        + "On Windows this is a hard kill: requests that are still running are aborted\n"
        + "and clients receive an error.\n\nStop the server now?";

    private Dialogs() {
    }

    public static boolean confirmStop(Component parent) {
        return confirm(parent, "Stop server", STOP_WARNING);
    }

    public static boolean confirm(Component parent, String title, String message) {
        return JOptionPane.showConfirmDialog(parent, message, title, JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.WARNING_MESSAGE) == JOptionPane.OK_OPTION;
    }

    /** Show a failure with the uniform error text. */
    public static void showError(Component parent, String action, Throwable error) {
        JOptionPane.showMessageDialog(parent, action + " failed.\n\n" + ErrorText.describe(error),
            action, JOptionPane.ERROR_MESSAGE);
    }
}
