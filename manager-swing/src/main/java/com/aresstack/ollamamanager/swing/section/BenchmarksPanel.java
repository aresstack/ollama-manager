package com.aresstack.ollamamanager.swing.section;

import com.aresstack.ollamamanager.application.benchmark.BenchmarkReport;
import com.aresstack.ollamamanager.application.benchmark.ConcurrencyBenchmarkRequest;
import com.aresstack.ollamamanager.application.benchmark.StageResult;
import com.aresstack.ollamamanager.swing.component.CardPanel;
import com.aresstack.ollamamanager.swing.component.FormPanel;
import com.aresstack.ollamamanager.swing.component.JobProgressPanel;
import com.aresstack.ollamamanager.swing.component.MessageBanner;
import com.aresstack.ollamamanager.swing.component.SectionPanel;
import com.aresstack.ollamamanager.swing.component.Spacing;
import com.aresstack.ollamamanager.swing.component.Tables;
import com.aresstack.ollamamanager.swing.component.Theme;
import com.aresstack.ollamamanager.swing.component.ThroughputChart;
import com.aresstack.ollamamanager.swing.job.Job;
import com.aresstack.ollamamanager.swing.shell.Section;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.HierarchyEvent;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Concurrency benchmark: pick a model, choose how many parallel chats to try, run, and read off the optimum
 * from the chart. Per-stage numbers are in the table; finished runs are kept for comparison.
 */
public final class BenchmarksPanel extends SectionPanel implements BenchmarksView {

    private static final long serialVersionUID = 1L;
    static final String DEFAULT_LEVELS = "1, 2, 4, 6, 8, 12, 16";

    private final BenchmarksPresenter presenter;
    private final MessageBanner banner = new MessageBanner();
    private final JComboBox<String> model = new JComboBox<String>();
    private final JTextField levels = new JTextField(DEFAULT_LEVELS, 20);
    private final JSpinner maxTokens = new JSpinner(new SpinnerNumberModel(128, 8, 4096, 16));
    private final JCheckBox setContext = new JCheckBox("Context size");
    private final JSpinner numCtx = new JSpinner(new SpinnerNumberModel(4096, 256, 262144, 1024));
    private final JSpinner rounds = new JSpinner(new SpinnerNumberModel(1, 1, 10, 1));
    private final JCheckBox stopWhenUnstable = new JCheckBox("Stop at the first stage with errors or CPU offload",
        true);
    private final JLabel serverHint = new JLabel(" ");
    private final JButton run = new JButton("Run benchmark");
    private final JobProgressPanel progress = new JobProgressPanel();
    private final ThroughputChart chart = new ThroughputChart();
    private final JLabel verdict = new JLabel(" ");
    private final StageTableModel stageModel = new StageTableModel();
    private final JTable stageTable = new JTable(stageModel);
    private final HistoryTableModel historyModel = new HistoryTableModel();
    private final JTable historyTable = new JTable(historyModel);
    private final JButton showRun = new JButton("Show");
    private final JButton deleteRun = new JButton("Delete");
    private boolean running;
    private boolean hasModels;

