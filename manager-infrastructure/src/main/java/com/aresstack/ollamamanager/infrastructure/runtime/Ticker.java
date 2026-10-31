package com.aresstack.ollamamanager.infrastructure.runtime;

/**
 * Monotonic time and sleeping, replaceable in tests.
 */
public interface Ticker {

    long nowMillis();

    void sleep(long millis) throws InterruptedException;

    static Ticker system() {
        return new Ticker() {
            @Override
            public long nowMillis() {
                return System.nanoTime() / 1000000L;
            }

            @Override
            public void sleep(long millis) throws InterruptedException {
                Thread.sleep(millis);
            }
        };
    }
}
