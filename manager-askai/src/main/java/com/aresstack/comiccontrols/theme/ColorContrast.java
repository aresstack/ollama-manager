package com.aresstack.comiccontrols.theme;

import java.awt.Color;

/**
 * Contrast of two colors per WCAG 2 (relative luminance, ratio 1:1 to 21:1). Used to warn about hard-to-read
 * bubble colors and to keep accent colors legible on every surface; the theme tests check text/surface pairs with it.
 */
public final class ColorContrast {

    /** Below this, text is barely readable (WCAG minimum for large text and controls). */
    public static final double MINIMUM_READABLE = 3.0;

    /**
     * Below this, colored text on the surface is practically illegible (e.g. blue on blue); accent colors only give
     * way to the text color then, so familiar accents stay on the standard bubbles.
     */
    public static final double MINIMUM_LEGIBLE_ACCENT = 2.0;

    private static final double LINEAR_THRESHOLD = 0.03928;
    private static final double LINEAR_DIVISOR = 12.92;
    private static final double GAMMA_OFFSET = 0.055;
    private static final double GAMMA_DIVISOR = 1.055;
    private static final double GAMMA = 2.4;
    private static final double RED_WEIGHT = 0.2126;
    private static final double GREEN_WEIGHT = 0.7152;
    private static final double BLUE_WEIGHT = 0.0722;
    private static final double FLARE = 0.05;
    private static final double CHANNEL_MAX = 255.0;

    private ColorContrast() {
    }

    /** The contrast ratio (1 = same luminance, 21 = black on white); the order does not matter. */
    public static double ratio(Color first, Color second) {
        double a = luminance(first);
        double b = luminance(second);
        return (Math.max(a, b) + FLARE) / (Math.min(a, b) + FLARE);
    }

    /** Whether text in {@code foreground} on {@code background} reaches at least {@link #MINIMUM_READABLE}. */
    public static boolean isReadable(Color foreground, Color background) {
        return ratio(foreground, background) >= MINIMUM_READABLE;
    }

    /** Relative luminance (0 black … 1 white). */
    public static double luminance(Color color) {
        return RED_WEIGHT * channel(color.getRed()) + GREEN_WEIGHT * channel(color.getGreen())
                + BLUE_WEIGHT * channel(color.getBlue());
    }

    private static double channel(int value) {
        double c = value / CHANNEL_MAX;
        return c <= LINEAR_THRESHOLD ? c / LINEAR_DIVISOR : Math.pow((c + GAMMA_OFFSET) / GAMMA_DIVISOR, GAMMA);
    }
}
