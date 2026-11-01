package com.aresstack.ollamamanager.swing.section;

import com.aresstack.ollamamanager.application.benchmark.BenchmarkProfiles;
import com.aresstack.ollamamanager.application.benchmark.BenchmarkReport;
import com.aresstack.ollamamanager.application.benchmark.ConcurrencyBenchmarkRequest;
import com.aresstack.ollamamanager.application.benchmark.ParallelismSweepService;
import com.aresstack.ollamamanager.application.benchmark.StageResult;
import com.aresstack.ollamamanager.domain.profile.ModelProfile;
import com.aresstack.ollamamanager.swing.component.CardPanel;
import com.aresstack.ollamamanager.swing.component.Dialogs;
import com.aresstack.ollamamanager.swing.component.FormPanel;
import com.aresstack.ollamamanager.swing.component.JobProgressPanel;
import com.aresstack.ollamamanager.swing.component.MessageBanner;
import com.aresstack.ollamamanager.swing.component.ScrollColumn;
import com.aresstack.ollamamanager.swing.component.SectionPanel;
import com.aresstack.ollamamanager.swing.component.Spacing;
import com.aresstack.ollamamanager.swing.component.Tables;
import com.aresstack.ollamamanager.swing.component.Theme;
import com.aresstack.ollamamanager.swing.component.ThroughputChart;
import com.aresstack.ollamamanager.swing.component.WrapLabel;
import com.aresstack.ollamamanager.swing.job.Job;
import com.aresstack.ollamamanager.swing.shell.Section;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.table.AbstractTableModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.HierarchyEvent;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Concurrency benchmark: pick a model, choose how many parallel chats to try, run, and read off the optimum
 * from the chart. Optionally the owned server is restarted with several {@code OLLAMA_NUM_PARALLEL} values and
 * the runs are drawn on top of each other. Per-stage numbers are in the table; finished runs are kept for
 * comparison and an optimum can be saved as a model profile.
 */
public final class BenchmarksPanel extends SectionPanel implements BenchmarksView {

    private static final long serialVersionUID = 1L;
    static final String DEFAULT_LEVELS = "1, 2, 4, 6, 8, 12, 16";
    static final String DEFAULT_SERVER_VALUES = "1, 4, 8, 16";
    /** Highest number of parallel chats the UI accepts; the request enforces the same limit. */
    static final int MAX_LEVEL = 256;
    static final String SWEEP_WARNING = "The server is restarted once per value and once more at the end to restore"
        + " its current settings.\nEvery restart ends running requests of all clients at once.\n\nContinue?";
    /** Shown before the owned server is restarted with a higher OLLAMA_NUM_PARALLEL; %d is the value. */
    static final String RAISE_WARNING = "OLLAMA_NUM_PARALLEL = %d is stored in the server settings and the server"
        + " is stopped and started again with it and the other stored server settings.\nRequests that are still"
        + " running are aborted and loaded models are unloaded.\n\nRestart the server now?";
    /** Shown before an external server is replaced by an own server with a higher OLLAMA_NUM_PARALLEL. */
    static final String RAISE_EXTERNAL_WARNING = "This server was not started by Ollama Manager.\n"
        + "It is stopped (on Windows together with the Ollama app) and started again from the same installation"
        + "\nwith OLLAMA_NUM_PARALLEL = %d and the stored server settings; from then on Ollama Manager controls it."
        + "\nRequests that are still running are aborted and loaded models are unloaded.\n\nRestart the server now?";

