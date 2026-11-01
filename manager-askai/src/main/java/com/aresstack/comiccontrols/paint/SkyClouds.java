package com.aresstack.comiccontrols.paint;

import com.aresstack.comiccontrols.theme.AskAiTheme;

import javax.swing.BoundedRangeModel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.Stroke;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;

/**
 * The shared mechanics of the cloud skies (extracted from the research plugin's out-of-scope sky), so every sky
 * uses the same drawing: cloud silhouette, cloud body with border, text baseline, the translucent vertical gradient
 * (nearly opaque at the top, fully transparent at the bottom, no hard edge) and the mouse-wheel priority of an
 * inner scroll bar. Colors come from the active {@link AskAiTheme}.
 */
public final class SkyClouds {

    private SkyClouds() {
    }

    /** The soft cloud silhouette: a rounded body with a slightly irregular top edge, no emoji. */
    public static Area cloudShape(int width, int height) {
        float bodyTop = 7f;
        Area cloud = new Area(new RoundRectangle2D.Float(0.6f, bodyTop, width - 1.2f,
                height - bodyTop - 0.6f, height - bodyTop, height - bodyTop));
        if (width > 46) {
            cloud.add(new Area(new Ellipse2D.Float(width * 0.16f, 2.5f, 15f, 15f)));
            cloud.add(new Area(new Ellipse2D.Float(width * 0.42f, 0.8f, 18f, 18f)));
            cloud.add(new Area(new Ellipse2D.Float(width * 0.66f, 3.2f, 13f, 13f)));
        }
        return cloud;
    }

    /** Cloud body and border (lighter on hover), like the clouds of the sky. */
    public static void paintCloudBase(Graphics2D g2, int width, int height, boolean hovered) {
        paintCloudBase(g2, width, height, hovered, null);
    }

    /**
     * Like {@link #paintCloudBase(Graphics2D, int, int, boolean)}, with an own border stroke (e.g. dashed for a
     * generated label); {@code null}: the standard stroke.
     */
    public static void paintCloudBase(Graphics2D g2, int width, int height, boolean hovered, Stroke border) {
        Area shape = cloudShape(width, height);
        g2.setColor(hovered ? research("CLOUD_HOVER_SURFACE") : research("CLOUD_SURFACE"));
        g2.fill(shape);
        g2.setColor(hovered ? research("CLOUD_HOVER_BORDER") : research("CLOUD_BORDER"));
        g2.setStroke(border == null ? new BasicStroke(1.2f) : border);
        g2.draw(shape);
    }

    /** Text baseline inside the cloud body (the calm lower part of the silhouette). */
    public static int cloudTextY(FontMetrics metrics, int height) {
        float bodyTop = 7f;
        return Math.round(bodyTop + (height - bodyTop - metrics.getHeight()) / 2f)
                + metrics.getAscent();
    }

    /**
     * The sky behind the clouds: air instead of a surface — strongly opaque at the top, translucent in the middle,
     * fully transparent at the bottom; plus two very faint background clouds.
     *
     * @param contentBottom lower edge of the interactive zone; the fade tail hangs below it
     * @param skyHeight     painted height (zone plus tail)
     */
    public static void paintSky(Graphics2D g2, int width, int contentBottom, int skyHeight) {
        if (skyHeight <= 0 || contentBottom <= 0) {
            return;
        }
        LinearGradientPaint sky = new LinearGradientPaint(0f, 0f, 0f, skyHeight,
                skyFractions(contentBottom, skyHeight),
                skyColors(research("SKY_TOP"), research("SKY_MID")));
        g2.setPaint(sky);
        g2.fillRect(0, 0, width, skyHeight);

        g2.setColor(ghostCloudColor());
        g2.fill(new Ellipse2D.Float(width * 0.08f, contentBottom * 0.15f, width * 0.28f,
                Math.max(18f, contentBottom * 0.4f)));
        g2.fill(new Ellipse2D.Float(width * 0.58f, contentBottom * 0.45f, width * 0.34f,
                Math.max(16f, contentBottom * 0.35f)));
    }

    /**
     * The gradient stops of the sky: top, 55 % of the content zone, the content edge, the bottom of the tail.
     * The content stop is clamped to 0.1..0.85.
     */
    public static float[] skyFractions(int contentBottom, int skyHeight) {
        float contentStop = Math.max(0.1f, Math.min(0.85f, contentBottom / (float) skyHeight));
        return new float[]{0f, contentStop * 0.55f, contentStop, 1f};
    }

    /**
     * The sunset gradient colors (pure): {@code top} at alpha 242, then {@code mid} at 205, 105 and 0. With
     * {@code mid == top} (classic theme) this is exactly the historic single-tone sky.
     */
    public static Color[] skyColors(Color top, Color mid) {
        return new Color[]{
                new Color(top.getRed(), top.getGreen(), top.getBlue(), 242),
                new Color(mid.getRed(), mid.getGreen(), mid.getBlue(), 205),
                new Color(mid.getRed(), mid.getGreen(), mid.getBlue(), 105),
                new Color(mid.getRed(), mid.getGreen(), mid.getBlue(), 0)};
    }

    /** The color of the two faint background clouds: the light plate at alpha 30 (white in classic). */
    public static Color ghostCloudColor() {
        Color ghost = AskAiTheme.active().buttonPlate();
        return new Color(ghost.getRed(), ghost.getGreen(), ghost.getBlue(), 30);
    }

    /**
     * Mouse-wheel priority: if the inner scroll bar has something to scroll, it scrolls and consumes the event;
     * otherwise the event goes on to {@code owner}'s parent so the content below scrolls — never both at once.
     */
    public static void installWheelPriority(final JScrollPane scroll, final Component owner) {
        scroll.setWheelScrollingEnabled(false);
        scroll.addMouseWheelListener(event -> {
            BoundedRangeModel model = scroll.getVerticalScrollBar().getModel();
            boolean scrollable = model.getExtent() > 0
                    && model.getExtent() < model.getMaximum() - model.getMinimum();
            if (scrollable) {
                int delta = (int) Math.round(event.getPreciseWheelRotation()
                        * event.getScrollAmount() * 16);
                if (delta == 0 && event.getPreciseWheelRotation() != 0) {
                    delta = event.getPreciseWheelRotation() < 0 ? -1 : 1;
                }
                model.setValue(model.getValue() + delta);
                event.consume();
            } else {
                Container parent = owner.getParent();
                if (parent != null) {
                    parent.dispatchEvent(SwingUtilities.convertMouseEvent(
                            (Component) event.getSource(), event, parent));
                }
            }
        });
    }

    private static Color research(String constantName) {
        return AskAiTheme.active().research(constantName);
    }
}
