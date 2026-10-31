package com.aresstack.ollamamanager.infrastructure.hardware;

import com.aresstack.ollamamanager.application.common.CancellationToken;
import com.aresstack.ollamamanager.application.common.ErrorKind;
import com.aresstack.ollamamanager.application.common.OperationCancelledException;
import com.aresstack.ollamamanager.application.common.OperationException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * {@link CommandRunner} based on {@link ProcessBuilder}. Only destroys the process it started itself.
 */
public final class ProcessCommandRunner implements CommandRunner {

    private static final int MAX_OUTPUT_BYTES = 1024 * 1024;

    private final Charset charset;

    public ProcessCommandRunner(Charset charset) {
        this.charset = charset;
    }

    @Override
    public CommandResult run(List<String> command, long timeoutMillis, CancellationToken cancellation)
        throws OperationException {
        cancellation.throwIfCancelled();
        final Process process;
        try {
            process = new ProcessBuilder(command).redirectInput(ProcessBuilder.Redirect.from(nullDevice()))
                .start();
        } catch (IOException e) {
            // Tool absence (ENOENT, CreateProcess error=2/3) is expected and not an error.
            return CommandResult.notFound();
        }
        CancellationToken.Registration registration = cancellation.onCancel(new Runnable() {
            @Override
            public void run() {
                process.destroyForcibly();
            }
        });
        Drain stdout = Drain.start(process.getInputStream());
        Drain.start(process.getErrorStream());
        try {
            boolean exited = process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS);
            if (cancellation.isCancelled()) {
                process.destroyForcibly();
                throw new OperationCancelledException("command cancelled: " + command.get(0));
            }
            if (!exited) {
                process.destroyForcibly();
                throw new OperationException(ErrorKind.TIMEOUT,
                    "command timed out after " + timeoutMillis + " ms: " + command.get(0));
            }
            stdout.thread.join(1000);
            return CommandResult.completed(process.exitValue(), new String(stdout.bytes(), charset));
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new OperationCancelledException("interrupted: " + command.get(0));
        } finally {
            registration.close();
        }
    }

    private static File nullDevice() {
        return new File(File.separatorChar == '\\' ? "NUL" : "/dev/null");
    }

    private static final class Drain implements Runnable {

        private final InputStream in;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private Thread thread;

        private Drain(InputStream in) {
            this.in = in;
        }

        static Drain start(InputStream in) {
            Drain drain = new Drain(in);
            drain.thread = new Thread(drain, "hardware-probe-output");
            drain.thread.setDaemon(true);
            drain.thread.start();
            return drain;
        }

        @Override
        public void run() {
            byte[] chunk = new byte[8192];
            try {
                int read;
                while ((read = in.read(chunk)) != -1) {
                    synchronized (buffer) {
                        if (buffer.size() < MAX_OUTPUT_BYTES) {
                            buffer.write(chunk, 0, Math.min(read, MAX_OUTPUT_BYTES - buffer.size()));
                        }
                    }
                }
            } catch (IOException ignored) {
                // Stream closes when the process is destroyed.
            } finally {
                try {
                    in.close();
                } catch (IOException ignored) {
                    // Nothing left to release.
                }
            }
        }

        byte[] bytes() {
            synchronized (buffer) {
                return buffer.toByteArray();
            }
        }
    }
}
