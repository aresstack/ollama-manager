package com.aresstack.ollamamanager.swing.shell;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.ListSelectionModel;
import java.awt.Component;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;

/**
 * Sidebar with all sections. Arrow keys move the selection; tooltips show the shortcut.
 */
final class NavigationList extends JList<Section> {

    private static final long serialVersionUID = 1L;

    NavigationList() {
        super(Section.values());
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        setCellRenderer(new Renderer());
        getAccessibleContext().setAccessibleName("Navigation");
    }

    static String shortcutText(Section section) {
        int mask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();
        return KeyEvent.getKeyModifiersText(mask) + "+" + KeyEvent.getKeyText(section.shortcutKey());
    }

    private static final class Renderer extends DefaultListCellRenderer {
        private static final long serialVersionUID = 1L;

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected,
                                                      boolean focused) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, selected, focused);
            Section section = (Section) value;
            label.setBorder(javax.swing.BorderFactory.createEmptyBorder(6, 12, 6, 12));
            label.setToolTipText(section.description() + " (" + shortcutText(section) + ")");
            return label;
        }
    }
}
