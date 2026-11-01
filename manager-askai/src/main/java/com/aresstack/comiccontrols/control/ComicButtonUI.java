package com.aresstack.comiccontrols.control;

import com.aresstack.comiccontrols.theme.AskAiTheme;
import com.aresstack.comiccontrols.theme.ComicPalette;

import javax.swing.AbstractButton;
import javax.swing.ButtonModel;
import javax.swing.JComponent;
import javax.swing.LookAndFeel;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.BorderUIResource;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.UIResource;
import javax.swing.plaf.basic.BasicButtonUI;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Container;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;

/**
 * The global button delegate of the comic design language: every plain {@link javax.swing.JButton}
 * gets the {@link ComicButton} plate (rounded, ink contour; resting plate, yellow rollover, orange
 * press, surface when disabled) without being converted to a subclass. Registered through
 * {@code UIManager.put("ButtonUI", ComicButtonUI.class.getName())} by
 * {@link com.aresstack.comiccontrols.theme.ComicTheme#install(AskAiTheme)}.
 *
 * <p>Opt-out: a button with the client property {@value #PLAIN_BUTTON_PROPERTY} = {@code TRUE}
 * paints exactly like {@link BasicButtonUI}. {@link ComicButton} sets it, because it paints its
 * own plate. Buttons with {@code contentAreaFilled == false} (icon/link buttons) get no plate.</p>
 */
public class ComicButtonUI extends BasicButtonUI {

    /** Client property: {@code Boolean.TRUE} → no comic plate, plain basic painting. */
    public static final String PLAIN_BUTTON_PROPERTY = "askai.plainButton";

    private static final int ARC = 10;
    private static final float OUTLINE_WIDTH = 1.6f;

    private final ComicPalette palette;
    private final Color buttonPlate;

    public ComicButtonUI(AskAiTheme theme) {
        if (theme == null) {
            throw new IllegalArgumentException("theme must not be null");
        }
        this.palette = theme.comicPalette();
        this.buttonPlate = theme.buttonPlate();
    }

    public static ComponentUI createUI(JComponent c) {
        return new ComicButtonUI(AskAiTheme.active());
    }

    /**
     * THE plate color logic shared with {@link ComicButton}: disabled → surface, armed+pressed →
     * orange (red when critical), rollover → yellow (red when critical), else the resting plate.
     */
    public static Color plateFill(ComicPalette palette, Color restingPlate, boolean enabled,
                                  boolean armedAndPressed, boolean rollover, boolean critical) {
        if (!enabled) {
            return palette.getSurface();
        }
        if (armedAndPressed) {
            return critical ? palette.getAccentRed() : palette.getAccentOrange();
        }
        if (rollover) {
            return critical ? palette.getAccentRed() : palette.getAccentYellow();
        }
        return restingPlate;
    }

    /** Whether the button opted out of the comic plate. */
    public static boolean isPlain(JComponent c) {
        return Boolean.TRUE.equals(c.getClientProperty(PLAIN_BUTTON_PROPERTY));
    }

    @Override
    protected void installDefaults(AbstractButton b) {
        super.installDefaults(b);
        LookAndFeel.installProperty(b, "rolloverEnabled", Boolean.TRUE);
        Border border = b.getBorder();
        if (border instanceof UIResource) {
            b.setBorder(flattenedBorder(border, b));
        }
    }

    /**
     * The plate carries the contour, so the look-and-feel's bevel is dropped — but its insets are
     * kept, and a compound border's INSIDE part (Metal: the margin border tracking
     * {@code setMargin}) stays live. Button sizes and margins therefore behave as before.
     */
    static Border flattenedBorder(Border lafBorder, AbstractButton b) {
        if (lafBorder instanceof CompoundBorder) {
            CompoundBorder compound = (CompoundBorder) lafBorder;
            Border outside = compound.getOutsideBorder();
            Insets outer = outside == null ? new Insets(0, 0, 0, 0) : outside.getBorderInsets(b);
            return new BorderUIResource.CompoundBorderUIResource(
                    new EmptyBorder(outer), compound.getInsideBorder());
        }
        return new BorderUIResource.EmptyBorderUIResource(lafBorder.getBorderInsets(b));
    }

    @Override
    public void update(Graphics g, JComponent c) {
        AbstractButton b = (AbstractButton) c;
        if (isPlain(c) || !b.isContentAreaFilled()) {
            super.update(g, c);
            return;
        }
        ButtonModel model = b.getModel();
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            if (c.isOpaque()) {
                // an opaque component must paint every pixel — the corners outside the plate
                // get the surrounding background
                Container parent = c.getParent();
                g2.setColor(parent != null ? parent.getBackground() : c.getBackground());
                g2.fillRect(0, 0, c.getWidth(), c.getHeight());
            }
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            RoundRectangle2D plate = new RoundRectangle2D.Float(
                    1f, 1f, c.getWidth() - 2f, c.getHeight() - 2f, ARC, ARC);
            g2.setColor(plateFill(palette, buttonPlate, b.isEnabled(),
                    model.isArmed() && model.isPressed(), model.isRollover(), false));
            g2.fill(plate);
            g2.setColor(b.isEnabled() ? palette.getInk() : palette.getInk().brighter().brighter());
            g2.setStroke(new BasicStroke(OUTLINE_WIDTH));
            g2.draw(plate);
        } finally {
            g2.dispose();
        }
        paint(g, c);
    }

    @Override
    protected void paintFocus(Graphics g, AbstractButton b, Rectangle viewRect, Rectangle textRect,
                              Rectangle iconRect) {
        if (isPlain(b) || !b.isContentAreaFilled()) {
            super.paintFocus(g, b, viewRect, textRect, iconRect);
            return;
        }
        // keyboard focus stays visible: a thin inner ring inside the plate
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(palette.getNavigationBlue());
            g2.draw(new RoundRectangle2D.Float(3.5f, 3.5f, b.getWidth() - 7f, b.getHeight() - 7f,
                    ARC - 4, ARC - 4));
        } finally {
            g2.dispose();
        }
    }

    @Override
    protected void paintButtonPressed(Graphics g, AbstractButton b) {
        if (isPlain(b) || !b.isContentAreaFilled()) {
            super.paintButtonPressed(g, b);
        }
        // comic mode: the pressed state is already part of the plate
    }
}
