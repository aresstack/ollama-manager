package com.aresstack.comiccontrols.control;

import javax.swing.JComponent;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicPasswordFieldUI;
import java.awt.Graphics;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;

/** The password variant of {@link ComicTextFieldUI}: same plate, contour and colors, echo characters kept. */
public class ComicPasswordFieldUI extends BasicPasswordFieldUI {

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
        return new ComicPasswordFieldUI();
    }

    @Override
    public void installUI(JComponent c) {
        super.installUI(c);
        // after installUI: BasicTextUI re-applies its own (editable/enabled) background at the end of it
        ComicTextFieldUI.installFieldDefaults(getComponent());
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
        ComicTextFieldUI.paintFieldPlate(g, getComponent());
        super.paintSafely(g);
    }
}
