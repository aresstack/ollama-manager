package com.aresstack.comiccontrols.control;

import com.aresstack.comiccontrols.theme.AskAiTheme;

import javax.swing.JTextArea;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.UIResource;
import javax.swing.text.Document;
import java.awt.Color;

/**
 * A {@link JTextArea} in the comic language: {@code comic.fieldBg} paper, ink text and caret, the yellow
 * selection. Text areas usually live in a scroll pane, so there is no own contour. Caller-set colors win;
 * the look-and-feel values are re-themed on every {@link #updateUI()} (i.e. on every theme switch).
 */
public class ComicTextArea extends JTextArea {

    public ComicTextArea() {
        super();
    }

    public ComicTextArea(String text) {
        super(text);
    }

    public ComicTextArea(int rows, int columns) {
        super(rows, columns);
    }

    public ComicTextArea(String text, int rows, int columns) {
        super(text, rows, columns);
    }

    public ComicTextArea(Document document) {
        super(document);
    }

    public ComicTextArea(Document document, String text, int rows, int columns) {
        super(document, text, rows, columns);
    }

    @Override
    public void updateUI() {
        super.updateUI();
        AskAiTheme theme = AskAiTheme.active();
        Color ink = theme.comicPalette().getInk();
        if (isLookAndFeelValue(getBackground())) {
            setBackground(new ColorUIResource(theme.fieldBg()));
        }
        if (isLookAndFeelValue(getForeground())) {
            setForeground(new ColorUIResource(ink));
        }
        if (isLookAndFeelValue(getCaretColor())) {
            setCaretColor(new ColorUIResource(ink));
        }
        if (isLookAndFeelValue(getSelectionColor())) {
            setSelectionColor(new ColorUIResource(theme.comicPalette().getAccentYellow()));
        }
        if (isLookAndFeelValue(getSelectedTextColor())) {
            setSelectedTextColor(new ColorUIResource(ink));
        }
        if (isLookAndFeelValue(getDisabledTextColor())) {
            setDisabledTextColor(new ColorUIResource(theme.hintText()));
        }
    }

    private static boolean isLookAndFeelValue(Color color) {
        return color == null || color instanceof UIResource;
    }
}
