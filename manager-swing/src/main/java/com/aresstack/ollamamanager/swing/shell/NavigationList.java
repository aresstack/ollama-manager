package com.aresstack.ollamamanager.swing.shell;

import com.aresstack.ollamamanager.swing.component.Spacing;
import com.aresstack.ollamamanager.swing.component.Theme;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.HeadlessException;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Sidebar with all sections. The daily destinations sit at the top; support views ({@link Section#JOBS}) stick to
 * the bottom edge with free space above them. Arrow keys move the selection across both groups; each entry shows
 * its shortcut and a tooltip with the section description. Row height and padding follow the font scale.
 */
final class NavigationList extends JPanel {

    private static final long serialVersionUID = 1L;
    private static final Set<Section> BOTTOM = EnumSet.of(Section.JOBS);

    private final JList<Section> top;
    private final JList<Section> bottom;
    private final JScrollPane scroll;
    private final List<ListSelectionListener> listeners = new ArrayList<ListSelectionListener>();
    private boolean syncing;

    NavigationList() {
        super(new BorderLayout());
        List<Section> topSections = new ArrayList<Section>();
        List<Section> bottomSections = new ArrayList<Section>();
        for (Section section : Section.values()) {
            (BOTTOM.contains(section) ? bottomSections : topSections).add(section);
        }
        top = group(topSections, "Navigation");
        bottom = group(bottomSections, "Support views");
        scroll = new JScrollPane(top);
        scroll.setBorder(null);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        add(scroll, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);
        linkArrowKeys(top, bottom, KeyEvent.VK_DOWN, "selectNextRow");
        linkArrowKeys(bottom, top, KeyEvent.VK_UP, "selectPreviousRow");
        applyLook();
    }

    private JList<Section> group(List<Section> sections, String accessibleName) {
        JList<Section> list = new JList<Section>(sections.toArray(new Section[0]));
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new Renderer());
        list.getAccessibleContext().setAccessibleName(accessibleName);
        list.addListSelectionListener(event -> {
            if (syncing || event.getValueIsAdjusting()) {
                return;
            }
            if (list.getSelectedValue() != null) {
                // One selection across both groups: clear the other list without firing twice.
                syncing = true;
                try {
                    (list == top ? bottom : top).clearSelection();
                } finally {
                    syncing = false;
                }
            }
            if (getSelectedValue() != null) {
                for (ListSelectionListener listener : listeners) {
                    listener.valueChanged(new ListSelectionEvent(this, 0, Section.values().length - 1, false));
                }
            }
        });
        return list;
    }

    /** Let the arrow key at the edge of one group continue into the other group. */
    private static void linkArrowKeys(JList<Section> from, JList<Section> to, int key, String defaultAction) {
        InputMap inputs = from.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap actions = from.getActionMap();
        KeyStroke stroke = KeyStroke.getKeyStroke(key, 0);
        final javax.swing.Action fallback = actions.get(inputs.get(stroke) == null ? defaultAction : inputs.get(stroke));
        String name = "navigation." + defaultAction;
        inputs.put(stroke, name);
        actions.put(name, new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent event) {
                boolean atEdge = key == KeyEvent.VK_DOWN
                    ? from.getSelectedIndex() == from.getModel().getSize() - 1
                    : from.getSelectedIndex() == 0;
                if (atEdge && to.getModel().getSize() > 0) {
                    to.setSelectedIndex(key == KeyEvent.VK_DOWN ? 0 : to.getModel().getSize() - 1);
                    to.requestFocusInWindow();
                } else if (fallback != null) {
                    fallback.actionPerformed(event);
                }
            }
        });
    }

    void addListSelectionListener(ListSelectionListener listener) {
        listeners.add(listener);
    }

    Section getSelectedValue() {
        return top.getSelectedValue() != null ? top.getSelectedValue() : bottom.getSelectedValue();
    }

    void setSelectedValue(Section section, boolean scrollTo) {
        JList<Section> list = BOTTOM.contains(section) ? bottom : top;
        if (list.getSelectedValue() != section) {
            list.setSelectedValue(section, scrollTo);
        }
    }

    @Override
    public boolean requestFocusInWindow() {
        return (getSelectedValue() != null && BOTTOM.contains(getSelectedValue()) ? bottom : top)
            .requestFocusInWindow();
    }

    static String shortcutText(Section section) {
        int mask;
        try {
            mask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();
        } catch (HeadlessException e) {
            // Headless tests and previews: assume the usual Ctrl modifier.
            mask = java.awt.event.InputEvent.CTRL_MASK;
        }
        return KeyEvent.getKeyModifiersText(mask) + "+" + KeyEvent.getKeyText(section.shortcutKey());
    }

    @Override
    public void updateUI() {
        super.updateUI();
        if (top != null) {
            applyLook();
        }
    }

    private void applyLook() {
        Color background = Theme.blend(Theme.background(), Theme.foreground(), Theme.isDark() ? 0.04f : 0.03f);
        setBackground(background);
        top.setBackground(background);
        bottom.setBackground(background);
        scroll.getViewport().setBackground(background);
        top.setBorder(BorderFactory.createEmptyBorder(Spacing.GAP, 0, Spacing.GAP, 0));
        bottom.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.lineColor()),
            BorderFactory.createEmptyBorder(Spacing.GAP, 0, Spacing.GAP, 0)));
    }

    private static final class Renderer extends DefaultListCellRenderer {
        private static final long serialVersionUID = 1L;

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected,
                                                      boolean focused) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, selected, focused);
            Section section = (Section) value;
            label.setText("<html><b>" + section.title() + "</b>&nbsp;&nbsp;<span style='color:"
                + hex(selected ? label.getForeground() : Theme.mutedForeground()) + "'>" + shortcutText(section)
                + "</span></html>");
            label.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, selected ? 3 : 0, 0, 0, Theme.color(Theme.Tone.INFO)),
                BorderFactory.createEmptyBorder(Spacing.GAP, selected ? Spacing.MARGIN - 3 : Spacing.MARGIN,
                    Spacing.GAP, Spacing.GAP)));
            label.setToolTipText(section.description());
            if (!selected) {
                label.setBackground(list.getBackground());
            }
            return label;
        }

        private static String hex(Color color) {
            return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
        }
    }
}
