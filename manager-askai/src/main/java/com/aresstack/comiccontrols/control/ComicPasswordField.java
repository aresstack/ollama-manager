package com.aresstack.comiccontrols.control;

import javax.swing.JPasswordField;
import javax.swing.text.Document;

/** The password sibling of {@link ComicTextField}: always the {@link ComicPasswordFieldUI}. */
public class ComicPasswordField extends JPasswordField {

    private static final long serialVersionUID = 1L;

    public ComicPasswordField() {
        super();
    }

    public ComicPasswordField(int columns) {
        super(columns);
    }

    public ComicPasswordField(String text) {
        super(text);
    }

    public ComicPasswordField(String text, int columns) {
        super(text, columns);
    }

    public ComicPasswordField(Document document, String text, int columns) {
        super(document, text, columns);
    }

    @Override
    public void updateUI() {
        setUI(new ComicPasswordFieldUI());
        invalidate();
    }
}
