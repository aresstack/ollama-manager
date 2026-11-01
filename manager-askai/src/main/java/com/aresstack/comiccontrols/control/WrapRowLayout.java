package com.aresstack.comiccontrols.control;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;

/**
 * A {@link FlowLayout} whose preferred size is honest about WRAPPING (#43): plain FlowLayout
 * wraps its children onto further rows but keeps reporting a single-row preferred height, so a
 * narrow container CLIPS everything that wrapped — controls silently vanish instead of moving
 * to the next line. This layout computes the real wrapped height for the width the container
 * actually has, so detail rows (status/rating/score plates and friends) grow vertically when
 * the splitter narrows and every control stays visible and operable.
 */
public class WrapRowLayout extends FlowLayout {

    public WrapRowLayout() {
        super();
    }

    public WrapRowLayout(int align) {
        super(align);
    }

    public WrapRowLayout(int align, int hgap, int vgap) {
        super(align, hgap, vgap);
    }

    @Override
    public Dimension preferredLayoutSize(Container target) {
        return wrappedSize(target, true);
    }

    @Override
    public Dimension minimumLayoutSize(Container target) {
        Dimension minimum = wrappedSize(target, false);
        minimum.width -= getHgap() + 1;
        return minimum;
    }

    /** The real size of the wrapped rows for the container's CURRENT width. */
    private Dimension wrappedSize(Container target, boolean preferred) {
        synchronized (target.getTreeLock()) {
            int targetWidth = target.getSize().width;
            Container top = target;
            while (top.getSize().width == 0 && top.getParent() != null) {
                top = top.getParent();
            }
            if (targetWidth == 0) {
                targetWidth = top.getSize().width;
            }
            if (targetWidth == 0) {
                targetWidth = Integer.MAX_VALUE; // not laid out yet: fall back to one row
            }

            Insets insets = target.getInsets();
            int horizontalInsets = insets.left + insets.right + getHgap() * 2;
            int maxRowWidth = targetWidth - horizontalInsets;

            Dimension size = new Dimension(0, 0);
            int rowWidth = 0;
            int rowHeight = 0;
            for (int index = 0; index < target.getComponentCount(); index++) {
                Component component = target.getComponent(index);
                if (!component.isVisible()) {
                    continue;
                }
                Dimension d = preferred
                        ? component.getPreferredSize() : component.getMinimumSize();
                if (rowWidth + d.width > maxRowWidth && rowWidth > 0) {
                    // Row full: book it and start the next one — this is the honest height.
                    size.width = Math.max(size.width, rowWidth);
                    size.height += rowHeight + getVgap();
                    rowWidth = 0;
                    rowHeight = 0;
                }
                if (rowWidth != 0) {
                    rowWidth += getHgap();
                }
                rowWidth += d.width;
                rowHeight = Math.max(rowHeight, d.height);
            }
            size.width = Math.max(size.width, rowWidth);
            size.height += rowHeight;

            size.width += horizontalInsets;
            size.height += insets.top + insets.bottom + getVgap() * 2;

            // Inside a scroll pane the viewport width, not the (possibly huge) container
            // width, bounds the rows — otherwise the wrap never engages while scrolling.
            java.awt.Container scrollPane = javax.swing.SwingUtilities
                    .getAncestorOfClass(javax.swing.JScrollPane.class, target);
            if (scrollPane != null && target.isValid()) {
                size.width -= getHgap() + 1;
            }
            return size;
        }
    }
}
