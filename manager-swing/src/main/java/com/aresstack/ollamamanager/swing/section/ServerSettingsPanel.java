package com.aresstack.ollamamanager.swing.section;

import com.aresstack.ollamamanager.domain.configuration.ManagerConfiguration;
import com.aresstack.ollamamanager.domain.server.ServerSetting;
import com.aresstack.ollamamanager.domain.server.ServerSettingCatalog;
import com.aresstack.ollamamanager.domain.server.ServerSettings;
import com.aresstack.ollamamanager.swing.component.Dialogs;
import com.aresstack.ollamamanager.swing.component.FormPanel;
import com.aresstack.ollamamanager.swing.component.SectionPanel;
import com.aresstack.ollamamanager.swing.component.ValueLabel;
import com.aresstack.ollamamanager.swing.job.JobManager;
import com.aresstack.ollamamanager.swing.shell.ConsoleServices;
import com.aresstack.ollamamanager.swing.shell.Section;

import javax.swing.JButton;
import javax.swing.JScrollPane;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Desired server-wide settings with explanations from the setting catalog. Read-only in this skeleton; editing
 * goes through {@code ConfigurationService.update} when the editor is connected.
 */
public final class ServerSettingsPanel extends SectionPanel {

    private static final long serialVersionUID = 1L;

    private final Map<ServerSetting<?>, ValueLabel> values = new LinkedHashMap<ServerSetting<?>, ValueLabel>();

    public ServerSettingsPanel(ConsoleServices services, JobManager jobs) {
        super(Section.SERVER_SETTINGS.title(), Section.SERVER_SETTINGS.description());
        FormPanel form = new FormPanel();
        for (ServerSetting<?> setting : ServerSettingCatalog.all()) {
            ValueLabel value = new ValueLabel();
            values.put(setting, value);
            form.addRow(setting.label(), value, tooltip(setting));
        }
        form.addFiller();
        JScrollPane scroll = new JScrollPane(form);
        scroll.setBorder(null);
        setBody(scroll);

        JButton reload = new JButton("Reload");
        reload.setMnemonic('L');
        reload.addActionListener(event -> load(services, jobs));
        addToolbarItem(reload);
        load(services, jobs);
    }

    static String tooltip(ServerSetting<?> setting) {
        StringBuilder text = new StringBuilder("<html>").append(escape(setting.tooltip()));
        if (setting.environmentVariable() != null) {
            text.append("<br>Environment variable: ").append(escape(setting.environmentVariable()));
        }
        text.append("<br>").append(setting.isRestartRequired() ? "Requires a server restart." : "Applies per request.");
        return text.append("</html>").toString();
    }

    private static String escape(String text) {
        return com.aresstack.ollamamanager.swing.component.Html.escape(text);
    }

    private void load(ConsoleServices services, JobManager jobs) {
        jobs.submit("Load server settings", false, context -> services.configuration().current(),
            this::show, error -> Dialogs.showError(this, "Load server settings", error));
    }

    private void show(ManagerConfiguration configuration) {
        ServerSettings settings = configuration.desiredServerSettings();
        Map<String, String> encoded = settings.toEncodedMap();
        for (Map.Entry<ServerSetting<?>, ValueLabel> entry : values.entrySet()) {
            String value = encoded.get(entry.getKey().id());
            entry.getValue().show(value == null ? "Ollama default" : value);
        }
    }
}
