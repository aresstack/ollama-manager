package com.aresstack.askai.java8.ui.about;

import org.junit.Test;

import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

/** The About footer's copyright: grey "© 2026 AresStack", the name links to the Ollama Manager repository. */
public class CopyrightFooterTest {

    @Test
    public void theNameOpensTheRepository() {
        assertEquals("https://github.com/aresstack/ollama-manager", CopyrightFooter.repositoryUrl());
        final List<String> opened = new ArrayList<String>();
        CopyrightFooter footer = new CopyrightFooter(CopyrightFooter.repositoryUrl(), opened::add);
        assertEquals("AresStack", footer.link().getText());
        MouseEvent click = new MouseEvent(footer.link(), MouseEvent.MOUSE_CLICKED, 0L, 0, 1, 1, 1, false);
        for (MouseListener listener : footer.link().getMouseListeners()) {
            listener.mouseClicked(click);
        }
        assertEquals("[https://github.com/aresstack/ollama-manager]", opened.toString());
    }
}
