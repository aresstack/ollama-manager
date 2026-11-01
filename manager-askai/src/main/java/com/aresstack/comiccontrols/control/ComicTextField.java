package com.aresstack.comiccontrols.control;

import javax.swing.JTextField;
import javax.swing.text.Document;

/**
 * A {@link JTextField} that always carries the {@link ComicTextFieldUI} (rounded field plate, theme
 * colors) — also across {@code updateUI()} on a runtime theme switch. Usable as a base for anonymous
 * subclasses (e.g. an in-field placeholder painted in {@code paintComponent}).
 */
public class ComicTextField extends JTextField {

    private static final long serialVersionUID = 1L;

    public ComicTextField() {
        super();
    }

    public ComicTextField(int columns) {
        super(columns);
    }

    public ComicTextField(String text) {
        super(text);
    }

    public ComicTextField(String text, int columns) {
        super(text, columns);
    }

    public ComicTextField(Document document, String text, int columns) {
        super(document, text, columns);
    }

    @Override
    public void updateUI() {
        setUI(new ComicTextFieldUI());
        invalidate();
    }
}
