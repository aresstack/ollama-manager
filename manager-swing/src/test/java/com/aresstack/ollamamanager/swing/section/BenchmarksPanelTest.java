package com.aresstack.ollamamanager.swing.section;

import com.aresstack.ollamamanager.application.benchmark.BenchmarkReport;
import com.aresstack.ollamamanager.application.benchmark.StageResult;
import com.aresstack.ollamamanager.swing.component.MessageBanner;
import com.aresstack.ollamamanager.swing.component.Theme;
import com.aresstack.ollamamanager.swing.job.JobManager;
import org.junit.Test;

import java.awt.Component;
import java.awt.Container;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class BenchmarksPanelTest {

    @Test
    public void parsesListsAndRanges() {
        assertEquals(Arrays.asList(1, 2, 4, 8), BenchmarksPanel.parseLevels("1, 2 4;8"));
        assertEquals(Arrays.asList(2, 3, 4), BenchmarksPanel.parseLevels("2-4"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsOutOfRangeEndpointsBeforeExpanding() {
        BenchmarksPanel.parseLevels("2147483647-2147483647");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsZero() {
        BenchmarksPanel.parseLevels("0, 4");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsEmptyInput() {
        BenchmarksPanel.parseLevels("  ");
    }

    @Test
    public void verdictNamesOptimumAndStableMaximum() {
        BenchmarkReport report = new BenchmarkReport("id", 0, "m", null, 50, null, 0, Arrays.asList(
            new StageResult(1, 1, 0, 50, 50, 50, 1, 1, 1, 1, 100, null, 1.0, null),
            new StageResult(4, 4, 0, 30, 28, 120, 1, 1, 1, 1, 100, null, 1.0, null),
            new StageResult(8, 8, 0, 14, 10, 112, 1, 1, 1, 1, 100, null, 1.0, null)), true);
        String text = BenchmarksPanel.verdictText(java.util.Collections.singletonList(report));
        assertTrue(text, text.contains("optimum 4 parallel chats"));
        assertTrue(text, text.contains("still stable up to 8"));
    }

    @Test
    public void verdictOverSeveralRunsNamesTheWinningServerValue() {
        BenchmarkReport slow = new BenchmarkReport("a", 0, "m", null, 50, "1", 0, Arrays.asList(
            new StageResult(1, 1, 0, 50, 50, 50, 1, 1, 1, 1, 100, null, 1.0, null),
            new StageResult(4, 4, 0, 13, 12, 52, 1, 1, 1, 1, 100, null, 1.0, null)), true);
        BenchmarkReport fast = new BenchmarkReport("b", 0, "m", null, 50, "8", 0, Arrays.asList(
            new StageResult(1, 1, 0, 50, 50, 50, 1, 1, 1, 1, 100, null, 1.0, null),
            new StageResult(4, 4, 0, 40, 38, 160, 1, 1, 1, 1, 100, null, 1.0, null)), true);
        String text = BenchmarksPanel.verdictText(Arrays.asList(slow, fast));
        assertTrue(text, text.startsWith("OLLAMA_NUM_PARALLEL=8 wins."));
        assertTrue(text, text.contains("optimum 4 parallel chats"));
    }

    private static final List<Integer> UP_TO_16 = Arrays.asList(1, 2, 4, 8, 16);

    @Test
    public void warnsInRedWhenTheServerValueIsBelowTheHighestStage() {
        BenchmarksPanel.ServerNotice notice = BenchmarksPanel.serverNotice("8", UP_TO_16, false, true);
        assertEquals(Theme.Tone.ERROR, notice.tone);
        assertEquals(16, notice.target);
        assertTrue(notice.restartable);
        assertTrue(notice.text, notice.text.contains("OLLAMA_NUM_PARALLEL is 8"));
        assertTrue(notice.text, notice.text.contains("up to 16"));
        assertFalse(notice.text, notice.text.contains("where the server is started"));
    }

    @Test
    public void saysNothingWhenTheServerValueCoversEveryStage() {
        assertNull(BenchmarksPanel.serverNotice("16", UP_TO_16, false, true));
        assertNull(BenchmarksPanel.serverNotice("32", UP_TO_16, false, true));
        assertNull(BenchmarksPanel.serverNotice("1", Collections.singletonList(1), false, true));
    }

    @Test
    public void treatsAnUnsetValueAsTooLowForMoreThanOneChat() {
        BenchmarksPanel.ServerNotice notice = BenchmarksPanel.serverNotice(BenchmarksPresenter.SERVER_DEFAULT,
            Arrays.asList(1, 2), false, true);
        assertEquals(Theme.Tone.ERROR, notice.tone);
        assertEquals(2, notice.target);
        assertTrue(notice.text, notice.text.contains("not set"));
        assertNull(BenchmarksPanel.serverNotice(BenchmarksPresenter.SERVER_DEFAULT,
            Collections.singletonList(1), false, true));
    }

    @Test
    public void warnsSofterWhileTheServerValueIsUnknown() {
        BenchmarksPanel.ServerNotice notice = BenchmarksPanel.serverNotice(null, UP_TO_16, false, true);
        assertEquals(Theme.Tone.WARNING, notice.tone);
        assertEquals(16, notice.target);
        assertTrue(notice.text, notice.text.contains("unknown"));
        assertNull(BenchmarksPanel.serverNotice(null, Collections.singletonList(1), false, true));
    }

    @Test
    public void offersNoRestartButNamesTheValueWhenTheServerCannotBeRestartedFromHere() {
        BenchmarksPanel.ServerNotice notice = BenchmarksPanel.serverNotice("4", UP_TO_16, false, false);
        assertFalse(notice.restartable);
        assertTrue(notice.text, notice.text.endsWith("Set it to at least 16 where the server is started."));
    }

    @Test
    public void staysQuietDuringASweepOrWithoutParsableLevels() {
        assertNull(BenchmarksPanel.serverNotice("4", UP_TO_16, true, true));
        assertNull(BenchmarksPanel.serverNotice("4", Collections.<Integer>emptyList(), false, true));
        assertNull(BenchmarksPanel.serverNotice("?", UP_TO_16, false, true));
    }

    @Test
    public void restartConfirmationSurvivesTheModelReload() {
        BenchmarksPanel panel = new BenchmarksPanel(new BenchmarksPresenter(TestServices.builder().build(),
            new JobManager(Runnable::run, Runnable::run)));
        panel.showModels(Collections.<String>emptyList());
        MessageBanner banner = banner(panel);
        assertTrue(banner.message(), banner.message().startsWith("No models installed"));

        panel.showServerRestarted(16);
        panel.showModels(Arrays.asList("llama3.1:8b"));

        assertEquals(Theme.Tone.SUCCESS, banner.tone());
        assertTrue(banner.message(), banner.message().contains("OLLAMA_NUM_PARALLEL = 16"));
        assertTrue(banner.isVisible());
    }

    @Test
    public void noModelsHintGoesAwayOnceModelsArrive() {
        BenchmarksPanel panel = new BenchmarksPanel(new BenchmarksPresenter(TestServices.builder().build(),
            new JobManager(Runnable::run, Runnable::run)));
        panel.showModels(Collections.<String>emptyList());
        panel.showModels(Arrays.asList("llama3.1:8b"));
        assertFalse(banner(panel).isVisible());
    }

    /** The first message banner in the panel tree: the one above the split pane. */
    private static MessageBanner banner(Container root) {
        for (Component child : root.getComponents()) {
            if (child instanceof MessageBanner) {
                return (MessageBanner) child;
            }
            if (child instanceof Container) {
                MessageBanner found = banner((Container) child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
