package com.aresstack.ollamamanager.swing.component;

import com.aresstack.comiccontrols.theme.AskAiTheme;
import com.aresstack.comiccontrols.theme.ComicPalette;

import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Font;

/**
 * Semantic colours and fonts derived from the active look-and-feel, so widgets look right on light and dark
 * themes and follow the global font scale. Nothing here is hard-wired to a pixel size.
 */
public final class Theme {

    /** Meaning of a value or state; widgets map it to a colour. */
    public enum Tone { NEUTRAL, INFO, SUCCESS, WARNING, ERROR }

    private Theme() {
    }

    /** True when the look-and-feel paints on a dark background. */
    public static boolean isDark() {
        Color background = UIManager.getColor("Panel.background");
        return background != null && luminance(background) < 0.5;
    }

    /** Colour that stands for {@code tone}, readable on the panel background. */
    public static Color color(Tone tone) {
        // The askai-java8 comic palette of the active theme (petrol = good, orange = attention, red = fault).
        ComicPalette palette = AskAiTheme.active().comicPalette();
        switch (tone) {
            case SUCCESS:
                return palette.getAgentPetrol();
            case WARNING:
                return blend(palette.getAccentOrange(), palette.getInk(), 0.25f);
            case ERROR:
                return palette.getAccentRed();
            case INFO:
                return palette.getNavigationBlue();
            case NEUTRAL:
            default:
                return mutedForeground();
        }
    }

    /** Soft background for a banner or badge of {@code tone}. */
    public static Color tint(Tone tone) {
        Color base = tone == Tone.NEUTRAL ? foreground() : color(tone);
        return blend(base, background(), isDark() ? 0.78f : 0.86f);
    }

    public static Color foreground() {
        return orDefault(UIManager.getColor("Label.foreground"), Color.BLACK);
    }

    public static Color background() {
        return orDefault(UIManager.getColor("Panel.background"), Color.WHITE);
    }

    /** Foreground for secondary text such as hints and descriptions. */
    public static Color mutedForeground() {
        Color disabled = UIManager.getColor("Label.disabledForeground");
        return disabled != null ? disabled : blend(foreground(), background(), 0.55f);
    }

    /** Thin line colour for separators and card borders. */
    public static Color lineColor() {
        Color separator = UIManager.getColor("Separator.foreground");
        return separator != null ? separator : blend(foreground(), background(), 0.75f);
    }

    /** Base label font of the look-and-feel at the current scale. */
    public static Font baseFont() {
        Font font = UIManager.getFont("Label.font");
        return font != null ? font : new Font(Font.DIALOG, Font.PLAIN, 12);
    }

    /** Label font scaled by {@code factor}; bold when {@code bold}. */
    public static Font derived(float factor, boolean bold) {
        Font base = baseFont();
        return base.deriveFont(bold ? Font.BOLD : Font.PLAIN, base.getSize2D() * factor);
    }

    /** Monospaced font that matches the current label size. */
    public static Font monospace() {
        return new Font(Font.MONOSPACED, Font.PLAIN, baseFont().getSize());
    }

    /** Mix {@code color} towards {@code into} by {@code amount} (0 keeps {@code color}, 1 gives {@code into}). */
    public static Color blend(Color color, Color into, float amount) {
        float keep = 1f - amount;
        return new Color(
            Math.round(color.getRed() * keep + into.getRed() * amount),
            Math.round(color.getGreen() * keep + into.getGreen() * amount),
            Math.round(color.getBlue() * keep + into.getBlue() * amount));
    }

    static double luminance(Color color) {
        return (0.2126 * color.getRed() + 0.7152 * color.getGreen() + 0.0722 * color.getBlue()) / 255.0;
    }

    private static Color orDefault(Color color, Color fallback) {
        return color != null ? color : fallback;
    }
}
