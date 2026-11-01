package com.aresstack.ollamamanager.swing.shell;

import com.aresstack.askai.java8.ui.AppIcon;
import com.aresstack.askai.java8.ui.AskAiTitleBar;
import com.aresstack.comiccontrols.control.ComicWindowDragger;
import com.aresstack.comiccontrols.control.ComicWindowResizer;
import com.aresstack.comiccontrols.control.ComicWindowShape;
import com.aresstack.comiccontrols.theme.ComicPalette;
import com.aresstack.comiccontrols.theme.ResearchUiMetrics;
import com.aresstack.comiccontrols.theme.ThemeColor;
import com.aresstack.ollamamanager.swing.component.ComicStyle;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import com.aresstack.ollamamanager.swing.job.JobManager;
import com.aresstack.ollamamanager.swing.section.BenchmarksPanel;
import com.aresstack.ollamamanager.swing.section.BenchmarksPresenter;
import com.aresstack.ollamamanager.swing.section.DashboardPanel;
import com.aresstack.ollamamanager.swing.section.DashboardPresenter;
import com.aresstack.ollamamanager.swing.section.JobsPanel;
import com.aresstack.ollamamanager.swing.section.InstallModelsPanel;
import com.aresstack.ollamamanager.swing.section.ModelsPanel;
import com.aresstack.ollamamanager.swing.section.ModelsPresenter;
import com.aresstack.ollamamanager.swing.section.SetupPanel;
import com.aresstack.ollamamanager.swing.section.SetupPresenter;
import com.aresstack.ollamamanager.swing.section.ProfilesPanel;
import com.aresstack.ollamamanager.swing.section.ProfilesPresenter;
import com.aresstack.ollamamanager.swing.section.ServerSettingsPanel;
import com.aresstack.ollamamanager.swing.section.ServerSettingsPresenter;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.KeyStroke;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.EnumMap;
import java.util.Map;

/**
 * Console window: navigation sidebar, one card per {@link Section}, status bar and menus with keyboard shortcuts.
 * It only arranges views; data access lives in presenters that call application services through jobs.
 */
public class ConsoleShell extends JFrame {

    private static final long serialVersionUID = 1L;

