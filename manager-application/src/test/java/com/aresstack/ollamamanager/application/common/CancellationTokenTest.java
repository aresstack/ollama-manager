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
    public void closedRegistrationIsNotRunAndReleased() {
        CancellationToken token = new CancellationToken();
        AtomicInteger calls = new AtomicInteger();

        try (CancellationToken.Registration ignored = token.onCancel(calls::incrementAndGet)) {
            assertEquals(1, token.registeredCallbackCount());
        }
        token.cancel();

        assertEquals(0, token.registeredCallbackCount());
        assertEquals(0, calls.get());
    }

    @Test
    public void reusedTokenDoesNotAccumulateCallbacks() {
        CancellationToken token = new CancellationToken();
        Runnable callback = () -> { };

        for (int i = 0; i < 1000; i++) {
            CancellationToken.Registration registration = token.onCancel(callback);
            registration.close();
        }

        assertEquals(0, token.registeredCallbackCount());
    }

    @Test
    public void closingOneRegistrationKeepsOthersOfSameCallback() {
        CancellationToken token = new CancellationToken();
        AtomicInteger calls = new AtomicInteger();
        Runnable callback = calls::incrementAndGet;
        CancellationToken.Registration first = token.onCancel(callback);
        token.onCancel(callback);

        first.close();
        first.close();
        token.cancel();

        assertEquals(1, calls.get());
    }

    @Test
    public void closingAfterCancelHasNoEffect() {
        CancellationToken token = new CancellationToken();
        CancellationToken.Registration registration = token.onCancel(() -> { });
        token.cancel();

        registration.close();

        assertEquals(0, token.registeredCallbackCount());
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
