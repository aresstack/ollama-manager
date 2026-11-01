package com.aresstack.comiccontrols.control;

import com.aresstack.comiccontrols.border.ComicFieldBorder;
import com.aresstack.comiccontrols.theme.AskAiTheme;

import javax.swing.JComponent;
import javax.swing.LookAndFeel;
import javax.swing.border.Border;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.UIResource;
import javax.swing.plaf.basic.BasicTextFieldUI;
import javax.swing.text.JTextComponent;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.geom.RoundRectangle2D;

/**
 * The comic text field delegate: a rounded {@code comic.fieldBg} plate with the {@link ComicFieldBorder}
 * contour (navigation blue while focused), ink text and caret, the yellow selection. Read-only or
 * disabled fields rest on the quieter {@code comic.fieldBgQuiet}. Caller-set colors and borders win —
 * only look-and-feel ({@link UIResource}) values are replaced.
 */
public class ComicTextFieldUI extends BasicTextFieldUI {

    private final FocusListener repaintOnFocus = new FocusAdapter() {
        @Override
        public void focusGained(FocusEvent event) {
            event.getComponent().repaint();
        }

        @Override
        public void focusLost(FocusEvent event) {
            event.getComponent().repaint();
        }
    };

    public static ComponentUI createUI(JComponent c) {
        return new ComicTextFieldUI();
    }

    @Override
    public void installUI(JComponent c) {
        super.installUI(c);
        // after installUI: BasicTextUI re-applies its own (editable/enabled) background at the end of it
        installFieldDefaults(getComponent());
    }

    @Override
    protected void installListeners() {
        super.installListeners();
        getComponent().addFocusListener(repaintOnFocus);
    }

    @Override
    protected void uninstallListeners() {
        getComponent().removeFocusListener(repaintOnFocus);
        super.uninstallListeners();
    }

    @Override
    protected void paintSafely(Graphics g) {
        paintFieldPlate(g, getComponent());
        super.paintSafely(g);
    }

    /** Shared by the text and password field delegates: theme colors, comic contour, transparent corners. */
    static void installFieldDefaults(JTextComponent field) {
        AskAiTheme theme = AskAiTheme.active();
        Color ink = theme.comicPalette().getInk();
        LookAndFeel.installProperty(field, "opaque", Boolean.FALSE);
        if (isLookAndFeelValue(field.getBackground())) {
            field.setBackground(new ColorUIResource(theme.fieldBg()));
        }
        if (isLookAndFeelValue(field.getForeground())) {
            field.setForeground(new ColorUIResource(ink));
        }
        if (isLookAndFeelValue(field.getCaretColor())) {
            field.setCaretColor(new ColorUIResource(ink));
        }
        if (isLookAndFeelValue(field.getSelectionColor())) {
            field.setSelectionColor(new ColorUIResource(theme.comicPalette().getAccentYellow()));
        }
        if (isLookAndFeelValue(field.getSelectedTextColor())) {
            field.setSelectedTextColor(new ColorUIResource(ink));
        }
        if (isLookAndFeelValue(field.getDisabledTextColor())) {
            field.setDisabledTextColor(new ColorUIResource(theme.hintText()));
        }
        Border border = field.getBorder();
        if (border == null || border instanceof UIResource) {
            field.setBorder(new ComicFieldBorder());
        }
    }

    /** The rounded field plate behind the text (the field itself is not opaque). */
    static void paintFieldPlate(Graphics g, JTextComponent field) {
        if (field.isOpaque()) {
            return; // a caller made it opaque: the look-and-feel paints its square background
        }
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(plateColor(field));
            g2.fill(new RoundRectangle2D.Float(0.5f, 0.5f, field.getWidth() - 1f, field.getHeight() - 1f,
                    ComicFieldBorder.ARC, ComicFieldBorder.ARC));
        } finally {
            g2.dispose();
        }
    }

    /** Pure: the plate color of a field in its state. */
    static Color plateColor(JTextComponent field) {
        Color background = field.getBackground();
        if (!(background instanceof UIResource)) {
            return background; // caller's explicit color
        }
        return field.isEnabled() && field.isEditable()
                ? AskAiTheme.active().fieldBg() : AskAiTheme.active().fieldBgQuiet();
    }

    private static boolean isLookAndFeelValue(Color color) {
        return color == null || color instanceof UIResource;
    }
}
