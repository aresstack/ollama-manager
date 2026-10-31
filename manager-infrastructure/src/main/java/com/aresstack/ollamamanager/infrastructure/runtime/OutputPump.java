package com.aresstack.ollamamanager.infrastructure.runtime;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

/**
 * Drains a process output stream on a daemon thread so the child never blocks on a full pipe.
 * Characters beyond {@link BoundedLog#MAX_LINE_LENGTH} are drained but not kept, so a line without a newline
 * cannot exhaust memory.
 * The thread ends when the stream closes, which happens when the process exits or is destroyed.
 */
final class OutputPump implements Runnable {

    private final InputStream stream;
    private final BoundedLog log;

    private OutputPump(InputStream stream, BoundedLog log) {
        this.stream = stream;
        this.log = log;
    }

    static Thread start(InputStream stream, BoundedLog log, String name) {
        Thread thread = new Thread(new OutputPump(stream, log), name);
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    @Override
    public void run() {
        try (Reader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder line = new StringBuilder();
            boolean truncated = false;
            boolean afterCarriageReturn = false;
            int next;
            while ((next = reader.read()) != -1) {
                char c = (char) next;
                if (c == '\n' && afterCarriageReturn) {
                    afterCarriageReturn = false;
                    continue;
                }
                afterCarriageReturn = c == '\r';
                if (c == '\n' || c == '\r') {
                    log.append(truncated ? line + " [...]" : line.toString());
                    line.setLength(0);
                    truncated = false;
                } else if (line.length() < BoundedLog.MAX_LINE_LENGTH) {
                    line.append(c);
                } else {
                    truncated = true;
                }
            }
            if (line.length() > 0 || truncated) {
                log.append(truncated ? line + " [...]" : line.toString());
            }
        } catch (IOException e) {
            // Destroying the process closes the pipe; nothing remains to read.
        }
    }
}
