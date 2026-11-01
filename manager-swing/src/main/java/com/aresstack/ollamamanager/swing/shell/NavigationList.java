package com.aresstack.ollamamanager.swing.shell;

import com.aresstack.comiccontrols.control.ComicScrollPane;
import com.aresstack.comiccontrols.theme.AskAiTheme;
import com.aresstack.comiccontrols.theme.ComicPalette;
import com.aresstack.comiccontrols.theme.ResearchUiMetrics;
import com.aresstack.comiccontrols.theme.ResearchUiPainter;
import com.aresstack.comiccontrols.theme.ResearchUiPalette;
import com.aresstack.comiccontrols.theme.ResearchUiTypography;
import com.aresstack.comiccontrols.theme.ThemeColor;
import com.aresstack.ollamamanager.swing.component.ComicStyle;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.BorderFactory;
import javax.swing.ListCellRenderer;
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
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
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
    /** Pinned to the bottom edge in this order; Jobs & logs is always the last entry. */
    private static final List<Section> BOTTOM_ORDER =
        java.util.Arrays.asList(Section.PROFILES, Section.SETUP, Section.JOBS);
    private static final Set<Section> BOTTOM = EnumSet.copyOf(BOTTOM_ORDER);

    private final JList<Section> top;
    private final JList<Section> bottom;
    private final JScrollPane scroll;
    private final JLabel heading = new JLabel("OLLAMA MANAGER");
    private final List<ListSelectionListener> listeners = new ArrayList<ListSelectionListener>();
    private boolean syncing;

    NavigationList() {
        super(new BorderLayout());
        List<Section> topSections = new ArrayList<Section>();
        List<Section> bottomSections = new ArrayList<Section>(BOTTOM_ORDER);
        for (Section section : Section.values()) {
            if (!BOTTOM.contains(section)) {
                topSections.add(section);
            }
        }
        top = group(topSections, "Navigation");
        bottom = group(bottomSections, "Support views");
        JPanel column = new JPanel(new BorderLayout());
        column.setOpaque(false);
        column.add(heading, BorderLayout.NORTH);
        column.add(top, BorderLayout.CENTER);
        scroll = new ComicScrollPane(column, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
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
        list.setCellRenderer(new Row());
        list.setFixedCellHeight(ResearchUiMetrics.CHAT_ROW_HEIGHT);
        trackHover(list);
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
        // The askai drawer: comic surface, the page rows in the chat-list look (see Row).
        Color background = AskAiTheme.active().comicPalette().getSurface();
        setBackground(background);
        heading.setFont(ResearchUiTypography.semiBold(11f));
        heading.setForeground(ResearchUiPalette.LIGHT_TEXT_MUTED);
        heading.setBorder(BorderFactory.createEmptyBorder(14, 14, 4, 8));
        for (JList<Section> list : java.util.Arrays.asList(top, bottom)) {
            list.setOpaque(false);
            list.setBackground(background);
            list.putClientProperty(ComicStyle.SKIP, Boolean.TRUE);
        }
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        top.setBorder(BorderFactory.createEmptyBorder(0, 4, 4, 4));
        bottom.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0,
                ThemeColor.mix(AskAiTheme.active().comicPalette().getInk(), background, 0.8f)),
            BorderFactory.createEmptyBorder(4, 4, 4, 4)));
    }

    /** Index under the mouse per list, for the drawer's hover wash. */
    private static void trackHover(final JList<Section> list) {
        java.awt.event.MouseAdapter mouse = new java.awt.event.MouseAdapter() {
            @Override
            public void mouseMoved(java.awt.event.MouseEvent event) {
                int index = list.locationToIndex(event.getPoint());
                if (index >= 0 && !list.getCellBounds(index, index).contains(event.getPoint())) {
                    index = -1;
                }
                set(index);
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent event) {
                set(-1);
            }

            private void set(int index) {
                Object old = list.getClientProperty(HOVER);
                if (old == null ? index != -1 : (Integer) old != index) {
                    list.putClientProperty(HOVER, index);
                    list.repaint();
                }
            }
        };
        list.addMouseListener(mouse);
        list.addMouseMotionListener(mouse);
    }

    private static final String HOVER = "ollamamanager.navigation.hover";

    /**
     * One page row painted like askai's drawer rows ({@code chat.ChatHistoryRow}): transparent at rest, a light
     * blue wash on hover, the stronger wash plus the 3px navigation-blue accent when selected; title in
     * semi-bold 13, the shortcut as quiet second line.
     */
    private static final class Row extends JComponent implements ListCellRenderer<Section> {
        private static final long serialVersionUID = 1L;
        private static final Color HOVER_WASH =
            ThemeColor.mix(ResearchUiPalette.ACCENT_BLUE, ThemeColor.of("comic.fieldBg"), 0.94f);
        private static final Color SELECTED_WASH =
            ThemeColor.mix(ResearchUiPalette.ACCENT_BLUE, ThemeColor.of("comic.fieldBg"), 0.88f);

        private String title = "";
        private String meta = "";
        private boolean selected;
        private boolean hovered;

        Row() {
            setOpaque(false);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends Section> list, Section value, int index,
                                                      boolean isSelected, boolean focused) {
            title = value.title();
            meta = shortcutText(value);
            selected = isSelected;
            Object hover = list.getClientProperty(HOVER);
            hovered = hover != null && (Integer) hover == index;
            setToolTipText(value.description());
            return this;
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g2 = ResearchUiPainter.prepare(graphics);
            try {
                int radius = ResearchUiMetrics.CHAT_ROW_RADIUS;
                if (selected) {
                    ResearchUiPainter.fillRound(g2, 0, 0, getWidth(), getHeight(), radius, SELECTED_WASH);
                    g2.setColor(ResearchUiPalette.ACCENT_BLUE);
                    g2.fillRoundRect(0, 6, ResearchUiMetrics.CHAT_ROW_ACCENT_WIDTH, getHeight() - 12, 2, 2);
                } else if (hovered) {
                    ResearchUiPainter.fillRound(g2, 0, 0, getWidth(), getHeight(), radius, HOVER_WASH);
                }
                int textX = ResearchUiMetrics.CHAT_ROW_PADDING_H + 4;
                g2.setFont(ResearchUiTypography.semiBold(13f));
                FontMetrics titleMetrics = g2.getFontMetrics();
                g2.setColor(ComicPalette.defaultPalette().getInk());
                g2.drawString(title, textX, 15 + titleMetrics.getAscent() / 2 - 1);
                g2.setFont(ResearchUiTypography.regular(11f));
                g2.setColor(ResearchUiPalette.LIGHT_TEXT_MUTED);
                g2.drawString(meta, textX, getHeight() - 9);
            } finally {
                g2.dispose();
            }
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(10, ResearchUiMetrics.CHAT_ROW_HEIGHT);
        }
    }
}
