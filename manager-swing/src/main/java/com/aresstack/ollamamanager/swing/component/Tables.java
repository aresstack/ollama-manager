package com.aresstack.ollamamanager.swing.component;

import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;
import java.awt.Component;
import java.awt.Font;

/**
 * Shared table setup so every list in the console behaves the same: single selection, rows sized to the scaled
 * font, right-aligned numbers and a sort by column header.
 */
public final class Tables {

    private Tables() {
    }

    /** Apply the console defaults to {@code table}. */
    public static void setup(JTable table, String accessibleName) {
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFillsViewportHeight(true);
        table.setAutoCreateRowSorter(true);
        table.setShowVerticalLines(false);
        table.setIntercellSpacing(new java.awt.Dimension(Spacing.GAP, 1));
        table.getTableHeader().setReorderingAllowed(false);
        table.getAccessibleContext().setAccessibleName(accessibleName);
        fitRowHeight(table);
    }

    /** Size rows to the current (possibly scaled) table font; call again from {@code updateUI}. */
    public static void fitRowHeight(JTable table) {
        Font font = UIManager.getFont("Table.font");
        if (font == null) {
            font = table.getFont();
        }
        table.setRowHeight(table.getFontMetrics(font).getHeight() + 6);
    }

    /** Right-align the given columns, for sizes and counts. */
    public static void alignRight(JTable table, int... columns) {
        DefaultTableCellRenderer renderer = new DefaultTableCellRenderer();
        renderer.setHorizontalAlignment(SwingConstants.TRAILING);
        for (int column : columns) {
            table.getColumnModel().getColumn(column).setCellRenderer(renderer);
        }
    }

    /** Centre the given columns, for short counts and states. */
    public static void alignCenter(JTable table, int... columns) {
        DefaultTableCellRenderer renderer = new DefaultTableCellRenderer();
        renderer.setHorizontalAlignment(SwingConstants.CENTER);
        for (int column : columns) {
            table.getColumnModel().getColumn(column).setCellRenderer(renderer);
        }
    }

    /**
     * Give every column a preferred width that fits its header text, so that the table's space is shared in
     * proportion to the headers and none of them is cut to "Total tok…".
     */
    public static void fitHeaders(JTable table) {
        java.awt.FontMetrics metrics = table.getFontMetrics(table.getFont());
        for (int i = 0; i < table.getColumnCount(); i++) {
            TableColumn column = table.getColumnModel().getColumn(i);
            int width = metrics.stringWidth(String.valueOf(column.getHeaderValue())) + 2 * metrics.charWidth('m');
            column.setPreferredWidth(Math.max(width, metrics.charWidth('m') * 6));
        }
    }

    /** Give a column a preferred width in characters of the table font. */
    public static void setWidthInChars(JTable table, int column, int chars) {
        TableColumn tableColumn = table.getColumnModel().getColumn(column);
        int width = table.getFontMetrics(table.getFont()).charWidth('m') * chars;
        tableColumn.setPreferredWidth(width);
    }

    /** Renderer that paints a {@link Theme.Tone} coloured text, for state columns. */
    public static DefaultTableCellRenderer toneRenderer(final ToneMapper mapper) {
        return new DefaultTableCellRenderer() {
            private static final long serialVersionUID = 1L;

            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                           boolean focused, int row, int column) {
                super.getTableCellRendererComponent(table, value, selected, focused, row, column);
                if (!selected) {
                    Theme.Tone tone = mapper.tone(value);
                    setForeground(tone == null || tone == Theme.Tone.NEUTRAL ? table.getForeground()
                        : Theme.color(tone));
                }
                return this;
            }
        };
    }

    /** Maps a cell value to a tone. */
    public interface ToneMapper {
        Theme.Tone tone(Object value);
    }
}
