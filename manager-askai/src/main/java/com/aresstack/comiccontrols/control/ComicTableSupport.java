package com.aresstack.comiccontrols.control;

import com.aresstack.comiccontrols.theme.ThemeColor;
import com.aresstack.comiccontrols.theme.ComicPalette;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;

/**
 * Restyles a {@link JTable} into the comic language without touching its behavior: calm paper
 * rows, thin derived horizontal lines instead of a full grid, a light navigation-blue selection
 * wash with ink text, and a FLAT header (no bevel) with a quiet ink baseline. The header keeps the
 * look-and-feel's sort arrows because the original renderer still paints underneath — this class
 * only re-dresses it. Generic on purpose: sorting, models, tooltips and column widths stay the
 * caller's business.
 */
public final class ComicTableSupport {

    private ComicTableSupport() {
    }

    /** Apply the comic table look with the default palette. */
    public static void style(JTable table) {
        style(table, ComicPalette.defaultPalette());
    }

    public static void style(JTable table, ComicPalette palette) {
        Color paper = ThemeColor.of("comic.fieldBg");
        Color line = ThemeColor.mix(palette.getInk(), paper, 0.86f);
        table.setBackground(paper);
        table.setForeground(palette.getInk());
        table.setRowHeight(Math.max(table.getRowHeight(), 26));
        table.setShowVerticalLines(false);
        table.setShowHorizontalLines(true);
        table.setGridColor(line);
        table.setIntercellSpacing(new Dimension(0, 1));
        table.setSelectionBackground(
                ThemeColor.mix(palette.getNavigationBlue(), paper, 0.85f));
        table.setSelectionForeground(palette.getInk());
        table.setFillsViewportHeight(true);

        JTableHeader header = table.getTableHeader();
        if (header != null) {
            header.setOpaque(false);
            final TableCellRenderer base = header.getDefaultRenderer();
            final Font headerFont = header.getFont() == null
                    ? null : header.getFont().deriveFont(Font.BOLD, 11.5f);
            final Color baseline = ThemeColor.mix(palette.getInk(), paper, 0.55f);
            header.setDefaultRenderer(new TableCellRenderer() {
                public Component getTableCellRendererComponent(JTable owner, Object value,
                        boolean selected, boolean focused, int row, int column) {
                    Component component = base.getTableCellRendererComponent(
                            owner, value, selected, focused, row, column);
                    component.setBackground(paper);
                    component.setForeground(palette.getInk());
                    if (headerFont != null) {
                        component.setFont(headerFont);
                    }
                    if (component instanceof JComponent) {
                        // Flat: the ink baseline replaces the bevel; sort arrows stay with the
                        // wrapped look-and-feel renderer.
                        ((JComponent) component).setOpaque(true);
                        ((JComponent) component).setBorder(BorderFactory.createCompoundBorder(
                                BorderFactory.createMatteBorder(0, 0, 1, 0, baseline),
                                BorderFactory.createEmptyBorder(4, 6, 4, 6)));
                    }
                    return component;
                }
            });
        }
    }
}
