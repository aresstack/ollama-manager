package com.aresstack.ollamamanager.swing.component;

/**
 * Escapes text that is embedded into Swing HTML labels.
 */
public final class Html {

    private Html() {
    }

    public static String escape(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>");
    }
}
