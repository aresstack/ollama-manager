package com.aresstack.ollamamanager.swing.job;

import javax.swing.SwingUtilities;

/**
 * Delivers callbacks to the thread that owns the UI. Production code uses the EDT; tests run callbacks inline.
 */
@FunctionalInterface
public interface UiDispatcher {

    void dispatch(Runnable action);

    /** Dispatch onto the Swing event dispatch thread. */
    static UiDispatcher swing() {
        return action -> {
            if (SwingUtilities.isEventDispatchThread()) {
                action.run();
            } else {
                SwingUtilities.invokeLater(action);
            }
        };
    }

    /** Run callbacks on the calling thread; only for tests. */
    static UiDispatcher inline() {
        return Runnable::run;
    }
}
