package com.aresstack.ollamamanager.swing.shell;

import org.junit.After;
import org.junit.Test;

import java.awt.Rectangle;
import java.util.prefs.Preferences;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class WindowStateStoreTest {

    private final Preferences node = Preferences.userRoot().node("com/aresstack/ollamamanager/test-" + System.nanoTime());
    private final WindowStateStore store = new WindowStateStore(node);

    @After
    public void removeNode() throws Exception {
        node.removeNode();
    }

    @Test
    public void ignoresBoundsOutsideTheVisibleScreens() {
        node.putInt("window.x", 5000);
        node.putInt("window.y", 5000);
        node.putInt("window.width", 800);
        node.putInt("window.height", 600);

        assertNull(store.bounds(new Rectangle(0, 0, 1920, 1080)));
        assertEquals(new Rectangle(5000, 5000, 800, 600), store.bounds(new Rectangle(0, 0, 8000, 8000)));
    }

    @Test
    public void unknownSectionFallsBackToDashboard() {
        node.put("section", "REMOVED");
        assertEquals(Section.DASHBOARD, store.lastSection());
        store.saveSection(Section.JOBS);
        assertEquals(Section.JOBS, store.lastSection());
    }

    @Test
    public void fontScaleIsClamped() {
        store.saveFontScale(5f);
        assertEquals(FontScale.MAX, store.fontScale(), 0f);
    }
}