    public BenchmarksPanel(BenchmarksPresenter presenter) {
        super(Section.BENCHMARKS.title(), Section.BENCHMARKS.description());
        this.presenter = presenter;

        JButton refresh = new JButton("Refresh");
        refresh.setMnemonic('R');
        refresh.addActionListener(event -> presenter.refresh());
        addToolbarItem(refresh);

        model.setToolTipText("Installed model to measure.");
        model.setPrototypeDisplayValue("some-model-name:latest-q4_K_M");
        levels.setToolTipText("How many chats to start at the same time, one stage per number, for example 1, 2, 4, 8.");
        maxTokens.setToolTipText("Tokens each chat generates per request. More tokens give steadier numbers but take longer.");
        setContext.setToolTipText("Send num_ctx with every request. Larger contexts need more VRAM per chat.");
        numCtx.setEnabled(false);
        setContext.addActionListener(event -> numCtx.setEnabled(setContext.isSelected()));
        rounds.setToolTipText("Requests each chat sends in a row per stage.");
        run.setMnemonic('B');
        run.addActionListener(event -> start());

        FormPanel form = new FormPanel();
        form.addRow("Model", model, null);
        form.addRow("Parallel chats", levels, levels.getToolTipText());
        form.addRow("Tokens per request", withUnit(maxTokens, "tokens"), maxTokens.getToolTipText());
        JPanel contextRow = new JPanel(new FlowLayout(FlowLayout.LEADING, Spacing.GAP, 0));
        contextRow.setOpaque(false);
        contextRow.add(setContext);
        contextRow.add(numCtx);
        contextRow.add(new JLabel("tokens"));
        form.addRow("num_ctx", contextRow, setContext.getToolTipText());
        form.addRow("Rounds per chat", rounds, rounds.getToolTipText());
        serverHint.setForeground(Theme.mutedForeground());
        JPanel controls = new JPanel(new BorderLayout(0, Spacing.GAP));
        controls.setOpaque(false);
        controls.add(form, BorderLayout.CENTER);
        stopWhenUnstable.setOpaque(false);
        JPanel options = new JPanel(new BorderLayout(0, Spacing.GAP));
        options.setOpaque(false);
        options.add(stopWhenUnstable, BorderLayout.NORTH);
        JPanel runRow = new JPanel(new BorderLayout(Spacing.GAP, 0));
        runRow.setOpaque(false);
        runRow.add(serverHint, BorderLayout.CENTER);
        runRow.add(run, BorderLayout.EAST);
        options.add(runRow, BorderLayout.SOUTH);
        controls.add(options, BorderLayout.SOUTH);
        CardPanel setupCard = new CardPanel("Settings", controls);

        verdict.setFont(Theme.derived(1.1f, true));
        JPanel chartBox = new JPanel(new BorderLayout(0, Spacing.GAP));
        chartBox.setOpaque(false);
        chartBox.add(chart, BorderLayout.CENTER);
        chartBox.add(verdict, BorderLayout.SOUTH);
        CardPanel chartCard = new CardPanel("Total tokens per second over parallel chats", chartBox);

        Tables.setup(stageTable, "Benchmark stages");
        Tables.alignRight(stageTable, 0, 1, 2, 3, 4, 5, 6, 7);
        stageTable.getColumnModel().getColumn(8).setCellRenderer(Tables.toneRenderer(value ->
            "ok".equals(value) ? Theme.Tone.SUCCESS : Theme.Tone.ERROR));
        JScrollPane stageScroll = new JScrollPane(stageTable);
        stageScroll.setPreferredSize(new Dimension(400, 150));
        CardPanel stageCard = new CardPanel("Per stage", stageScroll);

        Tables.setup(historyTable, "Benchmark history");
        historyTable.getSelectionModel().addListSelectionListener(event -> updateButtons());
        showRun.addActionListener(event -> {
            BenchmarkReport selected = selectedReport();
            if (selected != null) {
                showReport(selected);
            }
        });
        deleteRun.addActionListener(event -> presenter.delete(selectedReport()));
        JPanel historyButtons = new JPanel(new FlowLayout(FlowLayout.TRAILING, Spacing.GAP, 0));
        historyButtons.setOpaque(false);
        historyButtons.add(showRun);
        historyButtons.add(deleteRun);
        JScrollPane historyScroll = new JScrollPane(historyTable);
        historyScroll.setPreferredSize(new Dimension(400, 120));
        JPanel historyBox = new JPanel(new BorderLayout(0, Spacing.GAP));
        historyBox.setOpaque(false);
        historyBox.add(historyScroll, BorderLayout.CENTER);
        historyBox.add(historyButtons, BorderLayout.SOUTH);
        CardPanel historyCard = new CardPanel("Previous runs", historyBox);

        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        left.add(setupCard);
        left.add(Box.createVerticalStrut(Spacing.GAP));
        left.add(historyCard);
        left.add(Box.createVerticalGlue());

        JPanel right = new JPanel(new BorderLayout(0, Spacing.GAP));
        right.setOpaque(false);
        right.add(chartCard, BorderLayout.CENTER);
        right.add(stageCard, BorderLayout.SOUTH);

        JScrollPane leftScroll = new JScrollPane(left);
        leftScroll.setBorder(null);
        leftScroll.setMinimumSize(new Dimension(320, 200));
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftScroll, right);
        split.setBorder(null);
        split.setResizeWeight(0);
        split.setDividerLocation(400);
        split.setContinuousLayout(true);

