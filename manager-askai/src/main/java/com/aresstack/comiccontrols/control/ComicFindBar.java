package com.aresstack.comiccontrols.control;

import com.aresstack.comiccontrols.theme.ThemeColor;
import com.aresstack.comiccontrols.theme.ComicPalette;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * The warm AMBER search-bar variant for in-content search ("find in page"), ported from
 * MainframeMate's {@code FindBarPanel}. Same geometry as {@link ComicSearchBar}, but with the
 * design language's action accent while focused and a soft warm background tint.
 *
 * <p><b>Navigation mode</b> ({@link #setStepSearchEnabled(boolean)}): disabled (default), Enter
 * highlights ALL matches at once; enabled, the first Enter triggers the search and the ↵ button
 * transforms into ◀ ▶ prev/next arrows that step through matches. The mode toggles via a
 * right-click context menu and can be persisted through {@link StepSearchModeListener}.</p>
 */
public class ComicFindBar extends ComicSearchBar {

    private final JButton enterButton; // ↵  (initial state)
    private final JButton prevButton;  // ◀  (after first search, step-search mode)
    private final JButton nextButton;  // ▶  (after first search, step-search mode)
    private final JButton clearButton; // ✕  (always visible)

    private boolean stepSearchEnabled;
    private boolean arrowsVisible;

    private final List<ActionListener> searchListeners = new ArrayList<ActionListener>();
    private ActionListener prevListener;
    private ActionListener nextListener;
    private StepSearchModeListener stepSearchModeListener;
    private FindBarExitListener exitListener;

    /** Callback for persisting the step-search preference. */
    public interface StepSearchModeListener {
        void onStepSearchModeChanged(boolean stepSearch);
    }

    /** Callback for leaving the find bar via keyboard. */
    public interface FindBarExitListener {
        /** UP arrow pressed → transfer focus to the first line of the content. */
        void onExitUp();

        /** DOWN arrow pressed → transfer focus to the last line of the content. */
        void onExitDown();

        /** Enter on an empty field or Escape → dismiss / leave the find bar. */
        void onDismiss();
    }

    public ComicFindBar(String placeholder) {
        this(placeholder, null);
    }

    public ComicFindBar(String placeholder, String tooltip) {
        this(placeholder, tooltip, ComicPalette.defaultPalette());
    }

    public ComicFindBar(String placeholder, String tooltip, ComicPalette palette) {
        super(placeholder, tooltip,
                ThemeColor.of("comic.fieldBgQuiet"), ThemeColor.of("comic.warmFocus"),
                ThemeColor.of("comic.fieldBorder"), palette.getAccentOrange(),
                ThemeColor.of("comic.hintText"), ThemeColor.of("comic.hintTextSoft"), 10, 1f);

        // The inherited go button becomes the always-visible clear (✕) button.
        clearButton = getGoButton();
        clearButton.setText("✕");
        clearButton.setToolTipText("Clear the search field");
        for (ActionListener al : clearButton.getActionListeners()) {
            clearButton.removeActionListener(al);
        }
        clearButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                setText("");
                resetToEnterButton();
            }
        });

        enterButton = makeNavButton("↵", "Start search (Enter)");
        enterButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (getText().trim().isEmpty()) {
                    if (exitListener != null) {
                        exitListener.onDismiss();
                    }
                    return;
                }
                fireSearch(e);
                showArrows();
            }
        });

        prevButton = makeNavButton("◀", "Previous match");
        prevButton.setVisible(false);
        prevButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (prevListener != null) {
                    prevListener.actionPerformed(e);
                }
            }
        });

        nextButton = makeNavButton("▶", "Next match");
        nextButton.setVisible(false);
        nextButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (nextListener != null) {
                    nextListener.actionPerformed(e);
                }
            }
        });

        JPanel east = getEastPanel();
        east.removeAll();
        east.add(enterButton);
        east.add(prevButton);
        east.add(nextButton);
        east.add(clearButton);

        installContextMenu();
        installArrowKeyBindings();
    }

    /** Register a listener for keyboard-driven exits from the find bar. */
    public void setExitListener(FindBarExitListener listener) {
        this.exitListener = listener;
    }

    private static JButton makeNavButton(String text, String tooltip) {
        JButton btn = new JButton(text);
        btn.setFont(btn.getFont().deriveFont(Font.BOLD, 11f));
        btn.setMargin(new Insets(0, 2, 0, 2));
        btn.setFocusable(false);
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setOpaque(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setToolTipText(tooltip);
        return btn;
    }

    // ------------------------------------------------------------------ key bindings

    private void installArrowKeyBindings() {
        final JTextField tf = getTextField();
        InputMap im = tf.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap am = tf.getActionMap();

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "findbar.exitUp");
        am.put("findbar.exitUp", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                if (exitListener != null) {
                    exitListener.onExitUp();
                }
            }
        });

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "findbar.exitDown");
        am.put("findbar.exitDown", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                if (exitListener != null) {
                    exitListener.onExitDown();
                }
            }
        });

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), "findbar.prevOrCursor");
        am.put("findbar.prevOrCursor", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                if (arrowsVisible && prevListener != null) {
                    prevListener.actionPerformed(e);
                } else {
                    int pos = tf.getCaretPosition();
                    if (pos > 0) {
                        tf.setCaretPosition(pos - 1);
                    }
                }
            }
        });

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), "findbar.nextOrCursor");
        am.put("findbar.nextOrCursor", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                if (arrowsVisible && nextListener != null) {
                    nextListener.actionPerformed(e);
                } else {
                    int pos = tf.getCaretPosition();
                    if (pos < tf.getText().length()) {
                        tf.setCaretPosition(pos + 1);
                    }
                }
            }
        });

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "findbar.dismiss");
        am.put("findbar.dismiss", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                if (exitListener != null) {
                    exitListener.onDismiss();
                }
            }
        });
    }

    // ------------------------------------------------------------------ search wiring

    /**
     * Register a search action. In non-step mode the action fires on every Enter; in step mode it
     * fires on the first Enter and the bar then switches to ◀ ▶ match navigation.
     */
    @Override
    public void addSearchAction(final ActionListener listener) {
        searchListeners.add(listener);
        getTextField().addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (getText().trim().isEmpty()) {
                    if (exitListener != null) {
                        exitListener.onDismiss();
                    }
                    return;
                }
                fireSearch(e);
                showArrows();
            }
        });
    }

    public void setPrevAction(ActionListener listener) {
        this.prevListener = listener;
    }

    public void setNextAction(ActionListener listener) {
        this.nextListener = listener;
    }

    private void fireSearch(ActionEvent e) {
        for (ActionListener l : searchListeners) {
            l.actionPerformed(e);
        }
    }

    // ------------------------------------------------------------------ arrow/enter state

    /** Switch to ◀ ▶ navigation (only in step-search mode; callable externally on results). */
    public void showArrows() {
        if (!stepSearchEnabled || arrowsVisible) {
            return;
        }
        arrowsVisible = true;
        enterButton.setVisible(false);
        prevButton.setVisible(true);
        nextButton.setVisible(true);
        revalidate();
        repaint();
    }

    /** Reset to the ↵ button (hide the arrows), e.g. when the text is cleared. */
    public void resetToEnterButton() {
        arrowsVisible = false;
        enterButton.setVisible(true);
        prevButton.setVisible(false);
        nextButton.setVisible(false);
        revalidate();
        repaint();
    }

    public boolean isArrowsVisible() {
        return arrowsVisible;
    }

    // ------------------------------------------------------------------ step-search mode

    /** Enable or disable step-search (prev/next) mode. Default: {@code false}. */
    public void setStepSearchEnabled(boolean enabled) {
        this.stepSearchEnabled = enabled;
        if (!enabled) {
            resetToEnterButton();
        }
    }

    public boolean isStepSearchEnabled() {
        return stepSearchEnabled;
    }

    /** Register a callback for when the user toggles step-search via the context menu. */
    public void setStepSearchModeListener(StepSearchModeListener listener) {
        this.stepSearchModeListener = listener;
    }

    private void installContextMenu() {
        final JPopupMenu popup = new JPopupMenu();
        final JCheckBoxMenuItem stepItem = new JCheckBoxMenuItem("Step through matches");
        stepItem.setToolTipText("Enabled: arrow navigation from match to match. "
                + "Disabled: highlight all matches at once.");
        stepItem.setSelected(stepSearchEnabled);
        stepItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                stepSearchEnabled = stepItem.isSelected();
                if (!stepSearchEnabled) {
                    resetToEnterButton();
                }
                if (stepSearchModeListener != null) {
                    stepSearchModeListener.onStepSearchModeChanged(stepSearchEnabled);
                }
            }
        });
        popup.add(stepItem);

        MouseAdapter rightClickAdapter = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                showPopup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                showPopup(e);
            }

            private void showPopup(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    stepItem.setSelected(stepSearchEnabled);
                    popup.show(e.getComponent(), e.getX(), e.getY());
                }
            }
        };
        addMouseListener(rightClickAdapter);
        getTextField().addMouseListener(rightClickAdapter);
    }
}
