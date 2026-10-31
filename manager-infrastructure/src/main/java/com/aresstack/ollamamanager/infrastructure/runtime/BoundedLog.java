package com.aresstack.ollamamanager.infrastructure.runtime;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Thread-safe ring buffer of the most recent output lines. Overlong lines are truncated so memory stays bounded.
 */
final class BoundedLog {

    static final int DEFAULT_MAX_LINES = 500;
    static final int MAX_LINE_LENGTH = 2000;

    private final int maxLines;
    private final Deque<String> lines = new ArrayDeque<String>();
    private long dropped;

    BoundedLog(int maxLines) {
        if (maxLines < 1) {
            throw new IllegalArgumentException("maxLines must be positive: " + maxLines);
        }
        this.maxLines = maxLines;
    }

    synchronized void append(String line) {
        String bounded = line.length() > MAX_LINE_LENGTH ? line.substring(0, MAX_LINE_LENGTH) + " [...]" : line;
        if (lines.size() == maxLines) {
            lines.removeFirst();
            dropped++;
        }
        lines.addLast(bounded);
    }

    /** Return the retained lines, oldest first. */
    synchronized List<String> snapshot() {
        return new ArrayList<String>(lines);
    }

    synchronized long droppedLines() {
        return dropped;
    }

    /** Return the last lines joined for an error message. */
    synchronized String tail(int count) {
        List<String> all = new ArrayList<String>(lines);
        StringBuilder text = new StringBuilder();
        for (String line : all.subList(Math.max(0, all.size() - count), all.size())) {
            text.append(System.lineSeparator()).append(line);
        }
        return text.toString();
    }

    synchronized boolean containsIgnoreCase(String fragment) {
        String needle = fragment.toLowerCase(java.util.Locale.ROOT);
        for (String line : lines) {
            if (line.toLowerCase(java.util.Locale.ROOT).contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
