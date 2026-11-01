package com.aresstack.comiccontrols.control;

import com.aresstack.comiccontrols.theme.ThemeColor;
import com.aresstack.comiccontrols.theme.ComicPalette;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JList;
import javax.swing.ListCellRenderer;
import java.awt.Color;
import java.awt.Component;

/**
 * Restyles a {@link JList} into the comic language without touching its behavior (pattern of
 * {@link ComicTableSupport}): calm {@code comic.fieldBg} paper, ink text, the light navigation-blue
 * selection wash with ink text, and a little row padding for the default renderer. A renderer the
 * caller installs later is left alone.
 */
public final class ComicListSupport {

    private ComicListSupport() {
    }

    /** Apply the comic list look with the default palette; returns the list for chaining. */
    public static <E> JList<E> style(JList<E> list) {
        return style(list, ComicPalette.defaultPalette());
    }

    public static <E> JList<E> style(JList<E> list, ComicPalette palette) {
        if (list == null || palette == null) {
            throw new IllegalArgumentException("list and palette must not be null");
        }
        Color paper = ThemeColor.of("comic.fieldBg");
        list.setBackground(paper);
        list.setForeground(palette.getInk());
        list.setSelectionBackground(selectionWash(palette, paper));
        list.setSelectionForeground(palette.getInk());
        ListCellRenderer<? super E> renderer = list.getCellRenderer();
        if (renderer instanceof DefaultListCellRenderer.UIResource || renderer == null) {
            list.setCellRenderer(new PaddedRenderer());
        }
        return list;
    }

    /** The selection wash: navigation blue mixed into the paper. */
    static Color selectionWash(ComicPalette palette, Color paper) {
        return ThemeColor.mix(palette.getNavigationBlue(), paper, 0.85f);
    }

    /** The default renderer with comic row padding. */
    static final class PaddedRenderer extends DefaultListCellRenderer {
        private static final long serialVersionUID = 1L;

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected,
                                                      boolean focused) {
            Component component = super.getListCellRendererComponent(list, value, index, selected, focused);
            setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
            return component;
        }
    }
}
