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

    /** Shown before stopping a server that Ollama Manager did not start. */
    public static final String EXTERNAL_STOP_WARNING = "This server was not started by Ollama Manager.\n"
        + "Stopping it ends the Ollama process, and on Windows also the Ollama app that would start it again.\n"
        + "Requests that are still running are aborted and clients receive an error.\n\nStop the server now?";

    /** Shown before restarting a server with the stored server settings. */
    public static final String RESTART_WARNING = "The server is stopped and started again with the stored server"
        + " settings.\nRequests that are still running are aborted and loaded models are unloaded.\n\n"
        + "Restart the server now?";

    /** Shown before replacing an external server with an own server started from the same installation. */
    public static final String EXTERNAL_RESTART_WARNING = "This server was not started by Ollama Manager.\n"
        + "It is stopped (on Windows together with the Ollama app) and started again from the same installation"
        + "\nwith the stored server settings; from then on Ollama Manager controls it.\n"
        + "Requests that are still running are aborted and loaded models are unloaded.\n\n"
        + "Restart the server now?";

    private Dialogs() {
    }

    public static boolean confirmStop(Component parent) {
        return confirm(parent, "Stop server", STOP_WARNING);
    }

    public static boolean confirmStop(Component parent, boolean external) {
        return confirm(parent, "Stop server", external ? EXTERNAL_STOP_WARNING : STOP_WARNING);
    }

    public static boolean confirmRestart(Component parent, boolean external) {
        return confirm(parent, "Restart server", external ? EXTERNAL_RESTART_WARNING : RESTART_WARNING);
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
