package com.aresstack.ollamamanager.swing;

import javax.swing.SwingUtilities;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Runs blocking work off the event dispatch thread and delivers results back on it.
 */
public final class BackgroundTasks implements AutoCloseable {

    private final ExecutorService executor;

    public BackgroundTasks() {
        this.executor = Executors.newCachedThreadPool(new DaemonThreadFactory());
    }

    /**
     * Run {@code work} in the background, then call {@code onSuccess} or {@code onFailure} on the EDT.
     */
    public <T> Future<?> run(Callable<T> work, Consumer<T> onSuccess, Consumer<Exception> onFailure) {
        return executor.submit(() -> {
            try {
                T result = work.call();
                SwingUtilities.invokeLater(() -> onSuccess.accept(result));
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> onFailure.accept(e));
            }
        });
    }

    /** Stop accepting work and wait briefly for running tasks. */
    @Override
    public void close() {
        executor.shutdownNow();
        try {
            executor.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static final class DaemonThreadFactory implements ThreadFactory {
        private final AtomicInteger counter = new AtomicInteger();

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "ollama-manager-worker-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
