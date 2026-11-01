package com.aresstack.ollamamanager.swing.shell;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

public class NavigationListTest {

    @Test
    public void oneSelectionAcrossBothGroups() {
        NavigationList navigation = new NavigationList();
        AtomicInteger changes = new AtomicInteger();
        navigation.addListSelectionListener(event -> changes.incrementAndGet());

        navigation.setSelectedValue(Section.MODELS, false);
        assertEquals(Section.MODELS, navigation.getSelectedValue());
        navigation.setSelectedValue(Section.JOBS, false);
        assertEquals(Section.JOBS, navigation.getSelectedValue());
        navigation.setSelectedValue(Section.DASHBOARD, false);
        assertEquals(Section.DASHBOARD, navigation.getSelectedValue());
        assertEquals(3, changes.get());
    }

    @Test
    public void jobsIsTheLastSectionAndTheOnlyBottomOne() {
        Section[] all = Section.values();
        assertEquals(Section.JOBS, all[all.length - 1]);
    }
}
