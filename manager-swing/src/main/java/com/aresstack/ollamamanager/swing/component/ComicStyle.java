package com.aresstack.ollamamanager.swing.component;

import com.aresstack.comiccontrols.control.ComicComboBoxUI;
import com.aresstack.comiccontrols.control.ComicFormControls;
import com.aresstack.comiccontrols.control.ComicListSupport;
import com.aresstack.comiccontrols.control.ComicProgressBarUI;
import com.aresstack.comiccontrols.control.ComicScrollBarUI;
import com.aresstack.comiccontrols.control.ComicTableSupport;
import com.aresstack.comiccontrols.control.ComicTextFieldUI;
import com.aresstack.comiccontrols.theme.AskAiTheme;
import com.aresstack.comiccontrols.theme.ComicTheme;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFormattedTextField;
import javax.swing.JList;
import javax.swing.JPasswordField;
import javax.swing.JProgressBar;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.JRadioButton;
import javax.swing.SwingUtilities;
import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Toolkit;
import java.awt.event.ContainerEvent;
import java.awt.Container;

/**
 * Puts the askai-java8 comic design language (module manager-askai, package {@code com.aresstack.comiccontrols},
 * copied unchanged) on every tab: {@link #installTheme()} registers the comic delegates and colors exactly like
 * askai's {@code AskAiFrame}, and {@link #apply(Component)} walks a component tree and hands each control to the
 * matching askai installer, so panels keep their own construction code and behaviour.
 */
public final class ComicStyle {

    /** Client property: {@code Boolean.TRUE} keeps a component (and its children) out of {@link #apply}. */
    public static final String SKIP = "ollamamanager.comic.skip";

    private ComicStyle() {
    }

    /** As askai's {@code AskAiFrame}: the theme first, so everything built afterwards picks up the comic look. */
    public static void installTheme() {
        ComicTheme.install(AskAiTheme.active());
        ComicTheme.installMenuDefaults();
        try {
            // Rows, cards and dialogs built later (model cards, settings rows, ...) get the comic look as they appear.
            Toolkit.getDefaultToolkit().addAWTEventListener(event -> {
                if (event.getID() == ContainerEvent.COMPONENT_ADDED) {
                    apply(((ContainerEvent) event).getChild());
                }
            }, AWTEvent.CONTAINER_EVENT_MASK);
        } catch (SecurityException ignored) {
            // Without the listener, components added later keep the plain comic delegates of the theme.
        }
    }

    /** Refresh the tree after a font or theme change, then put the comic controls back. */
    public static void refresh(Component root) {
        SwingUtilities.updateComponentTreeUI(root);
        apply(root);
    }

    /** Give every control under {@code root} the askai comic look. Safe to call more than once. */
    public static void apply(Component root) {
        if (root instanceof JComponent && Boolean.TRUE.equals(((JComponent) root).getClientProperty(SKIP))) {
            return;
        }
        style(root);
        if (root instanceof JSpinner || root instanceof JComboBox) {
            return; // their editors are styled by the askai installers
        }
        if (root instanceof Container) {
            for (Component child : ((Container) root).getComponents()) {
                apply(child);
            }
        }
    }

    private static void style(Component component) {
        if (component instanceof JCheckBox) {
            ComicFormControls.install((JCheckBox) component);
        } else if (component instanceof JToggleButton && !(component instanceof JRadioButton)) {
            ComicFormControls.install((JToggleButton) component);
        } else if (component instanceof JSlider) {
            ComicFormControls.install((JSlider) component);
        } else if (component instanceof JSpinner) {
            ComicFormControls.install((JSpinner) component);
        } else if (component instanceof JComboBox) {
            if (!(((JComboBox<?>) component).getUI() instanceof ComicComboBoxUI)) {
                ComicComboBoxUI.install((JComboBox<?>) component);
            }
        } else if (component instanceof JTextField && !(component instanceof JPasswordField)
            && !(component instanceof JFormattedTextField)) {
            JTextField field = (JTextField) component;
            // Read-only value fields (dashboard values) stay plain text; only real inputs get the comic field.
            if (field.isEditable() && !(field.getUI() instanceof ComicTextFieldUI)) {
                field.setUI(new ComicTextFieldUI());
            }
        } else if (component instanceof JTable) {
            ComicTableSupport.style((JTable) component);
        } else if (component instanceof JList) {
            if (!Boolean.TRUE.equals(((JList<?>) component).getClientProperty(SKIP))) {
                ComicListSupport.style((JList<?>) component);
            }
        } else if (component instanceof JProgressBar) {
            JProgressBar bar = (JProgressBar) component;
            if (!(bar.getUI() instanceof ComicProgressBarUI)) {
                bar.setUI(new ComicProgressBarUI());
            }
        } else if (component instanceof JScrollBar) {
            ComicScrollBarUI.install((JScrollBar) component);
        } else if (component instanceof JScrollPane) {
            JScrollPane scroll = (JScrollPane) component;
            scroll.getViewport().setBackground(Theme.background());
        }
    }
}
