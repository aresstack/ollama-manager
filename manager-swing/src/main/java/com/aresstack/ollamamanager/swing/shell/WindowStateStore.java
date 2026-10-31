package com.aresstack.ollamamanager.swing.shell;

import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.util.prefs.Preferences;

/**
 * Persists per-user UI state (window bounds, font scale, last section) in {@link Preferences}. This is view state,
 * not manager configuration, so it deliberately bypasses the configuration file.
 */
public final class WindowStateStore {

    private static final String X = "window.x";
    private static final String Y = "window.y";
    private static final String WIDTH = "window.width";
    private static final String HEIGHT = "window.height";
    private static final String MAXIMIZED = "window.maximized";
    private static final String FONT_SCALE = "font.scale";
    private static final String SECTION = "section";

    private final Preferences preferences;

    public WindowStateStore(Preferences preferences) {
        this.preferences = preferences;
    }

    public static WindowStateStore forUser() {
        return new WindowStateStore(Preferences.userRoot().node("com/aresstack/ollamamanager/ui"));
    }

    /** Return saved bounds if they are still visible on a current screen, otherwise {@code null}. */
    public Rectangle bounds(Rectangle visibleArea) {
        int width = preferences.getInt(WIDTH, -1);
        int height = preferences.getInt(HEIGHT, -1);
        if (width < 200 || height < 150) {
            return null;
        }
        Rectangle saved = new Rectangle(preferences.getInt(X, 0), preferences.getInt(Y, 0), width, height);
        return visibleArea != null && visibleArea.intersects(saved) ? saved : null;
    }

    public boolean maximized() {
        return preferences.getBoolean(MAXIMIZED, false);
    }

    /** Save the frame's normal bounds and maximized flag. */
    public void save(Frame frame) {
        boolean max = (frame.getExtendedState() & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH;
        preferences.putBoolean(MAXIMIZED, max);
        if (!max) {
            Rectangle bounds = frame.getBounds();
            preferences.putInt(X, bounds.x);
            preferences.putInt(Y, bounds.y);
            preferences.putInt(WIDTH, bounds.width);
            preferences.putInt(HEIGHT, bounds.height);
        }
    }

    public float fontScale() {
        return FontScale.clamp(preferences.getFloat(FONT_SCALE, 1f));
    }

    public void saveFontScale(float scale) {
        preferences.putFloat(FONT_SCALE, FontScale.clamp(scale));
    }

    public Section lastSection() {
        String name = preferences.get(SECTION, Section.DASHBOARD.name());
        try {
            return Section.valueOf(name);
        } catch (IllegalArgumentException e) {
            return Section.DASHBOARD;
        }
    }

    public void saveSection(Section section) {
        preferences.put(SECTION, section.name());
    }

    /** Union of all screen bounds, or {@code null} when headless. */
    public static Rectangle virtualScreen() {
        if (GraphicsEnvironment.isHeadless()) {
            return null;
        }
        Rectangle union = new Rectangle();
        for (java.awt.GraphicsDevice device : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
            union = union.union(device.getDefaultConfiguration().getBounds());
        }
        return union;
    }
}
