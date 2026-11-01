package com.aresstack.ollamamanager.swing.section;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ServerSettingsPanelTest {

    @Test
    public void shortValuesStayAsTheyAre() {
        assertEquals("4096", ServerSettingsPanel.shorten("4096"));
        assertEquals("127.0.0.1:11434", ServerSettingsPanel.shorten("127.0.0.1:11434"));
    }

    @Test
    public void longPathsKeepTheirTailBehindAnEllipsis() {
        String shortened = ServerSettingsPanel.shorten("/home/angelo/models/ollama/models");
        assertTrue(shortened, shortened.startsWith("…"));
        assertTrue(shortened, shortened.endsWith("ollama/models"));
        assertTrue(shortened, shortened.length() <= 24);
    }
}