        JPanel body = new JPanel(new BorderLayout(0, Spacing.GAP));
        body.setOpaque(false);
        body.add(banner, BorderLayout.NORTH);
        body.add(split, BorderLayout.CENTER);
        body.add(progress, BorderLayout.SOUTH);
        setBody(body);
        updateButtons();
        presenter.bind(this);
        addHierarchyListener(event -> {
            if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && isShowing()) {
                presenter.refresh();
            }
        });
    }

    private static JPanel withUnit(JSpinner spinner, String unit) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEADING, Spacing.GAP, 0));
        row.setOpaque(false);
        row.add(spinner);
        row.add(new JLabel(unit));
        return row;
    }

    private void start() {
        String name = (String) model.getSelectedItem();
        if (name == null || name.trim().isEmpty()) {
            banner.warning("Choose a model first.");
            return;
        }
        List<Integer> parsed;
        try {
            parsed = parseLevels(levels.getText());
        } catch (IllegalArgumentException e) {
            banner.error("Parallel chats: " + e.getMessage());
            return;
        }
        banner.clear();
        ConcurrencyBenchmarkRequest request;
        try {
            request = new ConcurrencyBenchmarkRequest(name, parsed,
                setContext.isSelected() ? (Integer) numCtx.getValue() : null, (Integer) maxTokens.getValue(),
                (Integer) rounds.getValue(), null, stopWhenUnstable.isSelected());
        } catch (IllegalArgumentException e) {
            banner.error(e.getMessage());
            return;
        }
        chart.clear();
        stageModel.setRows(new ArrayList<StageResult>());
        verdict.setText("Running…");
        presenter.run(request);
    }

    /** Parse "1, 2, 4-8" style input into distinct numbers between 1 and {@link #MAX_LEVEL}. */
    static List<Integer> parseLevels(String text) {
        List<Integer> result = new ArrayList<Integer>();
        for (String part : text.split("[,;\\s]+")) {
            if (part.isEmpty()) {
                continue;
            }
            int dash = part.indexOf('-');
            if (dash > 0) {
                int from = level(part.substring(0, dash));
                int to = level(part.substring(dash + 1));
                if (to < from) {
                    throw new IllegalArgumentException("range " + part + " runs backwards");
                }
                for (int i = from; i <= to; i++) {
                    result.add(i);
                }
            } else {
                result.add(level(part));
            }
        }
        if (result.isEmpty()) {
            throw new IllegalArgumentException("enter at least one number, for example 1, 2, 4, 8");
        }
        return result;
    }

    /** Highest number of parallel chats the UI accepts; the request enforces the same limit. */
    static final int MAX_LEVEL = 256;

    private static int level(String text) {
        int value;
        try {
            value = Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("'" + text.trim() + "' is not a number");
        }
        if (value < 1 || value > MAX_LEVEL) {
            throw new IllegalArgumentException(value + " is outside 1-" + MAX_LEVEL);
        }
        return value;
    }

    private BenchmarkReport selectedReport() {
        int row = historyTable.getSelectedRow();
        return row < 0 ? null : historyModel.rows.get(historyTable.convertRowIndexToModel(row));
    }

    private void updateButtons() {
        run.setEnabled(!running && hasModels);
        boolean selected = historyTable.getSelectedRow() >= 0;
        // Neither action touches the display of a running sweep.
        showRun.setEnabled(selected && !running);
        deleteRun.setEnabled(selected && !running);
    }

    @Override
    public void updateUI() {
        super.updateUI();
        if (stageTable != null) {
            Tables.fitRowHeight(stageTable);
            Tables.fitRowHeight(historyTable);
            serverHint.setForeground(Theme.mutedForeground());
        }
    }

    @Override
    public void showModels(List<String> models) {
        String previous = (String) model.getSelectedItem();
        model.setModel(new DefaultComboBoxModel<String>(models.toArray(new String[0])));
        if (previous != null && models.contains(previous)) {
            model.setSelectedItem(previous);
        }
        hasModels = !models.isEmpty();
        if (!hasModels) {
            banner.info("No models installed on this server. Pull one in Models first.");
        } else if (banner.tone() == Theme.Tone.INFO) {
            banner.clear();
        }
        updateButtons();
    }

    @Override
    public void showServerParallel(String text) {
        if (text == null) {
            serverHint.setText("OLLAMA_NUM_PARALLEL of this server is unknown; stages above it queue on the server.");
        } else if (BenchmarksPresenter.SERVER_DEFAULT.equals(text)) {
            serverHint.setText("OLLAMA_NUM_PARALLEL is not set; Ollama picks 1 or 4 by itself. Stages above that queue.");
        } else {
            serverHint.setText("OLLAMA_NUM_PARALLEL = " + text + " on the running server. Stages above that queue.");
        }
    }

    @Override
    public void showUnavailable(String reason) {
        hasModels = false;
        model.setModel(new DefaultComboBoxModel<String>());
        banner.warning(reason);
        updateButtons();
    }

    @Override
    public void setRunning(boolean isRunning) {
        running = isRunning;
        model.setEnabled(!isRunning);
        levels.setEnabled(!isRunning);
        maxTokens.setEnabled(!isRunning);
        setContext.setEnabled(!isRunning);
        numCtx.setEnabled(!isRunning && setContext.isSelected());
        rounds.setEnabled(!isRunning);
        stopWhenUnstable.setEnabled(!isRunning);
        if (!isRunning && "Running…".equals(verdict.getText())) {
            verdict.setText(chart.pointCount() == 0 ? "Cancelled before the first stage finished."
                : "Cancelled; stages so far are shown.");
        }
        updateButtons();
    }

    @Override
    public void stageFinished(StageResult stage) {
        chart.addPoint(toPoint(stage));
        stageModel.add(stage);
    }

    @Override
    public void showReport(BenchmarkReport report) {
        List<ThroughputChart.Point> points = new ArrayList<ThroughputChart.Point>();
        for (StageResult stage : report.stages()) {
            points.add(toPoint(stage));
        }
        StageResult optimum = report.optimum();
        chart.setPoints(points, optimum == null ? null : optimum.parallel());
        stageModel.setRows(report.stages());
        verdict.setText(verdictText(report));
    }

    static String verdictText(BenchmarkReport report) {
        StageResult optimum = report.optimum();
        if (optimum == null) {
            return report.stages().isEmpty() ? "No stage finished." : "No stable stage: every stage had errors or left the GPU.";
        }
        String text = String.format(Locale.ROOT, "%s: optimum %d parallel %s at %.1f tok/s total (%.1f tok/s per chat)",
            report.model(), optimum.parallel(), optimum.parallel() == 1 ? "chat" : "chats", optimum.aggregateTps(),
            optimum.perChatTpsAvg());
        StageResult maxStable = report.maxStable();
        if (maxStable != null && maxStable.parallel() > optimum.parallel()) {
            text += String.format(Locale.ROOT, "; still stable up to %d", maxStable.parallel());
        }
        return report.complete() ? text : text + " (run incomplete)";
    }

    private static ThroughputChart.Point toPoint(StageResult stage) {
        return new ThroughputChart.Point(stage.parallel(), stage.aggregateTps(), stage.perChatTpsAvg(),
            stage.isStable());
    }

    @Override
    public void showHistory(List<BenchmarkReport> reports) {
        historyModel.setRows(reports);
        updateButtons();
    }

    @Override
    public void showFailure(String action, Exception error) {
        banner.error(action, error);
    }

    @Override
    public void trackJob(Job job) {
        progress.attach(job);
    }

    /** Per-stage numbers. */
    static final class StageTableModel extends AbstractTableModel {
        private static final long serialVersionUID = 1L;
        private static final String[] COLUMNS = {"Chats", "Total tok/s", "Per chat tok/s", "Slowest chat",
            "First token p50", "First token p95", "Latency p95", "Peak VRAM", "State"};
        private final List<StageResult> rows = new ArrayList<StageResult>();

        void setRows(List<StageResult> stages) {
            rows.clear();
            rows.addAll(stages);
            fireTableDataChanged();
        }

        void add(StageResult stage) {
            rows.add(stage);
            fireTableRowsInserted(rows.size() - 1, rows.size() - 1);
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
            StageResult s = rows.get(row);
            switch (column) {
                case 0: return String.valueOf(s.parallel());
                case 1: return tps(s.aggregateTps());
                case 2: return tps(s.perChatTpsAvg());
                case 3: return tps(s.perChatTpsMin());
                case 4: return s.ttftP50Millis() + " ms";
                case 5: return s.ttftP95Millis() + " ms";
                case 6: return s.latencyP95Millis() + " ms";
                case 7: return s.peakVramBytes() == null ? "–" : Format.bytes(s.peakVramBytes());
                default: return state(s);
            }
        }

        private static String state(StageResult s) {
            if (s.failures() > 0) {
                return s.failures() + " failed" + (s.firstError() == null ? "" : ": " + s.firstError());
            }
            if (s.gpuShare() != null && s.gpuShare() < 0.999) {
                return s.gpuShare() == 0 ? "unloaded" : String.format(Locale.ROOT, "%.0f %% on GPU", s.gpuShare() * 100);
            }
            return "ok";
        }
    }

    static String tps(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    /** Stored runs, newest first. */
    static final class HistoryTableModel extends AbstractTableModel {
        private static final long serialVersionUID = 1L;
        private static final String[] COLUMNS = {"When", "Model", "num_ctx", "Optimum", "Total tok/s"};
        final List<BenchmarkReport> rows = new ArrayList<BenchmarkReport>();
        private final DateFormat format = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);

        void setRows(List<BenchmarkReport> reports) {
            rows.clear();
            rows.addAll(reports);
            fireTableDataChanged();
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
            BenchmarkReport r = rows.get(row);
            StageResult optimum = r.optimum();
            switch (column) {
                case 0: return format.format(new Date(r.startedAtEpochMillis()));
                case 1: return r.model();
                case 2: return r.numCtx() == null ? "default" : String.valueOf(r.numCtx());
                case 3: return optimum == null ? "–" : String.valueOf(optimum.parallel());
                default: return optimum == null ? "–" : tps(optimum.aggregateTps());
            }
        }
    }
}
