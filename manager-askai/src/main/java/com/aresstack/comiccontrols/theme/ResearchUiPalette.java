package com.aresstack.comiccontrols.theme;

import java.awt.Color;

/**
 * The ONE color table of the dark research-UI design study (phase selector, chats footer, blacklist
 * chips, …). New Java2D controls read their colors from here instead of hardcoding hex values per
 * class. Every constant is a living {@link ThemeColor} of the research token with exactly its name:
 * it always paints the value of the CURRENTLY active {@link AskAiTheme}, so a runtime theme switch
 * reaches every consumer without touching it.
 */
public final class ResearchUiPalette {

    /** Deepest window background of the study. */
    public static final Color DARK_BASE = token("DARK_BASE");
    /** Raised dark surface (cards, popups). */
    public static final Color DARK_SURFACE = token("DARK_SURFACE");
    /** The workspace strip under the composer (blacklist area). */
    public static final Color WORKSPACE_SURFACE = token("WORKSPACE_SURFACE");
    /** Chip body fill. */
    public static final Color CHIP_SURFACE = token("CHIP_SURFACE");
    /** Secondary dark controls (buttons, language pill, add chip). */
    public static final Color SECONDARY_SURFACE = token("SECONDARY_SURFACE");

    public static final Color TEXT_PRIMARY = token("TEXT_PRIMARY");
    public static final Color TEXT_MUTED = token("TEXT_MUTED");
    public static final Color TEXT_DARK = token("TEXT_DARK");

    public static final Color PURPLE_PRIMARY = token("PURPLE_PRIMARY");
    public static final Color PURPLE_ACTION = token("PURPLE_ACTION");
    /** Hover shade between primary and action purple (phase pill hover). */
    public static final Color PURPLE_HOVER = token("PURPLE_HOVER");

    public static final Color BORDER_DARK = token("BORDER_DARK");
    public static final Color BORDER_WINDOW = token("BORDER_WINDOW");

    /**
     * THE AskAI accent blue of the active navigation (the highlighted Chats ribbon entry / composer
     * primary — {@code ChatComposerPanel} reads its PRIMARY from here). The phase selector and the
     * chat-list selection use exactly this token; no second blue palette exists.
     */
    public static final Color ACCENT_BLUE = token("ACCENT_BLUE");
    /** The destructive red of the composer family (delete-all hover, stop button). */
    public static final Color DANGER_RED = token("DANGER_RED");

    /** Light-chrome tokens for design-study controls embedded in the BRIGHT AskAI surfaces. */
    public static final Color LIGHT_CONTROL_BG = token("LIGHT_CONTROL_BG");
    public static final Color LIGHT_CONTROL_BORDER = token("LIGHT_CONTROL_BORDER");
    public static final Color LIGHT_CONTROL_TEXT = token("LIGHT_CONTROL_TEXT");
    public static final Color LIGHT_TEXT_MUTED = token("LIGHT_TEXT_MUTED");

    /** Hover fill of secondary dark controls. */
    public static final Color SECONDARY_HOVER = token("SECONDARY_HOVER");
    /** Hovered chip body / hovered chip border. */
    public static final Color CHIP_HOVER_SURFACE = token("CHIP_HOVER_SURFACE");
    public static final Color CHIP_HOVER_BORDER = token("CHIP_HOVER_BORDER");
    /** Round hover backdrop behind a chip's ✕. */
    public static final Color CHIP_CLOSE_HOVER = token("CHIP_CLOSE_HOVER");
    /** The subtle divider on top of dark workspace strips. */
    public static final Color WORKSPACE_DIVIDER = token("WORKSPACE_DIVIDER");

    // ------------------------------------------------------------------ out-of-scope sky
    // Deliberately air-like, harmonizing with the bright AskAI surfaces — never a dominant blue.

    /** The sky's top tone; it fades to fully transparent towards the chat. */
    public static final Color SKY_TOP = token("SKY_TOP");
    /** The sky's second tone the gradient runs into (equal to {@link #SKY_TOP} in the classic theme). */
    public static final Color SKY_MID = token("SKY_MID");
    /**
     * The COLLAPSED sky status bar: a firmer, calm sky blue so the bar reads as a deliberate
     * control — clearly stronger than the airy near-white of the open sky.
     */
    public static final Color SKY_BAR_SURFACE = token("SKY_BAR_SURFACE");
    /** The quiet caption ("Außerhalb des Scopes"). */
    public static final Color SKY_CAPTION = token("SKY_CAPTION");
    /** Cloud chips: light body, soft blue border, calm slate text. */
    public static final Color CLOUD_SURFACE = token("CLOUD_SURFACE");
    public static final Color CLOUD_BORDER = token("CLOUD_BORDER");
    public static final Color CLOUD_TEXT = token("CLOUD_TEXT");
    public static final Color CLOUD_HOVER_SURFACE = token("CLOUD_HOVER_SURFACE");
    public static final Color CLOUD_HOVER_BORDER = token("CLOUD_HOVER_BORDER");
    /** The remove ✕ inside a cloud chip (hover uses {@link #CLOUD_TEXT}). */
    public static final Color CLOUD_CLOSE = token("CLOUD_CLOSE");

    /** The living color of this constant's research token (follows every theme switch). */
    private static Color token(String constantName) {
        return ThemeColor.of(AskAiTheme.RESEARCH_PREFIX + constantName);
    }

    private ResearchUiPalette() {
    }
}
