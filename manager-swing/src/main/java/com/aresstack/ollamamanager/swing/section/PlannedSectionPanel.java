package com.aresstack.ollamamanager.swing.section;

import com.aresstack.ollamamanager.swing.component.PlaceholderPanel;
import com.aresstack.ollamamanager.swing.component.SectionPanel;
import com.aresstack.ollamamanager.swing.shell.Section;

/**
 * Section whose feature is connected in a later step. It keeps the shared layout and says what will come.
 */
public final class PlannedSectionPanel extends SectionPanel {

    private static final long serialVersionUID = 1L;

    public PlannedSectionPanel(Section section, String plan) {
        super(section.title(), section.description());
        setBody(new PlaceholderPanel(plan));
    }
}
