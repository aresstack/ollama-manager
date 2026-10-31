package com.aresstack.ollamamanager.swing.component;

import com.aresstack.ollamamanager.swing.job.Job;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import java.awt.BorderLayout;
import java.util.function.Consumer;

/**
 * Progress bar with a working Cancel button for one job. Hidden while no job is attached.
 */
public final class JobProgressPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private final JLabel phase = new JLabel(" ");
    private final JProgressBar bar = new JProgressBar(0, 1000);
    private final JButton cancel = new JButton("Cancel");
    private final Consumer<Job> listener = this::render;
    private Job job;

    public JobProgressPanel() {
        super(new BorderLayout(Spacing.GAP, 2));
        setOpaque(false);
        bar.setStringPainted(true);
        add(phase, BorderLayout.NORTH);
        add(bar, BorderLayout.CENTER);
        add(cancel, BorderLayout.EAST);
        cancel.setMnemonic('C');
        cancel.addActionListener(event -> {
            if (job != null) {
                job.cancel();
            }
        });
        setVisible(false);
    }

    /** Follow {@code newJob}; pass {@code null} to hide the panel. */
    public void attach(Job newJob) {
        if (job != null) {
            job.removeListener(listener);
        }
        job = newJob;
        if (job != null) {
            job.addListener(listener);
            render(job);
        }
        setVisible(job != null);
    }

    private void render(Job current) {
        phase.setText(current.title() + ": " + current.phase());
        double fraction = current.fraction();
        bar.setIndeterminate(fraction < 0 && !current.state().isFinished());
        if (fraction >= 0) {
            bar.setValue((int) Math.round(fraction * 1000));
            bar.setString(Math.round(fraction * 100) + " %");
        } else {
            bar.setString(current.state().label());
        }
        cancel.setEnabled(current.isCancellable());
    }
}
