package com.aresstack.ollamamanager.swing.section;

import com.aresstack.ollamamanager.domain.server.ServerSetting;

import java.util.Map;
import java.util.Set;

/**
 * What the server settings presenter needs from its panel. All calls happen on the UI thread.
 */
public interface ServerSettingsView {

    /** Replace all editor contents; empty text means "Ollama default". */
    void showValues(Map<ServerSetting<?>, String> texts);

    /**
     * Show what the running server actually uses, next to the stored values.
     *
     * @param applied        encoded values of the running owned server, or {@code null} when none runs
     * @param pendingRestart stored settings that differ from the running server and need a restart
     * @param source         short description of where {@code applied} comes from, or why it is missing
     */
    void showApplied(Map<ServerSetting<?>, String> applied, Set<ServerSetting<?>> pendingRestart, String source);

    /** Current editor contents. */
    Map<ServerSetting<?>, String> editedValues();

    /** Mark invalid fields; an empty map clears all marks. */
    void showErrors(Map<ServerSetting<?>, String> errors);

    void setBusy(boolean busy);

    /** Show the save outcome or restart notice; {@code null} clears it. */
    void showNotice(String notice);

    void showFailure(String action, Exception error);
}
