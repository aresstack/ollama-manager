package com.aresstack.comiccontrols.control;

import com.aresstack.comiccontrols.theme.ThemeColor;
import com.aresstack.comiccontrols.paint.ComposerIcons;
import com.aresstack.comiccontrols.theme.ResearchUiMetrics;
import com.aresstack.comiccontrols.theme.AskAiTheme;
import com.aresstack.comiccontrols.theme.ResearchUiTypography;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.text.JTextComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicComboPopup;
import javax.swing.plaf.basic.ComboPopup;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * The comic look for a plain {@link JComboBox}: a rounded white surface with a light border, the chevron of
 * {@link ComposerIcons#chevronDown()}, an accent border on hover and focus, muted when disabled, and a rounded popup
 * with comic scroll bars. Keeps the combo box model, keyboard handling and listeners untouched, so callers stay
 * plain Swing; colors only from the {@link AskAiTheme} tokens.
 */
public final class ComicComboBoxUI extends BasicComboBoxUI {

    private static final int HEIGHT = 28;
    private static final int ARROW_WIDTH = 26;
    private static final int TEXT_INSET = 10;
    private static final String EDITOR_KEY = "comic.combo.editor";

    private boolean hovered;

    /** Give the combo box the comic look; returns it for chaining. */
    public static <T> JComboBox<T> install(JComboBox<T> combo) {
        combo.setUI(new ComicComboBoxUI());
        combo.setOpaque(false);
        combo.setFont(ResearchUiTypography.regular(12.5f));
        combo.setForeground(token("research.LIGHT_CONTROL_TEXT"));
        combo.setBackground(token("research.LIGHT_CONTROL_BG"));
        combo.setBorder(BorderFactory.createEmptyBorder());
        combo.setRenderer(new Renderer());
        return combo;
    }

    /** Hover tracking; removed again in {@link #uninstallListeners()} (a theme switch replaces the UI). */
    private final MouseAdapter hoverListener = new MouseAdapter() {
        @Override
        public void mouseEntered(MouseEvent event) {
            hovered = true;
            repaintCombo();
        }

        @Override
        public void mouseExited(MouseEvent event) {
            hovered = false;
            repaintCombo();
        }
    };

    private final FocusAdapter focusRepaintListener = new FocusAdapter() {
        @Override
        public void focusGained(FocusEvent event) {
            repaintCombo();
        }

        @Override
        public void focusLost(FocusEvent event) {
            repaintCombo();
        }
    };

    @Override
    protected void installListeners() {
        super.installListeners();
        comboBox.addMouseListener(hoverListener);
        comboBox.addFocusListener(focusRepaintListener);
    }

    @Override
    protected void uninstallListeners() {
        comboBox.removeMouseListener(hoverListener);
        comboBox.removeFocusListener(focusRepaintListener);
        super.uninstallListeners();
    }

    /**
     * An editable combo box keeps its real text field (typing, selection, caret, keyboard), only without its own
     * square background and border, so the rounded comic surface shows through and the text lines up with the
     * rendered value of a non-editable box.
     */
    @Override
    protected void configureEditor() {
        super.configureEditor();
        if (editor instanceof JTextComponent) {
            JTextComponent field = (JTextComponent) editor;
            field.setOpaque(false);
            field.setBorder(BorderFactory.createEmptyBorder(0, TEXT_INSET - 2, 0, 2));
            field.setFont(comboBox.getFont());
            field.setForeground(token("research.LIGHT_CONTROL_TEXT"));
            field.setCaretColor(token("research.LIGHT_CONTROL_TEXT"));
            field.setDisabledTextColor(token("research.LIGHT_TEXT_MUTED"));
        }
        if (editor instanceof JComponent) {
            // One listener per editor, bound to this UI: a reinstalled UI replaces the one of the old UI, whose
            // comboBox is already cleared.
            JComponent component = (JComponent) editor;
            Object previous = component.getClientProperty(EDITOR_KEY);
            if (previous instanceof FocusListener) {
                component.removeFocusListener((FocusListener) previous);
            }
            FocusListener repaint = new FocusAdapter() {
                @Override
                public void focusGained(FocusEvent event) {
                    repaintCombo();
                }

                @Override
                public void focusLost(FocusEvent event) {
                    repaintCombo();
                }
            };
            component.putClientProperty(EDITOR_KEY, repaint);
            component.addFocusListener(repaint);
        }
    }

    private void repaintCombo() {
        if (comboBox != null) {
            comboBox.repaint();
        }
    }

    @Override
    protected JButton createArrowButton() {
        final Icon chevron = ComposerIcons.chevronDown();
        JButton arrow = new JButton() {
            private static final long serialVersionUID = 1L;

            @Override
            protected void paintComponent(Graphics graphics) {
                int x = (getWidth() - chevron.getIconWidth()) / 2 - 2;
                int y = (getHeight() - chevron.getIconHeight()) / 2;
                setForeground(comboBox.isEnabled() ? (hovered || comboBox.isPopupVisible()
                        ? token("research.ACCENT_BLUE") : token("research.LIGHT_CONTROL_TEXT"))
                        : token("research.LIGHT_TEXT_MUTED"));
                chevron.paintIcon(this, graphics, x, y);
            }
        };
        arrow.setName("ComboBox.arrowButton");
        arrow.setOpaque(false);
        arrow.setContentAreaFilled(false);
        arrow.setBorderPainted(false);
        arrow.setFocusPainted(false);
        arrow.setFocusable(false);
        arrow.setBorder(BorderFactory.createEmptyBorder());
        arrow.setPreferredSize(new Dimension(ARROW_WIDTH, HEIGHT));
        return arrow;
    }

    @Override
    protected ComboPopup createPopup() {
        return new BasicComboPopup(comboBox) {
            private static final long serialVersionUID = 1L;

            @Override
            protected void configurePopup() {
                super.configurePopup();
                setBorder(BorderFactory.createLineBorder(token("research.LIGHT_CONTROL_BORDER"), 1, true));
                setBackground(token("research.LIGHT_CONTROL_BG"));
            }

            @Override
            protected JScrollPane createScroller() {
                JScrollPane scroller = super.createScroller();
                scroller.setBorder(BorderFactory.createEmptyBorder());
                ComicScrollBarUI.install(scroller.getVerticalScrollBar());
                return scroller;
            }
        };
    }

    @Override
    public void paint(Graphics graphics, JComponent component) {
        Graphics2D g2 = (Graphics2D) graphics.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = component.getWidth();
            int h = component.getHeight();
            int arc = ResearchUiMetrics.RADIUS_CONTROL;
            g2.setColor(token("research.LIGHT_CONTROL_BG"));
            g2.fillRoundRect(0, 0, w - 1, h - 1, arc, arc);
            boolean editing = comboBox.isEditable() && editor != null && editor.isFocusOwner();
            boolean active = component.isEnabled() && (hovered || editing || comboBox.hasFocus()
                    || comboBox.isPopupVisible());
            g2.setColor(active ? token("research.ACCENT_BLUE") : component.isEnabled()
                    ? token("research.LIGHT_CONTROL_BORDER") : token("research.LIGHT_TEXT_MUTED"));
            g2.setStroke(new BasicStroke(active ? 1.6f : 1.2f));
            g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);
        } finally {
            g2.dispose();
        }
        if (!comboBox.isEditable()) {
            paintCurrentValue(graphics, rectangleForCurrentValue(), false);
        }
    }

    @Override
    public void paintCurrentValueBackground(Graphics graphics, Rectangle bounds, boolean hasFocus) {
        // The rounded surface painted in paint() is the background.
    }

    @Override
    public void paintCurrentValue(Graphics graphics, Rectangle bounds, boolean hasFocus) {
        ListCellRenderer<Object> renderer = comboBox.getRenderer();
        Object value = comboBox.getSelectedItem();
        Component cell = renderer.getListCellRendererComponent(listBox, value, -1, false, false);
        cell.setFont(comboBox.getFont());
        cell.setForeground(comboBox.isEnabled() ? token("research.LIGHT_CONTROL_TEXT")
                : token("research.LIGHT_TEXT_MUTED"));
        if (cell instanceof JComponent) {
            ((JComponent) cell).setOpaque(false);
        }
        currentValuePane.paintComponent(graphics, cell, comboBox, bounds.x, bounds.y, bounds.width, bounds.height,
                false);
    }

    @Override
    protected Insets getInsets() {
        return new Insets(2, 2, 2, 2);
    }

    @Override
    public Dimension getMinimumSize(JComponent component) {
        Dimension size = super.getMinimumSize(component);
        return new Dimension(size.width, Math.max(size.height, HEIGHT));
    }

    @Override
    public Dimension getPreferredSize(JComponent component) {
        Dimension size = super.getPreferredSize(component);
        return new Dimension(size.width, Math.max(size.height, HEIGHT));
    }

    /** Rows with comic padding; the selected row in the accent color, long names cut with an ellipsis. */
    private static final class Renderer extends DefaultListCellRenderer {
        private static final long serialVersionUID = 1L;

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected,
                                                      boolean focused) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, selected, false);
            label.putClientProperty("html.disable", Boolean.TRUE);
            label.setBorder(BorderFactory.createEmptyBorder(4, TEXT_INSET, 4, TEXT_INSET));
            label.setFont(ResearchUiTypography.regular(12.5f));
            label.setText(value == null ? "" : value.toString());
            label.setToolTipText(value == null ? null : value.toString());
            Color background = selected ? token("research.ACCENT_BLUE") : token("research.LIGHT_CONTROL_BG");
            label.setBackground(background);
            label.setForeground(selected ? token("research.LIGHT_CONTROL_BG") : token("research.LIGHT_CONTROL_TEXT"));
            label.setOpaque(index >= 0);
            return label;
        }
    }

    /** The living color of a theme token (full key). */
    private static Color token(String key) {
        return ThemeColor.of(key);
    }
}
