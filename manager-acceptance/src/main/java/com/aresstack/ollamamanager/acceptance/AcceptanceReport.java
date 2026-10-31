package com.aresstack.ollamamanager.acceptance;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Evidence log of one acceptance run: every line goes to stdout and to {@code report.txt} in the work directory.
 * A check is recorded as PASS, FAIL or SKIP; a skipped check never counts as passed.
 */
final class AcceptanceReport implements AutoCloseable {

    private final PrintWriter file;
    private int passed;
    private int failed;
    private int skipped;

    AcceptanceReport(File reportFile) throws IOException {
        this.file = new PrintWriter(new OutputStreamWriter(new FileOutputStream(reportFile), StandardCharsets.UTF_8), true);
    }

    synchronized void info(String message) {
        line("INFO", message);
    }

    synchronized void pass(String check, String evidence) {
        passed++;
        line("PASS", check + " | " + evidence);
    }

    synchronized void fail(String check, String evidence) {
        failed++;
        line("FAIL", check + " | " + evidence);
    }

    synchronized void skip(String check, String reason) {
        skipped++;
        line("SKIP", check + " | " + reason);
    }

    synchronized void check(boolean condition, String check, String evidence) {
        if (condition) {
            pass(check, evidence);
        } else {
            fail(check, evidence);
        }
    }

    synchronized int failed() {
        return failed;
    }

    synchronized String summary() {
        return "passed=" + passed + " failed=" + failed + " skipped=" + skipped;
    }

    private void line(String level, String message) {
        String text = new SimpleDateFormat("HH:mm:ss.SSS").format(new Date()) + " " + level + " " + message;
        System.out.println(text);
        file.println(text);
    }

    @Override
    public synchronized void close() {
        file.close();
    }
}
