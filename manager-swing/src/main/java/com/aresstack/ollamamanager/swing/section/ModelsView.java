package com.aresstack.ollamamanager.swing.section;

import com.aresstack.ollamamanager.application.model.ModelEntry;
import com.aresstack.ollamamanager.swing.job.Job;

import java.util.List;

/**
 * What the models presenter needs from its panel. All calls happen on the UI thread.
 */
public interface ModelsView {

    void showModels(List<ModelEntry> entries);

    /** Show why the model list is not available, for example because no server is running. */
    void showUnavailable(String reason);

    /** Enable or disable all mutating actions; disabled while one of them runs. */
    void setActionsEnabled(boolean enabled);

    /**
     * Allow or forbid pull, load, unload and delete for the current server.
     *
     * @param reason shown while forbidden, {@code null} when allowed
     */
    void setMutationsAllowed(boolean allowed, String reason);

    void showFailure(String action, Exception error);

    void trackJob(Job job);

    /**
     * Ask the user to type the model name to confirm deletion.
     *
     * @return the typed name, or {@code null} when the user cancelled
     */
    String confirmDelete(String modelName);
}
