package com.aresstack.comiccontrols.theme;

import com.aresstack.comiccontrols.border.ComicBorder;

import javax.swing.BorderFactory;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.plaf.BorderUIResource;
import javax.swing.plaf.ColorUIResource;
import java.awt.Color;

/**
 * One-shot installation of the comic design language into the {@link UIManager} defaults, so the
 * language reaches components the library does not wrap individually: EVERY dropdown and context
 * popup gets the ink contour and the yellow/ink selection, and the menu bar gets its ink baseline.
 * Surfaces stay calm — this installs contours and selection accents, no burst backgrounds.
 *
 * <p>Call once at startup, after any look-and-feel setup and before the UI is built.</p>
 */
public final class ComicTheme {

    private ComicTheme() {
    }

    /** Fully qualified class names of the global delegates registered by {@link #install}. */
    public static final String BUTTON_UI = "com.aresstack.comiccontrols.control.ComicButtonUI";
    public static final String SCROLL_BAR_UI = "com.aresstack.comiccontrols.control.ComicScrollBarUI";
    public static final String SPLIT_PANE_UI = "com.aresstack.comiccontrols.control.ComicSplitPaneUI";

    /**
     * Installs the theme into the {@link UIManager} defaults: surfaces, text, fields, tables, lists,
     * trees, combos, tabs, progress bars, tooltips and menus get the theme's tokens, buttons and
     * scrollbars the comic delegates; the menu/popup contours of {@link #installMenuDefaults} are
     * included. Host-only — plugins never install UIManager defaults.
     */
    public static void install(AskAiTheme theme) {
        if (theme == null) {
            throw new IllegalArgumentException("theme must not be null");
        }
        ComicPalette p = theme.comicPalette();
        ColorUIResource surface = color(p.getSurface());
        ColorUIResource ink = color(p.getInk());
        ColorUIResource accentYellow = color(p.getAccentYellow());
        ColorUIResource accentOrange = color(p.getAccentOrange());
        ColorUIResource fieldBg = color(theme.fieldBg());
        ColorUIResource fieldBorder = color(theme.fieldBorder());
        ColorUIResource buttonPlate = color(theme.buttonPlate());

        for (String key : new String[]{"Panel", "Viewport", "ScrollPane", "SplitPane", "OptionPane"}) {
            UIManager.put(key + ".background", surface);
        }
        UIManager.put("Label.foreground", ink);

        UIManager.put("ButtonUI", BUTTON_UI);
        UIManager.put("Button.foreground", ink);
        UIManager.put("ScrollBarUI", SCROLL_BAR_UI);
        UIManager.put("SplitPaneUI", SPLIT_PANE_UI);

        for (String kind : new String[]{"TextField", "TextArea", "PasswordField", "FormattedTextField"}) {
            UIManager.put(kind + ".background", fieldBg);
            UIManager.put(kind + ".foreground", ink);
            UIManager.put(kind + ".caretForeground", ink);
            UIManager.put(kind + ".selectionBackground", accentYellow);
            UIManager.put(kind + ".selectionForeground", ink);
            UIManager.put(kind + ".inactiveForeground", color(theme.hintTextSoft()));
            // read-only / disabled fields rest on the quiet paper (Swing swaps to these on setEditable/setEnabled)
            UIManager.put(kind + ".inactiveBackground", color(theme.fieldBgQuiet()));
            UIManager.put(kind + ".disabledBackground", color(theme.fieldBgQuiet()));
        }
        Border fieldLine = BorderFactory.createLineBorder(theme.fieldBorder());
        UIManager.put("TextField.border", new BorderUIResource.CompoundBorderUIResource(
                fieldLine, BorderFactory.createEmptyBorder(3, 6, 3, 6)));

        for (String kind : new String[]{"CheckBox", "RadioButton"}) {
            UIManager.put(kind + ".background", surface);
            UIManager.put(kind + ".foreground", ink);
        }

        UIManager.put("Table.background", fieldBg);
        UIManager.put("Table.foreground", ink);
        UIManager.put("Table.gridColor", fieldBorder);
        UIManager.put("Table.selectionBackground", accentYellow);
        UIManager.put("Table.selectionForeground", ink);
        UIManager.put("TableHeader.background", surface);
        UIManager.put("TableHeader.foreground", ink);

        for (String kind : new String[]{"List", "Tree"}) {
            UIManager.put(kind + ".selectionBackground", accentYellow);
            UIManager.put(kind + ".selectionForeground", ink);
        }

        UIManager.put("ComboBox.background", fieldBg);
        UIManager.put("ComboBox.foreground", ink);
        UIManager.put("ComboBox.selectionBackground", accentYellow);
        UIManager.put("ComboBox.selectionForeground", ink);

        UIManager.put("TabbedPane.background", surface);
        UIManager.put("TabbedPane.foreground", ink);
        UIManager.put("TabbedPane.selected", buttonPlate);
        UIManager.put("TabbedPane.contentAreaColor", surface);

        UIManager.put("ProgressBar.foreground", accentOrange);
        UIManager.put("ProgressBar.background", surface);
        UIManager.put("ProgressBar.selectionForeground", surface);
        UIManager.put("ProgressBar.selectionBackground", ink);

        UIManager.put("ToolTip.background", color(theme.warmFocus()));
        UIManager.put("ToolTip.foreground", ink);
        UIManager.put("ToolTip.border",
                new BorderUIResource.LineBorderUIResource(p.getInk()));

        UIManager.put("MenuBar.background", surface);
        // every popup entry kind on the same plate (radio/check items too, e.g. the chat mode menu)
        for (String kind : new String[]{"Menu", "MenuItem", "PopupMenu", "RadioButtonMenuItem", "CheckBoxMenuItem"}) {
            UIManager.put(kind + ".background", buttonPlate);
        }

        installMenuDefaults(p);
    }

    private static ColorUIResource color(Color color) {
        return new ColorUIResource(color);
    }

    public static void installMenuDefaults() {
        installMenuDefaults(ComicPalette.defaultPalette());
    }

    public static void installMenuDefaults(ComicPalette palette) {
        if (palette == null) {
            throw new IllegalArgumentException("palette must not be null");
        }
        ColorUIResource selectionBackground = new ColorUIResource(palette.getAccentYellow());
        ColorUIResource selectionForeground = new ColorUIResource(palette.getInk());

        UIManager.put("PopupMenu.border", ComicBorder.popupBorder(palette));
        UIManager.put("MenuBar.border",
                BorderFactory.createMatteBorder(0, 0, 1, 0, palette.getInk()));

        String[] itemKinds = {"MenuItem", "Menu", "CheckBoxMenuItem", "RadioButtonMenuItem"};
        for (String kind : itemKinds) {
            UIManager.put(kind + ".selectionBackground", selectionBackground);
            UIManager.put(kind + ".selectionForeground", selectionForeground);
        }
        UIManager.put("MenuItem.acceleratorSelectionForeground", selectionForeground);
        UIManager.put("RadioButtonMenuItem.checkIcon", new ComicMenuRadioIcon());
    }
}
