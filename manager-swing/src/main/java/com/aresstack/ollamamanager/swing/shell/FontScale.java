package com.aresstack.ollamamanager.swing.shell;

import javax.swing.UIDefaults;
import javax.swing.UIManager;
import javax.swing.plaf.FontUIResource;
import java.awt.Font;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Scales all look-and-feel fonts relative to their original size, so the whole UI grows or shrinks together.
 */
public final class FontScale {

    public static final float MIN = 0.8f;
    public static final float MAX = 2.0f;
    public static final float STEP = 0.1f;

    private final Map<Object, Font> originals = new HashMap<Object, Font>();
    private float scale = 1f;

    /** Remember the current look-and-feel fonts as the 100 % baseline. */
    public FontScale() {
        UIDefaults defaults = UIManager.getLookAndFeelDefaults();
        List<Object> keys = new ArrayList<Object>(defaults.keySet());
        for (Object key : keys) {
            Object value = defaults.get(key);
            if (value instanceof Font) {
                originals.put(key, (Font) value);
            }
        }
    }

    public float scale() {
        return scale;
    }

    /** Apply a scale to UIManager; callers must refresh their component trees afterwards. */
    public void apply(float requested) {
        scale = clamp(requested);
        for (Map.Entry<Object, Font> entry : originals.entrySet()) {
            Font base = entry.getValue();
            UIManager.put(entry.getKey(), new FontUIResource(base.deriveFont(base.getSize2D() * scale)));
        }
    }

    static float clamp(float value) {
        if (Float.isNaN(value)) {
            return 1f;
        }
        float rounded = Math.round(value * 10f) / 10f;
        return Math.max(MIN, Math.min(MAX, rounded));
    }
}
