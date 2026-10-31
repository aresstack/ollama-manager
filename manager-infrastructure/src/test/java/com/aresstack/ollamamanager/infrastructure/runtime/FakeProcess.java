package com.aresstack.ollamamanager.infrastructure.runtime;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Controllable process whose output pipe closes when it exits. */
class FakeProcess extends Process {

    private final PipedInputStream output = new PipedInputStream(64 * 1024);
    private final PipedOutputStream writer;
    private final CountDownLatch exited = new CountDownLatch(1);
    private final boolean ignoresGracefulStop;
    private volatile int exitCode;
    volatile int destroyCalls;
    volatile int forcedDestroyCalls;

    FakeProcess(boolean ignoresGracefulStop) {
        this.ignoresGracefulStop = ignoresGracefulStop;
        try {
            writer = new PipedOutputStream(output);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    void print(String line) {
        try {
            writer.write((line + "\n").getBytes(StandardCharsets.UTF_8));
            writer.flush();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    synchronized void exit(int code) {
        if (exited.getCount() == 0) {
            return;
        }
        exitCode = code;
        try {
            writer.close();
        } catch (IOException ignored) {
            // Already closed.
        }
        exited.countDown();
    }

    @Override
    public OutputStream getOutputStream() {
        return new ByteArrayOutputStream();
    }

    @Override
    public InputStream getInputStream() {
        return output;
    }

    @Override
    public InputStream getErrorStream() {
        return new ByteArrayInputStream(new byte[0]);
    }

    @Override
    public int waitFor() throws InterruptedException {
        exited.await();
        return exitCode;
    }

    @Override
    public boolean waitFor(long timeout, TimeUnit unit) throws InterruptedException {
        return exited.await(timeout, unit);
    }

    @Override
    public int exitValue() {
        if (exited.getCount() != 0) {
            throw new IllegalThreadStateException("running");
        }
        return exitCode;
    }

    @Override
    public boolean isAlive() {
        return exited.getCount() != 0;
    }

    @Override
    public void destroy() {
        destroyCalls++;
        if (!ignoresGracefulStop) {
            exit(143);
        }
    }

    @Override
    public Process destroyForcibly() {
        forcedDestroyCalls++;
        exit(137);
        return this;
    }
}
