package com.aresstack.ollamamanager.swing.component;

import org.junit.Test;

import java.awt.Color;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ThemeTest {

    @Test
    public void blendMovesTowardsTarget() {
        assertEquals(Color.BLACK, Theme.blend(Color.BLACK, Color.WHITE, 0f));
        assertEquals(Color.WHITE, Theme.blend(Color.BLACK, Color.WHITE, 1f));
        assertEquals(new Color(128, 128, 128), Theme.blend(Color.BLACK, Color.WHITE, 0.5f));
    }

    @Test
    public void luminanceOrdersColours() {
        assertTrue(Theme.luminance(Color.WHITE) > Theme.luminance(Color.GRAY));
        assertTrue(Theme.luminance(Color.GRAY) > Theme.luminance(Color.BLACK));
    }

    @Test
    public void everyToneHasAColourDifferentFromTheBackground() {
        for (Theme.Tone tone : Theme.Tone.values()) {
            assertNotNull(Theme.color(tone));
            assertNotEquals(Theme.background(), Theme.color(tone));
            assertNotNull(Theme.tint(tone));
        }
    }

    @Test
    public void derivedFontScalesWithFactor() {
        assertEquals(Theme.baseFont().getSize2D() * 2f, Theme.derived(2f, false).getSize2D(), 0.01f);
        assertTrue(Theme.derived(1f, true).isBold());
    }
}