    private final BenchmarksPresenter presenter;
    private final MessageBanner banner = new MessageBanner();
    private final JComboBox<String> model = new JComboBox<String>();
    private final JTextField levels = new JTextField(DEFAULT_LEVELS, 14);
    private final JSpinner maxTokens = new JSpinner(new SpinnerNumberModel(128, 8, 4096, 16));
    private final JCheckBox setContext = new JCheckBox("Context size");
    private final JSpinner numCtx = new JSpinner(new SpinnerNumberModel(4096, 256, 262144, 1024));
    private final JSpinner rounds = new JSpinner(new SpinnerNumberModel(1, 1, 10, 1));
    private final JCheckBox stopWhenUnstable = new JCheckBox("Stop at the first stage with errors or CPU offload",
        true);
    private final JCheckBox sweep = new JCheckBox("Try several server values (OLLAMA_NUM_PARALLEL):");
    private final JTextField serverValues = new JTextField(DEFAULT_SERVER_VALUES, 8);
    private final WrapLabel sweepHint = WrapLabel.muted(" ");
    private final WrapLabel serverHint = WrapLabel.muted(" ");
    private final JButton run = new JButton("Run benchmark");
    private final JobProgressPanel progress = new JobProgressPanel();
    private final ThroughputChart chart = new ThroughputChart();
    private final WrapLabel verdict = new WrapLabel(" ");
    private final StageTableModel stageModel = new StageTableModel();
    private final JTable stageTable = new JTable(stageModel);
    private final HistoryTableModel historyModel = new HistoryTableModel();
    private final JTable historyTable = new JTable(historyModel);
    private final JButton saveProfile = new JButton("Profile…");
    private final JButton deleteRun = new JButton("Delete");
    /** Warns when the server's OLLAMA_NUM_PARALLEL is below the highest number of chats to measure. */
    private final MessageBanner serverNotice = new MessageBanner();
    private final JPanel serverNoticeRow;
    private final JButton raiseParallel = new JButton();
    private boolean running;
    private boolean hasModels;
    /** Whether the banner currently shows this panel's own "no models" hint, the only message it clears itself. */
    private boolean noModelsHint;
    private boolean sweepAvailable;
    /** OLLAMA_NUM_PARALLEL of the running server as the presenter reported it; {@code null} while unknown. */
    private String serverParallel;
    /** Whether a server runs at all; without one there is no value to warn about. */
    private boolean serverPresent;
    private boolean serverRestartAvailable;
    private boolean serverRestartExternal;
    /** Value the notice offers to set; 0 while the notice is hidden or offers nothing. */
    private int raiseTarget;
    /** Runs currently drawn, newest last; a sweep in progress appends to this list. */
    private final List<BenchmarkReport> shown = new ArrayList<BenchmarkReport>();

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
        setContext.setToolTipText("Send num_ctx with every request. Larger contexts need more VRAM per chat; unticked, the"
            + " server's default applies, which for some models is the model's full context and may not fit.");
        // An explicit context is part of the measured setting; the server default can be the model's full context.
        setContext.setSelected(true);
        numCtx.setEnabled(true);
        setContext.addActionListener(event -> numCtx.setEnabled(setContext.isSelected()));
        rounds.setToolTipText("Requests each chat sends in a row per stage.");
        sweep.setToolTipText("Restart the manager's own server with each value, run all stages, then restore the"
            + " current settings. Shows one line per value.");
        serverValues.setToolTipText("OLLAMA_NUM_PARALLEL values to try, for example 1, 4, 8, 16.");
        serverValues.setEnabled(false);
        sweep.addActionListener(event -> {
            serverValues.setEnabled(sweep.isSelected() && sweepAvailable);
            updateServerNotice();
        });
        // The notice follows what is typed, so it appears as soon as a number exceeds the server's value.
        levels.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                updateServerNotice();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                updateServerNotice();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                updateServerNotice();
            }
        });
        run.setMnemonic('B');
        run.addActionListener(event -> start());
        raiseParallel.setToolTipText("Store this OLLAMA_NUM_PARALLEL in the server settings and restart the server"
            + " with it, so that every stage runs its chats at the same time.");
        raiseParallel.addActionListener(event -> raiseServerParallel());

        FormPanel form = new FormPanel();
        form.addRow("Model", model, null);
        form.addRow("Parallel chats", levels, levels.getToolTipText());
        form.addRow("Tokens per request", withUnit(maxTokens, "tokens"), maxTokens.getToolTipText());
        // Every field starts at the label column's edge and stretches to the card's width, so rows line up.
        setContext.setOpaque(false);
        setContext.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, Spacing.GAP));
        JPanel contextRow = new JPanel(new BorderLayout());
        contextRow.setOpaque(false);
        contextRow.add(setContext, BorderLayout.WEST);
        contextRow.add(withUnit(numCtx, "tokens"), BorderLayout.CENTER);
        form.addRow("num_ctx", contextRow, setContext.getToolTipText());
        form.addRow("Rounds per chat", withUnit(rounds, "requests"), rounds.getToolTipText());

        stopWhenUnstable.setOpaque(false);
        sweep.setOpaque(false);
        sweep.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, Spacing.GAP));
        JPanel sweepRow = new JPanel(new BorderLayout());
        sweepRow.setOpaque(false);
        sweepRow.add(sweep, BorderLayout.WEST);
        sweepRow.add(serverValues, BorderLayout.CENTER);
        JPanel options = new JPanel();
        options.setOpaque(false);
        options.setLayout(new BoxLayout(options, BoxLayout.Y_AXIS));
        options.add(leftAligned(stopWhenUnstable));
        options.add(leftAligned(sweepRow));
        options.add(leftAligned(sweepHint));
        options.add(Box.createVerticalStrut(Spacing.GAP));
        JPanel runRow = new JPanel(new BorderLayout(Spacing.GAP, 0));
        runRow.setOpaque(false);
        runRow.add(serverHint, BorderLayout.CENTER);
        runRow.add(run, BorderLayout.EAST);
        options.add(leftAligned(runRow));
        JPanel controls = new JPanel(new BorderLayout(0, Spacing.GAP));
        controls.setOpaque(false);
        controls.add(form, BorderLayout.CENTER);
        controls.add(options, BorderLayout.SOUTH);
        CardPanel setupCard = new CardPanel("Settings", controls);

        verdict.setFont(Theme.derived(1.1f, true));
        // Two lines, whatever the text: a wrapped text area must not steal height from the chart.
        verdict.setPreferredSize(new Dimension(100, verdict.getFontMetrics(verdict.getFont()).getHeight() * 2 + 6));
        JPanel chartBox = new JPanel(new BorderLayout(0, Spacing.GAP));
        chartBox.setOpaque(false);
        chartBox.add(chart, BorderLayout.CENTER);
        chartBox.add(verdict, BorderLayout.SOUTH);
        CardPanel chartCard = new CardPanel("Total tokens per second over parallel chats", chartBox);

        Tables.setup(stageTable, "Benchmark stages");
        // Server left, counts and states centred, measurements right; columns sized so headers stay readable.
        Tables.alignCenter(stageTable, 1);
        Tables.alignRight(stageTable, 2, 3, 4, 5, 6, 7, 8);
        DefaultTableCellRenderer state = Tables.toneRenderer(value ->
            "ok".equals(value) ? Theme.Tone.SUCCESS : Theme.Tone.ERROR);
        state.setHorizontalAlignment(SwingConstants.CENTER);
        stageTable.getColumnModel().getColumn(9).setCellRenderer(state);
        Tables.fitHeaders(stageTable);
        JScrollPane stageScroll = new JScrollPane(stageTable);
        stageScroll.setPreferredSize(new Dimension(400, 150));
        CardPanel stageCard = new CardPanel("Per stage", stageScroll);

        Tables.setup(historyTable, "Benchmark history");
        Tables.alignRight(historyTable, 3, 4, 5);
        Tables.fitHeaders(historyTable);
        historyTable.setToolTipText("Select a run to see its chart and stages; select several to compare them.");
        historyTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        historyTable.getSelectionModel().addListSelectionListener(event -> {
            updateButtons();
            // Selecting runs shows them; several selected runs are drawn on top of each other.
            if (!event.getValueIsAdjusting() && !running) {
                List<BenchmarkReport> selected = selectedReports();
                if (!selected.isEmpty()) {
                    showReports(selected);
                }
            }
        });
        saveProfile.setToolTipText("Create a model profile with the selected run's optimum.");
        saveProfile.addActionListener(event -> askProfile());
        deleteRun.addActionListener(event -> {
            for (BenchmarkReport report : selectedReports()) {
                presenter.delete(report);
            }
        });
        JPanel historyButtons = new JPanel(new FlowLayout(FlowLayout.TRAILING, Spacing.GAP, 0));
        historyButtons.setOpaque(false);
        historyButtons.add(saveProfile);
        historyButtons.add(deleteRun);
        JScrollPane historyScroll = new JScrollPane(historyTable);
        historyScroll.setPreferredSize(new Dimension(400, 140));
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
        serverNoticeRow = leftAligned(serverNotice);
        serverNoticeRow.setBorder(javax.swing.BorderFactory.createEmptyBorder(Spacing.GAP, 0, 0, 0));
        serverNoticeRow.setVisible(false);
        left.add(serverNoticeRow);
        left.add(Box.createVerticalGlue());

        JPanel right = new JPanel(new BorderLayout(0, Spacing.GAP));
        right.setOpaque(false);
        right.add(chartCard, BorderLayout.CENTER);
        right.add(stageCard, BorderLayout.SOUTH);

        JScrollPane leftScroll = ScrollColumn.wrap(left);
        leftScroll.setMinimumSize(new Dimension(360, 200));
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftScroll, right);
        split.setBorder(null);
        split.setResizeWeight(0);
        split.setDividerLocation(470);
        split.setContinuousLayout(true);

        JPanel body = new JPanel(new BorderLayout(0, Spacing.GAP));
        body.setOpaque(false);
        body.add(banner, BorderLayout.NORTH);
        body.add(split, BorderLayout.CENTER);
        body.add(progress, BorderLayout.SOUTH);
        setBody(body);
        setSweepAvailable(false, null);
        updateButtons();
        presenter.bind(this);
        addHierarchyListener(event -> {
            if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && isShowing()) {
                presenter.refresh();
            }
        });
    }

    /** Field that fills the row with its unit at the right edge, so it aligns with plain fields. */
    private static JPanel withUnit(javax.swing.JComponent field, String unit) {
        JPanel row = new JPanel(new BorderLayout(Spacing.GAP, 0));
        row.setOpaque(false);
        row.add(field, BorderLayout.CENTER);
        row.add(new JLabel(unit), BorderLayout.EAST);
        return row;
    }

    /** Row that spans the column's width and is as high as its content, also after that content changed. */
    private static JPanel leftAligned(javax.swing.JComponent component) {
        JPanel row = new JPanel(new BorderLayout()) {
            private static final long serialVersionUID = 1L;

            @Override
            public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }
        };
        row.setOpaque(false);
        row.add(component, BorderLayout.CENTER);
        return row;
    }

    /** Ask, then let the presenter store the offered OLLAMA_NUM_PARALLEL and restart the server with it. */
    private void raiseServerParallel() {
        int target = raiseTarget;
        if (target <= 0 || running) {
            return;
        }
        String warning = String.format(Locale.ROOT, serverRestartExternal ? RAISE_EXTERNAL_WARNING : RAISE_WARNING,
            target);
        if (!Dialogs.confirm(this, "Restart the server with OLLAMA_NUM_PARALLEL = " + target + "?", warning)) {
            return;
        }
        banner.clear();
        presenter.applyServerParallel(target);
    }

    /** Show or hide the notice under the previous runs, from the server's value and the chats to measure. */
    private void updateServerNotice() {
        List<Integer> parsed;
        try {
            parsed = parseLevels(levels.getText());
        } catch (IllegalArgumentException e) {
            parsed = Collections.emptyList();
        }
        ServerNotice notice = serverPresent ? serverNotice(serverParallel, parsed,
            sweep.isSelected() && sweepAvailable, serverRestartAvailable) : null;
        if (notice == null) {
            raiseTarget = 0;
            serverNotice.clear();
            serverNoticeRow.setVisible(false);
        } else {
            raiseTarget = notice.target;
            raiseParallel.setText("Set " + notice.target + " and restart");
            raiseParallel.setEnabled(!running);
            serverNotice.setActions(notice.restartable ? raiseParallel : null, false);
            serverNotice.show(notice.tone, notice.text);
            serverNoticeRow.setVisible(true);
        }
        serverNoticeRow.revalidate();
        serverNoticeRow.repaint();
    }

    /**
     * What to say when the server cannot run every stage's chats at the same time; {@code null} when there is
     * nothing to say. During a sweep the server is restarted per value anyway.
     *
     * @param serverParallel OLLAMA_NUM_PARALLEL as {@link #showServerParallel} received it
     * @param levels         the parallel chats to measure; empty when the input does not parse
     * @param restartable    whether the server can be restarted with a higher value from here
     */
    static ServerNotice serverNotice(String serverParallel, List<Integer> levels, boolean sweeping,
                                     boolean restartable) {
        if (sweeping || levels.isEmpty()) {
            return null;
        }
        int highest = Collections.max(levels);
        String text;
        Theme.Tone tone = Theme.Tone.ERROR;
        if (serverParallel == null) {
            if (highest <= 1) {
                return null;
            }
            tone = Theme.Tone.WARNING;
            text = "OLLAMA_NUM_PARALLEL of this server is unknown. If it is below " + highest + ", the stages above it"
                + " queue on the server and cannot show more throughput.";
        } else if (BenchmarksPresenter.SERVER_DEFAULT.equals(serverParallel)) {
            if (highest <= 1) {
                return null;
            }
            text = "OLLAMA_NUM_PARALLEL is not set on this server, so Ollama picks 1 or 4 by itself, but up to "
                + highest + " parallel chats are to be measured. The stages above that queue on the server and cannot"
                + " show more throughput.";
        } else {
            int current;
            try {
                current = Integer.parseInt(serverParallel.trim());
            } catch (NumberFormatException e) {
                return null;
            }
            if (highest <= current) {
                return null;
            }
            text = "OLLAMA_NUM_PARALLEL is " + current + " on this server, but up to " + highest
                + " parallel chats are to be measured. The stages above " + current + " queue on the server and"
                + " cannot show more throughput.";
        }
        if (!restartable) {
            text += " Set it to at least " + highest + " where the server is started.";
        }
        return new ServerNotice(tone, text, highest, restartable);
    }

    /** Content of the notice under the previous runs. */
    static final class ServerNotice {
        final Theme.Tone tone;
        final String text;
        /** The value the notice asks for: the highest number of chats to measure. */
        final int target;
        /** Whether the notice offers to set the value and restart the server. */
        final boolean restartable;

        ServerNotice(Theme.Tone tone, String text, int target, boolean restartable) {
            this.tone = tone;
            this.text = text;
            this.target = target;
            this.restartable = restartable;
        }
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
        List<Integer> candidates = null;
        if (sweep.isSelected() && sweepAvailable) {
            try {
                // The same distinct, ascending order the sweep runs in, so that live labels match the server.
                candidates = ParallelismSweepService.candidates(parseLevels(serverValues.getText()));
            } catch (IllegalArgumentException e) {
                banner.error("Server values: " + e.getMessage());
                return;
            }
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
        if (candidates != null && !Dialogs.confirm(this, "Restart the server per value?", SWEEP_WARNING)) {
            return;
        }
        boolean started = candidates != null ? presenter.runSweep(request, candidates) : presenter.run(request);
        if (!started) {
            return;
        }
        // Stage results arrive through later UI events, so the chart is prepared after the start.
        shown.clear();
        chart.clear();
        stageModel.setRows(Collections.<BenchmarkReport>emptyList());
        verdict.setText("Running…");
        if (candidates != null) {
            sweepCandidates = candidates;
            currentSweepLabel = String.valueOf(candidates.get(0));
            chart.startSeries(seriesName(currentSweepLabel));
        } else {
            sweepCandidates = Collections.emptyList();
            currentSweepLabel = "";
            chart.startSeries(name);
        }
    }

    private void askProfile() {
        List<BenchmarkReport> selected = selectedReports();
        if (selected.isEmpty()) {
            return;
        }
        BenchmarkReport report = selected.get(0);
        StageResult optimum = report.optimum();
        if (optimum == null) {
            banner.warning("This run has no stable stage to save.");
            return;
        }
        String serverValue = (BenchmarkProfiles.hasServerValue(report)
            ? "OLLAMA_NUM_PARALLEL=" + report.serverNumParallel() : "OLLAMA_NUM_PARALLEL left unset (not measured)")
            + (report.numCtx() == null ? " and no num_ctx (server default)" : " and num_ctx=" + report.numCtx());
        String name = (String) JOptionPane.showInputDialog(this,
            "Profile name for " + report.model() + " with " + optimum.parallel() + " parallel chats"
                + " (" + tps(optimum.aggregateTps()) + " tok/s total).\nThe profile sets " + serverValue + ":",
            "Save as profile", JOptionPane.PLAIN_MESSAGE, null, null, BenchmarkProfiles.defaultName(report, optimum));
        if (name != null) {
            presenter.saveProfile(report, optimum, name);
        }
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

    private List<BenchmarkReport> selectedReports() {
        List<BenchmarkReport> result = new ArrayList<BenchmarkReport>();
        for (int row : historyTable.getSelectedRows()) {
            result.add(historyModel.rows.get(historyTable.convertRowIndexToModel(row)));
        }
        return result;
    }

    private void updateButtons() {
        run.setEnabled(!running && hasModels);
        int selected = historyTable.getSelectedRowCount();
        // Neither may touch the display of a running sweep.
        saveProfile.setEnabled(selected == 1 && !running);
        deleteRun.setEnabled(selected >= 1 && !running);
    }

    @Override
    public void updateUI() {
        super.updateUI();
        if (stageTable != null) {
            Tables.fitRowHeight(stageTable);
            Tables.fitRowHeight(historyTable);
            verdict.setFont(Theme.derived(1.1f, true));
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
            noModelsHint = true;
        } else if (noModelsHint) {
            // Only the hint of this panel goes; a confirmation shown meanwhile stays.
            banner.clear();
            noModelsHint = false;
        }
        updateButtons();
    }

    @Override
    public void showServerParallel(String text) {
        serverParallel = text;
        serverPresent = true;
        updateServerNotice();
        if (text == null) {
            serverHint.setText("OLLAMA_NUM_PARALLEL of this server is unknown; stages above it queue on the server.");
        } else if (BenchmarksPresenter.SERVER_DEFAULT.equals(text)) {
            serverHint.setText("OLLAMA_NUM_PARALLEL is not set; Ollama picks 1 or 4 by itself. Stages above that queue.");
        } else {
            serverHint.setText("OLLAMA_NUM_PARALLEL = " + text + " on the running server. Stages above that queue.");
        }
    }

    @Override
    public void setSweepAvailable(boolean available, String reason) {
        sweepAvailable = available;
        sweep.setEnabled(available && !running);
        serverValues.setEnabled(available && sweep.isSelected() && !running);
        sweepHint.setText(available || reason == null ? " " : reason);
        updateServerNotice();
    }

    @Override
    public void setServerRestartAvailable(boolean available, boolean external) {
        serverRestartAvailable = available;
        serverRestartExternal = external;
        updateServerNotice();
    }

    @Override
    public void showServerRestarted(int numParallel) {
        noModelsHint = false;
        banner.show(Theme.Tone.SUCCESS, "The server now runs with OLLAMA_NUM_PARALLEL = " + numParallel
            + "; the value is stored in the server settings.");
    }

    @Override
    public void showUnavailable(String reason) {
        hasModels = false;
        model.setModel(new DefaultComboBoxModel<String>());
        banner.warning(reason);
        noModelsHint = false;
        setSweepAvailable(false, null);
        serverParallel = null;
        serverPresent = false;
        setServerRestartAvailable(false, false);
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
        sweep.setEnabled(!isRunning && sweepAvailable);
        serverValues.setEnabled(!isRunning && sweepAvailable && sweep.isSelected());
        raiseParallel.setEnabled(!isRunning && raiseTarget > 0);
        updateButtons();
    }

    @Override
    public void runEnded(boolean failed) {
        if (!"Running…".equals(verdict.getText())) {
            return;
        }
        if (failed) {
            verdict.setText(chart.pointCount() == 0 ? "Failed before the first stage finished; see the message above."
                : "Failed; stages so far are shown. See the message above.");
        } else {
            verdict.setText(chart.pointCount() == 0 ? "Cancelled before the first stage finished."
                : "Cancelled; stages so far are shown.");
        }
    }

    @Override
    public void stageFinished(StageResult stage) {
        chart.addPoint(toPoint(stage));
        stageModel.add(liveLabel(), stage);
    }

    /** Label of the server value a running sweep is currently measuring; empty for a plain run. */
    private String liveLabel() {
        return currentSweepLabel;
    }

    /** Server values of the running sweep in the order the sweep tries them; empty for a plain run. */
    private List<Integer> sweepCandidates = Collections.emptyList();
    private String currentSweepLabel = "";

    @Override
    public void reportFinished(BenchmarkReport report) {
        shown.add(report);
        // The next stages belong to the next server value; after the last one no new line is started.
        int next = shown.size();
        if (next < sweepCandidates.size()) {
            currentSweepLabel = String.valueOf(sweepCandidates.get(next));
            chart.startSeries(seriesName(currentSweepLabel));
        } else {
            currentSweepLabel = "";
        }
        verdict.setText(verdictText(shown));
    }

    static String seriesName(String serverParallel) {
        return "OLLAMA_NUM_PARALLEL=" + (serverParallel == null ? "?" : serverParallel);
    }

    @Override
    public void showReport(BenchmarkReport report) {
        showReports(Collections.singletonList(report));
    }

    @Override
    public void showReports(List<BenchmarkReport> reports) {
        shown.clear();
        shown.addAll(reports);
        List<ThroughputChart.Series> series = new ArrayList<ThroughputChart.Series>();
        for (BenchmarkReport report : reports) {
            ThroughputChart.Series line = new ThroughputChart.Series(reports.size() == 1 ? report.model()
                : seriesName(report.serverNumParallel()) + "  " + report.model());
            for (StageResult stage : report.stages()) {
                line.add(toPoint(stage));
            }
            series.add(line);
        }
        chart.setSeries(series);
        stageModel.setRows(reports);
        verdict.setText(verdictText(reports));
    }

    /** One line naming the optimum; for several runs the run with the best optimum. */
    static String verdictText(List<BenchmarkReport> reports) {
        BenchmarkReport best = null;
        for (BenchmarkReport report : reports) {
            if (report.optimum() != null && (best == null || report.optimum().aggregateTps()
                > best.optimum().aggregateTps())) {
                best = report;
            }
        }
        if (best == null) {
            boolean empty = true;
            for (BenchmarkReport report : reports) {
                empty &= report.stages().isEmpty();
            }
            return empty ? "No stage finished." : "No stable stage: every stage had errors or left the GPU.";
        }
        StageResult optimum = best.optimum();
        String text = String.format(Locale.ROOT, "%s: optimum %d parallel %s at %.1f tok/s total (%.1f tok/s per chat)",
            best.model(), optimum.parallel(), optimum.parallel() == 1 ? "chat" : "chats", optimum.aggregateTps(),
            optimum.perChatTpsAvg());
        if (reports.size() > 1 && best.serverNumParallel() != null) {
            text = "OLLAMA_NUM_PARALLEL=" + best.serverNumParallel() + " wins. " + text;
        }
        StageResult maxStable = best.maxStable();
        if (maxStable != null && maxStable.parallel() > optimum.parallel()) {
            text += String.format(Locale.ROOT, "; still stable up to %d", maxStable.parallel());
        }
        return best.complete() ? text : text + " (run incomplete)";
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
    public void showProfileSaved(ModelProfile profile) {
        noModelsHint = false;
        banner.info("Profile \"" + profile.name() + "\" saved. Activate it under Profiles.");
    }

    @Override
    public void showFailure(String action, Exception error) {
        noModelsHint = false;
        banner.error(action, error);
    }

    @Override
    public void trackJob(Job job) {
        progress.attach(job);
    }

    /** Per-stage numbers of one or more runs. */
    static final class StageTableModel extends AbstractTableModel {
        private static final long serialVersionUID = 1L;
        private static final String[] COLUMNS = {"Server", "Chats", "Total tok/s", "Per chat", "Slowest chat",
            "1st token p50", "1st token p95", "Latency p95", "Peak VRAM", "State"};
        private final List<String> labels = new ArrayList<String>();
        private final List<StageResult> rows = new ArrayList<StageResult>();

        void setRows(List<BenchmarkReport> reports) {
            labels.clear();
            rows.clear();
            for (BenchmarkReport report : reports) {
                for (StageResult stage : report.stages()) {
                    labels.add(report.serverNumParallel() == null ? "?" : report.serverNumParallel());
                    rows.add(stage);
                }
            }
            fireTableDataChanged();
        }

        void add(String label, StageResult stage) {
            labels.add(label == null ? "" : label);
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
                case 0: return labels.get(row);
                case 1: return String.valueOf(s.parallel());
                case 2: return tps(s.aggregateTps());
                case 3: return tps(s.perChatTpsAvg());
                case 4: return tps(s.perChatTpsMin());
                case 5: return s.ttftP50Millis() + " ms";
                case 6: return s.ttftP95Millis() + " ms";
                case 7: return s.latencyP95Millis() + " ms";
                case 8: return s.peakVramBytes() == null ? "–" : Format.bytes(s.peakVramBytes());
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
        private static final String[] COLUMNS = {"When", "Model", "Server", "num_ctx", "Optimum", "Total tok/s"};
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
                case 2: return r.serverNumParallel() == null ? "?" : r.serverNumParallel();
                case 3: return r.numCtx() == null ? "default" : String.valueOf(r.numCtx());
                case 4: return optimum == null ? "–" : String.valueOf(optimum.parallel());
                default: return optimum == null ? "–" : tps(optimum.aggregateTps());
            }
        }
    }
}
