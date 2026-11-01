package com.aresstack.ollamamanager.swing.shell;

import com.aresstack.ollamamanager.swing.component.Spacing;
import com.aresstack.ollamamanager.swing.component.Theme;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.ListSelectionModel;
import java.awt.Component;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;

/**
 * Sidebar with all sections. Arrow keys move the selection; each entry shows its shortcut and a tooltip with the
 * section description. Row height and padding follow the font scale.
 */
final class NavigationList extends JList<Section> {

    private static final long serialVersionUID = 1L;

    NavigationList() {
        super(Section.values());
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        setCellRenderer(new Renderer());
        getAccessibleContext().setAccessibleName("Navigation");
        applyLook();
    }

    static String shortcutText(Section section) {
        int mask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();
        return KeyEvent.getKeyModifiersText(mask) + "+" + KeyEvent.getKeyText(section.shortcutKey());
    }

    @Override
    public void updateUI() {
        super.updateUI();
        applyLook();
    }

    private void applyLook() {
        setBorder(BorderFactory.createEmptyBorder(Spacing.GAP, 0, Spacing.GAP, 0));
        setBackground(Theme.blend(Theme.background(), Theme.foreground(), Theme.isDark() ? 0.04f : 0.03f));
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

        private static String hex(java.awt.Color color) {
            return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
        }
    }
}
