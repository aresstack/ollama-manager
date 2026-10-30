package com.aresstack.ollamamanager.infrastructure.runtime;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Drains a process output stream on a daemon thread so the child never blocks on a full pipe.
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
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.append(line);
            }
        } catch (IOException e) {
            // Destroying the process closes the pipe; nothing remains to read.
        }
    }
}
