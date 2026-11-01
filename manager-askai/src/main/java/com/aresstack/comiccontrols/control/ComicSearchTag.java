package com.aresstack.comiccontrols.control;

import com.aresstack.comiccontrols.theme.ThemeColor;
import com.aresstack.comiccontrols.theme.ComicPalette;

import java.awt.Dimension;

/**
 * The third search-bar variant: the WEB-SEARCH tag in the top bar. At rest it reads like its
 * toolbar neighbours (the Research Agent / tab pills): calm surface, INK contour, the controls'
 * corner radius, a quiet grey go arrow — only the amber magnifier stays as the eye catcher.
 * Focusing keeps the amber family: warm cream surface and the amber contour of the find bar.
 * Compact by design: it sits inline in a tag flow, not in a toolbar. (History: v1 filled the
 * whole chip suggestion-yellow, v2 wore a permanent amber rim — both too loud at rest.)
 */
public class ComicSearchTag extends ComicSearchBar {

    /** The toolbar controls' rounding (ResearchUiMetrics.RADIUS_CONTROL equivalent). */
    private static final int TAG_ARC = 10;
    private static final int MIN_WIDTH = 200;

    public ComicSearchTag(String placeholder, String tooltip) {
        this(placeholder, tooltip, ComicPalette.defaultPalette());
    }

    public ComicSearchTag(String placeholder, String tooltip, ComicPalette palette) {
        super(placeholder, tooltip,
                palette.getSurface(), ThemeColor.of("comic.warmFocus"),
                palette.getInk(), palette.getAccentOrange(),
                palette.getAccentOrange(), ThemeColor.of("comic.hintTextSoft"), TAG_ARC, 1.4f);
        getTextField().setForeground(palette.getInk());
        getTextField().setCaretColor(palette.getInk());
        getGoButton().setForeground(ThemeColor.of("comic.hintText"));
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension size = super.getPreferredSize();
        return new Dimension(Math.max(MIN_WIDTH, size.width), Math.max(24, size.height));
    }

}
