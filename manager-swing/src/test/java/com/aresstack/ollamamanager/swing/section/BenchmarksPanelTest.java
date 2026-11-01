package com.aresstack.ollamamanager.swing.section;

import com.aresstack.ollamamanager.application.benchmark.BenchmarkReport;
import com.aresstack.ollamamanager.application.benchmark.StageResult;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
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
}