    private final transient JobManager jobs;
    private final transient WindowStateStore state = WindowStateStore.forUser();
    private final transient FontScale fontScale = new FontScale();
    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);
    private final NavigationList navigation = new NavigationList();
    private final Map<Section, JComponent> panels = new EnumMap<Section, JComponent>(Section.class);
    private final JSplitPane split;
    /** The frameless window's ink-contour padding (doubles as the resize grip zone), as in askai. */
    private static final int WINDOW_PADDING = 4;
    private static final int RESIZE_GRIP = 6;

    public ConsoleShell(ConsoleServices services, JobManager jobs, AutoCloseable onClose) {
        super("Ollama Manager");
        // As askai's AskAiFrame: frameless, with the askai title bar and the comic window contour.
        setUndecorated(true);
        AppIcon.apply(this);
        this.jobs = jobs;
        fontScale.apply(state.fontScale());
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        DashboardPresenter dashboard = new DashboardPresenter(services, jobs);
        panels.put(Section.DASHBOARD, new DashboardPanel(dashboard));
        panels.put(Section.SETUP, new SetupPanel(new SetupPresenter(services, jobs)));
        panels.put(Section.MODELS, new ModelsPanel(new ModelsPresenter(services, jobs)));
        panels.put(Section.INSTALL, new InstallModelsPanel(services));
        panels.put(Section.SERVER_SETTINGS, new ServerSettingsPanel(new ServerSettingsPresenter(services, jobs)));
        panels.put(Section.PROFILES, new ProfilesPanel(new ProfilesPresenter(services, jobs)));
        panels.put(Section.JOBS, new JobsPanel(services, jobs));
        panels.put(Section.BENCHMARKS, new BenchmarksPanel(new BenchmarksPresenter(services, jobs)));
        for (Map.Entry<Section, JComponent> entry : panels.entrySet()) {
            content.add(entry.getValue(), entry.getKey().name());
        }

        navigation.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting() && navigation.getSelectedValue() != null) {
                show(navigation.getSelectedValue());
            }
        });
        navigation.setMinimumSize(new Dimension(160, 100));
        content.setMinimumSize(new Dimension(400, 100));
        split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, navigation, content);
        split.setDividerLocation(state.dividerLocation(220));
        split.setContinuousLayout(true);
        split.setBorder(null);

        JPanel root = new JPanel(new BorderLayout());
        root.add(split, BorderLayout.CENTER);
        root.add(new StatusBar(jobs), BorderLayout.SOUTH);
        AskAiTitleBar titleBar = new AskAiTitleBar(getTitle(),
            () -> setState(Frame.ICONIFIED),
            () -> dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING)));
        // Title bar ABOVE the menu bar, both inside the frameless window's ink contour (as in askai).
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setOpaque(false);
        titleBar.setAlignmentX(LEFT_ALIGNMENT);
        header.add(titleBar);
        JMenuBar menus = menuBar();
        menus.setAlignmentX(LEFT_ALIGNMENT);
        menus.setMaximumSize(new Dimension(Integer.MAX_VALUE, menus.getPreferredSize().height));
        header.add(menus);
        JPanel windowSurface = new JPanel(new BorderLayout());
        windowSurface.add(header, BorderLayout.NORTH);
        windowSurface.add(root, BorderLayout.CENTER);
        setContentPane(windowSurface);
        ComicWindowShape.install(this, windowSurface, ComicPalette.defaultPalette(), WINDOW_PADDING,
            ResearchUiMetrics.RADIUS_WINDOW);
        ComicWindowResizer.install(windowSurface, RESIZE_GRIP);
        ComicWindowDragger.install(titleBar);
        ComicStyle.apply(getRootPane());

        setMinimumSize(new Dimension(720, 480));
        restoreBounds();
        navigation.setSelectedValue(state.lastSection(), true);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent event) {
                navigation.requestFocusInWindow();
                dashboard.refresh();
            }

            @Override
            public void windowClosing(WindowEvent event) {
                state.save(ConsoleShell.this);
                state.saveDividerLocation(split.getDividerLocation());
            }

            @Override
            public void windowClosed(WindowEvent event) {
                jobs.cancelAll();
                try {
                    onClose.close();
                } catch (Exception ignored) {
                    // Nothing left to clean up on exit.
                }
            }
        });
    }

    /** Switch to {@code section} and remember it. */
    public final void show(Section section) {
        cards.show(content, section.name());
        if (navigation.getSelectedValue() != section) {
            navigation.setSelectedValue(section, true);
        }
        state.saveSection(section);
    }

    /** Scale all fonts and relayout. */
    public final void setFontScale(float scale) {
        fontScale.apply(scale);
        state.saveFontScale(fontScale.scale());
        ComicStyle.refresh(this);
        validate();
        repaint();
    }

    JobManager jobs() {
        return jobs;
    }

    private void restoreBounds() {
        Rectangle saved = state.bounds(WindowStateStore.screens());
        if (saved != null) {
            setBounds(saved);
        } else {
            setSize(1024, 700);
            setLocationRelativeTo(null);
        }
        if (state.maximized()) {
            setExtendedState(getExtendedState() | Frame.MAXIMIZED_BOTH);
        }
    }

    private JMenuBar menuBar() {
        int mask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();
        JMenuBar bar = new JMenuBar();
        // Accent strip: the comic surface darkened towards the ink, so the menus stand out from the page.
        ComicPalette palette = ComicPalette.defaultPalette();
        bar.setOpaque(true);
        // Darker brown than the page: the surface pulled towards the ink and the orange accent.
        bar.setBackground(ThemeColor.mix(ThemeColor.mix(palette.getSurface(), palette.getAccentOrange(), 0.22f),
            palette.getInk(), 0.18f));
        bar.setBorder(BorderFactory.createMatteBorder(1, 0, 1, 0,
            ThemeColor.mix(palette.getSurface(), palette.getInk(), 0.45f)));

        JMenu file = new JMenu("File");
        file.setMnemonic('F');
        file.add(item("Exit", 'x', KeyStroke.getKeyStroke(KeyEvent.VK_Q, mask),
            () -> dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING))));
        bar.add(file);

        JMenu go = new JMenu("Go");
        go.setMnemonic('G');
        for (Section section : Section.values()) {
            go.add(item(section.title(), (char) 0, KeyStroke.getKeyStroke(section.shortcutKey(), mask),
                () -> show(section)));
        }
        bar.add(go);

        JMenu view = new JMenu("View");
        view.setMnemonic('V');
        view.add(item("Larger text", 'L', KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, mask),
            () -> setFontScale(fontScale.scale() + FontScale.STEP)));
        view.add(item("Smaller text", 'S', KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, mask),
            () -> setFontScale(fontScale.scale() - FontScale.STEP)));
        view.add(item("Normal text size", 'N', KeyStroke.getKeyStroke(KeyEvent.VK_0, mask),
            () -> setFontScale(1f)));
        bar.add(view);
        // Also accept Ctrl+Plus on the numeric keypad.
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
            .put(KeyStroke.getKeyStroke(KeyEvent.VK_ADD, mask), "font.larger");
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
            .put(KeyStroke.getKeyStroke(KeyEvent.VK_SUBTRACT, mask), "font.smaller");
        getRootPane().getActionMap().put("font.larger", new RunAction(
            () -> setFontScale(fontScale.scale() + FontScale.STEP)));
        getRootPane().getActionMap().put("font.smaller", new RunAction(
            () -> setFontScale(fontScale.scale() - FontScale.STEP)));
        return bar;
    }

    private static JMenuItem item(String text, char mnemonic, KeyStroke accelerator, Runnable action) {
        JMenuItem item = new JMenuItem(new RunAction(action));
        item.setText(text);
        if (mnemonic != 0) {
            item.setMnemonic(mnemonic);
        }
        item.setAccelerator(accelerator);
        return item;
    }

    private static final class RunAction extends AbstractAction {
        private static final long serialVersionUID = 1L;
        private final transient Runnable action;

        RunAction(Runnable action) {
            this.action = action;
        }

        @Override
        public void actionPerformed(ActionEvent event) {
            action.run();
        }
    }
}
