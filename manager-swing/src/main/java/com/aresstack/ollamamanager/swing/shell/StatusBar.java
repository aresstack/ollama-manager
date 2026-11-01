package com.aresstack.ollamamanager.swing.shell;

import com.aresstack.ollamamanager.swing.component.Spacing;
import com.aresstack.ollamamanager.swing.component.StatusBadge;
import com.aresstack.ollamamanager.swing.component.Theme;
import com.aresstack.ollamamanager.swing.job.Job;
import com.aresstack.ollamamanager.swing.job.JobManager;
import com.aresstack.ollamamanager.swing.job.JobState;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import java.awt.BorderLayout;

/**
 * Bottom line: last finished job outcome with a state colour, and the number of running jobs.
 */
final class StatusBar extends JPanel {

    private static final long serialVersionUID = 1L;

    private final StatusBadge message = new StatusBadge("Ready", Theme.Tone.NEUTRAL);
    private final StatusBadge jobs = new StatusBadge();

    StatusBar(JobManager jobManager) {
        super(new BorderLayout(Spacing.MARGIN, 0));
        message.getAccessibleContext().setAccessibleName("Last job result");
        jobs.getAccessibleContext().setAccessibleName("Running jobs");
        add(message, BorderLayout.CENTER);
        add(jobs, BorderLayout.EAST);
        jobManager.addListener(job -> update(jobManager, job));
        update(jobManager, null);
        applyLook();
    }

    @Override
    public void updateUI() {
        super.updateUI();
        if (message != null) {
            applyLook();
        }
    }

    private void applyLook() {
        setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.lineColor()),
            BorderFactory.createEmptyBorder(Spacing.SMALL, Spacing.MARGIN, Spacing.SMALL, Spacing.MARGIN)));
    }

    private void update(JobManager jobManager, Job job) {
        if (job != null && job.state().isFinished()) {
            message.show(job.title() + ": " + (job.state() == JobState.FAILED ? job.errorMessage()
                : job.state().label()), tone(job.state()));
        }
        int active = jobManager.activeCount();
        jobs.show(active == 0 ? "No running jobs" : active + " running job" + (active == 1 ? "" : "s"),
            active == 0 ? Theme.Tone.NEUTRAL : Theme.Tone.INFO);
        jobs.setPulsing(active > 0);
    }

    static Theme.Tone tone(JobState state) {
        switch (state) {
            case SUCCEEDED:
                return Theme.Tone.SUCCESS;
            case FAILED:
                return Theme.Tone.ERROR;
            case CANCELLED:
                return Theme.Tone.WARNING;
            case RUNNING:
            case CANCELLING:
            default:
                return Theme.Tone.INFO;
        }
    }
}
