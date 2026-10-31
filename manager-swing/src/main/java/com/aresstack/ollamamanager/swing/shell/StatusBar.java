package com.aresstack.ollamamanager.swing.shell;

import com.aresstack.ollamamanager.swing.component.Spacing;
import com.aresstack.ollamamanager.swing.job.Job;
import com.aresstack.ollamamanager.swing.job.JobManager;
import com.aresstack.ollamamanager.swing.job.JobState;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.UIManager;
import java.awt.BorderLayout;

/**
 * Bottom line: last finished job outcome and the number of running jobs.
 */
final class StatusBar extends JPanel {

    private static final long serialVersionUID = 1L;

    private final JLabel message = new JLabel("Ready");
    private final JLabel jobs = new JLabel();

    StatusBar(JobManager jobManager) {
        super(new BorderLayout(Spacing.GAP, 0));
        setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, UIManager.getColor("Separator.foreground")),
            BorderFactory.createEmptyBorder(4, Spacing.GAP, 4, Spacing.GAP)));
        add(message, BorderLayout.CENTER);
        add(jobs, BorderLayout.EAST);
        jobManager.addListener(job -> update(jobManager, job));
        update(jobManager, null);
    }

    private void update(JobManager jobManager, Job job) {
        if (job != null && job.state().isFinished()) {
            message.setText(job.title() + ": " + (job.state() == JobState.FAILED ? job.errorMessage()
                : job.state().label()));
        }
        int active = jobManager.activeCount();
        jobs.setText(active == 0 ? "No running jobs" : active + " running job" + (active == 1 ? "" : "s"));
    }
}
