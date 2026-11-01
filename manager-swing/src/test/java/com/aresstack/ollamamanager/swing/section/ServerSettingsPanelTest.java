package com.aresstack.ollamamanager.swing.section;

import com.aresstack.ollamamanager.domain.server.ServerSetting;
import com.aresstack.ollamamanager.domain.server.ServerSettingCatalog;
import com.aresstack.ollamamanager.swing.job.JobManager;
import com.aresstack.ollamamanager.swing.job.UiDispatcher;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

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

    @Test
    public void editorsRoundTripStoredValuesAndKeepUnsetSettingsEmpty() {
        ServerSettingsPanel panel = newPanel();
        Map<ServerSetting<?>, String> stored = new LinkedHashMap<ServerSetting<?>, String>();
        stored.put(ServerSettingCatalog.BIND, "0.0.0.0:8080");
        stored.put(ServerSettingCatalog.PARALLELISM, "2");
        stored.put(ServerSettingCatalog.CONTEXT_LENGTH, "40000");
        stored.put(ServerSettingCatalog.KV_CACHE, "q8_0");
        stored.put(ServerSettingCatalog.FLASH_ATTENTION, "1");
        stored.put(ServerSettingCatalog.KEEP_ALIVE, "1h");
        panel.showValues(stored);

        Map<ServerSetting<?>, String> edited = panel.editedValues();
        assertEquals("0.0.0.0:8080", edited.get(ServerSettingCatalog.BIND));
        assertEquals("2", edited.get(ServerSettingCatalog.PARALLELISM));
        assertEquals("40000", edited.get(ServerSettingCatalog.CONTEXT_LENGTH));
        assertEquals("q8_0", edited.get(ServerSettingCatalog.KV_CACHE));
        assertEquals("1", edited.get(ServerSettingCatalog.FLASH_ATTENTION));
        assertEquals("1h", edited.get(ServerSettingCatalog.KEEP_ALIVE));
        // Nothing stored: the variable must stay unset, whatever default the greyed control shows.
        assertEquals("", edited.get(ServerSettingCatalog.LOADED_MODELS));
        assertEquals("", edited.get(ServerSettingCatalog.QUEUE));
        assertEquals("", edited.get(ServerSettingCatalog.MODEL_ROOT));
    }

    @Test
    public void keepAliveSpecialValuesSurvive() {
        ServerSettingsPanel panel = newPanel();
        Map<ServerSetting<?>, String> stored = new LinkedHashMap<ServerSetting<?>, String>();
        stored.put(ServerSettingCatalog.KEEP_ALIVE, "-1");
        panel.showValues(stored);
        assertEquals("-1", panel.editedValues().get(ServerSettingCatalog.KEEP_ALIVE));
        stored.put(ServerSettingCatalog.KEEP_ALIVE, "0");
        panel.showValues(stored);
        assertEquals("0", panel.editedValues().get(ServerSettingCatalog.KEEP_ALIVE));
        stored.put(ServerSettingCatalog.KEEP_ALIVE, "30s");
        panel.showValues(stored);
        assertEquals("30s", panel.editedValues().get(ServerSettingCatalog.KEEP_ALIVE));
    }

    @Test
    public void clearingValuesUnsetsEveryEditor() {
        ServerSettingsPanel panel = newPanel();
        Map<ServerSetting<?>, String> stored = new LinkedHashMap<ServerSetting<?>, String>();
        stored.put(ServerSettingCatalog.CONTEXT_LENGTH, "8192");
        panel.showValues(stored);
        panel.showValues(new LinkedHashMap<ServerSetting<?>, String>());
        for (String text : panel.editedValues().values()) {
            assertEquals("", text);
        }
    }

    @Test
    public void contextTickLabelsUseShortUnits() {
        assertEquals("256", ServerSettingsPanel.ContextLengthEditor.tokens(256));
        assertEquals("4k", ServerSettingsPanel.ContextLengthEditor.tokens(4096));
        assertEquals("1M", ServerSettingsPanel.ContextLengthEditor.tokens(1 << 20));
    }

    private static ServerSettingsPanel newPanel() {
        JobManager jobs = new JobManager(command -> { }, UiDispatcher.inline());
        return new ServerSettingsPanel(new ServerSettingsPresenter(TestServices.builder().build(), jobs));
    }
}
