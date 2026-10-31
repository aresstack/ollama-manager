package com.aresstack.ollamamanager.swing.shell;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class FontScaleTest {

    @Test
    public void clampsAndRoundsToSteps() {
        assertEquals(0.8f, FontScale.clamp(0.1f), 0f);
        assertEquals(2.0f, FontScale.clamp(9f), 0f);
        assertEquals(1.2f, FontScale.clamp(1.2000001f), 0f);
        assertEquals(1f, FontScale.clamp(Float.NaN), 0f);
    }
}
