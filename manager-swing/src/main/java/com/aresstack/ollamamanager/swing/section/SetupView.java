package com.aresstack.ollamamanager.swing.section;

import com.aresstack.ollamamanager.application.hardware.PackageRecommendation;
import com.aresstack.ollamamanager.domain.server.OllamaInstallation;
import com.aresstack.ollamamanager.swing.job.Job;

/**
 * Passive view of the setup assistant. The presenter calls it on the UI thread only.
 */
public interface SetupView {

    /** Show what detection found; {@code null} while nothing was detected yet. */
    void showDetection(SetupDetection detection);

    /** Show the recommended portable packages and the reasons behind them. */
    void showRecommendation(PackageRecommendation recommendation);

    /** Disable every action while one runs; Cancel stays available through {@link #trackJob(Job)}. */
    void setBusy(boolean busy);

    /** Show a status line; {@code null} clears it. */
    void showNotice(String notice);

    void showFailure(String action, Exception error);

    /** Show progress and Cancel for a long-running job such as a download. */
    void trackJob(Job job);

    /** Ask before removing a portable version from disk. */
    boolean confirmUninstall(OllamaInstallation installation);

    /** Ask before starting a server; the text names the endpoint and the settings that are used. */
    boolean confirmStart(OllamaInstallation installation, String endpoint);
}
