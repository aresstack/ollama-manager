package com.aresstack.ollamamanager.application.common;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CancellationTokenTest {

    @Test
    public void runsCallbacksOnceOnCancel() {
        CancellationToken token = new CancellationToken();
        AtomicInteger calls = new AtomicInteger();
        token.onCancel(calls::incrementAndGet);

        token.cancel();
        token.cancel();

        assertEquals(1, calls.get());
        assertTrue(token.isCancelled());
    }

    @Test
    public void runsRemainingCallbacksWhenOneFails() {
        CancellationToken token = new CancellationToken();
        AtomicInteger calls = new AtomicInteger();
        token.onCancel(() -> {
            throw new IllegalStateException("close failed");
        });
        token.onCancel(calls::incrementAndGet);

        try {
            token.cancel();
            throw new AssertionError("expected failure");
        } catch (IllegalStateException expected) {
            assertEquals(1, calls.get());
        }
    }

    @Test
    public void toleratesSameExceptionInstanceFromSeveralCallbacks() {
        CancellationToken token = new CancellationToken();
        IllegalStateException shared = new IllegalStateException("shared");
        AtomicInteger calls = new AtomicInteger();
        token.onCancel(() -> {
            throw shared;
        });
        token.onCancel(() -> {
            throw shared;
        });
        token.onCancel(calls::incrementAndGet);

        try {
            token.cancel();
            throw new AssertionError("expected failure");
        } catch (IllegalStateException expected) {
            assertEquals(shared, expected);
            assertEquals(1, calls.get());
        }
    }

    @Test
    public void runsLateCallbackImmediately() {
        CancellationToken token = new CancellationToken();
        token.cancel();
        AtomicInteger calls = new AtomicInteger();

        token.onCancel(calls::incrementAndGet);

        assertEquals(1, calls.get());
    }

    @Test
    public void throwsCancelledKind() {
        CancellationToken token = new CancellationToken();
        token.cancel();
        try {
            token.throwIfCancelled();
        } catch (OperationCancelledException e) {
            assertEquals(ErrorKind.CANCELLED, e.kind());
            return;
        }
        throw new AssertionError("expected cancellation");
    }

    @Test
    public void transferFractionIsUnknownWithoutTotal() {
        assertEquals(-1.0, new TransferProgress("downloading", 5, -1).fraction(), 0.0);
        assertEquals(0.5, new TransferProgress("downloading", 5, 10).fraction(), 0.0);
    }
}
