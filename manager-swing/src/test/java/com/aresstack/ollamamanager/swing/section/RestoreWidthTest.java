package com.aresstack.ollamamanager.swing.section;

import com.aresstack.ollamamanager.swing.job.JobManager;
import com.aresstack.ollamamanager.swing.job.UiDispatcher;
import org.junit.Test;

import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.JViewport;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertTrue;

/** Every section must fit back into a narrow window after it was maximized (no content stuck at the wide width). */
public class RestoreWidthTest {

    @Test
    public void everySectionShrinksBackAfterTheWindowWasWide() {
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, JComponent> section : sections().entrySet()) {
            JComponent panel = section.getValue();
            fillWrappedText(panel); // real banners and hints carry long sentences
            layout(panel, 900, 600);
            layout(panel, 1900, 1000);
            layout(panel, 900, 600);
            collect(section.getKey(), panel, panel, problems);
        }
        assertTrue(String.join("\n", problems), problems.isEmpty());
    }

    static Map<String, JComponent> sections() {
        com.aresstack.ollamamanager.swing.shell.ConsoleServices services = TestServices.builder().build();
        JobManager jobs = new JobManager(command -> { }, UiDispatcher.inline());
        Map<String, JComponent> sections = new LinkedHashMap<>();
        sections.put("Dashboard", new DashboardPanel(new DashboardPresenter(services, jobs)));
        sections.put("Setup", new SetupPanel(new SetupPresenter(services, jobs)));
        sections.put("Models", new ModelsPanel(new ModelsPresenter(services, jobs)));
        sections.put("Install models", new InstallModelsPanel(services));
        sections.put("Server settings", new ServerSettingsPanel(new ServerSettingsPresenter(services, jobs)));
        sections.put("Profiles", new ProfilesPanel(new ProfilesPresenter(services, jobs)));
        sections.put("Jobs", new JobsPanel(services, jobs));
        sections.put("Benchmarks", new BenchmarksPanel(new BenchmarksPresenter(services, jobs)));
        return sections;
    }

    private static void fillWrappedText(Container container) {
        for (Component child : container.getComponents()) {
            if (child instanceof javax.swing.JTextArea && ((javax.swing.JTextArea) child).getLineWrap()) {
                StringBuilder text = new StringBuilder();
                for (int i = 0; i < 30; i++) {
                    text.append("A long sentence that has to wrap. ");
                }
                ((javax.swing.JTextArea) child).setText(text.toString());
                child.setVisible(true);
                for (Container p = child.getParent(); p != null && p != container; p = p.getParent()) {
                    p.setVisible(true);
                }
                child.getParent().setVisible(true);
            }
            if (child instanceof Container) {
                fillWrappedText((Container) child);
            }
        }
    }

    static void layout(Container panel, int width, int height) {
        panel.setSize(width, height);
        // two passes, like Swing's revalidate after wrapped text got its new width
        layoutTree(panel);
        layoutTree(panel);
    }

    private static void layoutTree(Container container) {
        container.doLayout();
        for (Component child : container.getComponents()) {
            if (child instanceof Container) {
                layoutTree((Container) child);
            }
        }
    }

    private static void collect(String section, Container root, Container container, List<String> problems) {
        for (Component child : container.getComponents()) {
            if (!child.isVisible()) {
                continue;
            }
            if (child instanceof JViewport) {
                JViewport viewport = (JViewport) child;
                Component view = viewport.getView();
                if (view != null && !(view instanceof JTable) && view.getWidth() > viewport.getWidth() + 1) {
                    problems.add(section + ": " + view.getClass().getSimpleName() + " width " + view.getWidth()
                        + " > viewport " + viewport.getWidth());
                }
                continue; // content inside a viewport may scroll
            }
            int right = javax.swing.SwingUtilities.convertPoint(container, child.getX() + child.getWidth(), 0, root).x;
            if (right > root.getWidth() + 1) {
                problems.add(section + ": " + child.getClass().getSimpleName() + " ends at " + right + " > "
                    + root.getWidth());
            }
            if (child instanceof Container) {
                collect(section, root, (Container) child, problems);
            }
        }
    }
}
