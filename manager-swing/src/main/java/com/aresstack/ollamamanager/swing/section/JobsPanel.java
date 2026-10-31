package com.aresstack.ollamamanager.swing.section;

import com.aresstack.ollamamanager.application.server.ServerLifecycleService;
import com.aresstack.ollamamanager.swing.component.JobProgressPanel;
import com.aresstack.ollamamanager.swing.component.SectionPanel;
import com.aresstack.ollamamanager.swing.job.Job;
import com.aresstack.ollamamanager.swing.job.JobManager;
import com.aresstack.ollamamanager.swing.shell.ConsoleServices;
import com.aresstack.ollamamanager.swing.shell.Section;

import javax.swing.JButton;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;

/**
 * All jobs of this session with progress and Cancel, plus the recent log of the managed server.
 */
public final class JobsPanel extends SectionPanel {

    private static final long serialVersionUID = 1L;

    private final JobTableModel model = new JobTableModel();
    private final JTable table = new JTable(model);
    private final JobProgressPanel progress = new JobProgressPanel();
    private final JTextArea log = new JTextArea();

    public JobsPanel(ConsoleServices services, JobManager jobs) {
        super(Section.JOBS.title(), Section.JOBS.description());
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFillsViewportHeight(true);
        table.getSelectionModel().addListSelectionListener(event -> {
            int row = table.getSelectedRow();
            progress.attach(row < 0 ? null : model.job(table.convertRowIndexToModel(row)));
        });
        jobs.addListener(model::changed);

        log.setEditable(false);
        log.setFont(new Font(Font.MONOSPACED, Font.PLAIN, log.getFont().getSize()));
        log.getAccessibleContext().setAccessibleName("Server log");

        JButton reloadLog = new JButton("Reload log");
        reloadLog.setMnemonic('L');
        reloadLog.addActionListener(event -> loadLog(services.serverLifecycle(), jobs));
        addToolbarItem(reloadLog);

        javax.swing.JPanel top = new javax.swing.JPanel(new BorderLayout(0, 4));
        top.add(new JScrollPane(table), BorderLayout.CENTER);
        top.add(progress, BorderLayout.SOUTH);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, top, new JScrollPane(log));
        split.setResizeWeight(0.5);
        setBody(split);
        loadLog(services.serverLifecycle(), jobs);
    }

    private void loadLog(ServerLifecycleService lifecycle, JobManager jobs) {
        if (lifecycle == null) {
            log.setText("Server log is not available: the server lifecycle is not wired in this build.");
            return;
        }
        jobs.submit("Read server log", false, context -> lifecycle.recentLog(), lines -> {
            log.setText(String.join("\n", lines));
            log.setCaretPosition(log.getDocument().getLength());
        }, error -> log.setText("Server log could not be read."));
    }

    private static final class JobTableModel extends AbstractTableModel {
        private static final long serialVersionUID = 1L;
        private static final String[] COLUMNS = {"#", "Job", "State", "Step"};
        private final List<Job> rows = new ArrayList<Job>();

        Job job(int row) {
            return rows.get(row);
        }

        void changed(Job job) {
            int index = rows.indexOf(job);
            if (index < 0) {
                rows.add(job);
                fireTableRowsInserted(rows.size() - 1, rows.size() - 1);
            } else {
                fireTableRowsUpdated(index, index);
            }
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMNS[column];
        }

        @Override
        public Object getValueAt(int row, int column) {
            Job job = rows.get(row);
            switch (column) {
                case 0:
                    return job.id();
                case 1:
                    return job.title();
                case 2:
                    return job.state().label();
                default:
                    return job.errorMessage() != null ? job.errorMessage() : job.phase();
            }
        }
    }
}
