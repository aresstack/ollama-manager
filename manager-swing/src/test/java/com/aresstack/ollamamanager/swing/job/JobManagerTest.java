package com.aresstack.ollamamanager.swing.job;

import com.aresstack.ollamamanager.application.common.ErrorKind;
import com.aresstack.ollamamanager.application.common.OperationException;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class JobManagerTest {

    private final JobManager inline = new JobManager(Runnable::run, UiDispatcher.inline());

    @Test
    public void successIsReportedOnlyAfterWorkReturns() {
        List<String> events = new ArrayList<String>();
        Job job = inline.submit("work", true, context -> {
            events.add("work");
            return "result";
        }, result -> events.add("success:" + result), error -> events.add("failure"));

        assertEquals(JobState.SUCCEEDED, job.state());
        assertEquals("[work, success:result]", events.toString());
    }

    @Test
    public void failureCarriesUniformErrorText() {
        AtomicReference<Exception> seen = new AtomicReference<Exception>();
        Job job = inline.submit("work", true, context -> {
            throw new OperationException(ErrorKind.PORT_IN_USE, "127.0.0.1:11435");
        }, result -> { }, seen::set);

        assertEquals(JobState.FAILED, job.state());
        assertTrue(job.errorMessage().startsWith("The port is already in use."));
        assertEquals(ErrorKind.PORT_IN_USE, ((OperationException) seen.get()).kind());
    }

    @Test
    public void cancelledJobReportsNeitherSuccessNorError() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            JobManager manager = new JobManager(executor, UiDispatcher.inline());
            CountDownLatch started = new CountDownLatch(1);
            List<String> events = new ArrayList<String>();
            Job job = manager.submit("download", true, context -> {
                started.countDown();
                while (true) {
                    context.cancellation().throwIfCancelled();
                    Thread.sleep(5);
                }
            }, result -> events.add("success"), error -> events.add("failure"));
            assertTrue(started.await(5, TimeUnit.SECONDS));

            job.cancel();
            assertEquals(JobState.CANCELLING, job.state());
            assertFalse(job.isCancellable());
            executor.shutdown();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

            assertEquals(JobState.CANCELLED, job.state());
            assertNull(job.errorMessage());
            assertTrue(events.isEmpty());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void progressIsClampedAndIgnoredAfterFinish() {
        AtomicReference<JobContext> holder = new AtomicReference<JobContext>();
        Job job = inline.submit("pull", true, context -> {
            holder.set(context);
            context.progress("downloading", 1.7);
            return null;
        }, result -> { }, error -> { });

        assertEquals(1d, job.fraction(), 0d);
        holder.get().progress("late", 0.2);
        assertEquals(JobState.SUCCEEDED, job.state());
        assertEquals(1d, job.fraction(), 0d);
    }

    @Test
    public void nonCancellableJobIgnoresCancel() {
        JobManager manager = new JobManager(command -> { }, UiDispatcher.inline());
        Job job = manager.submit("stop", false, context -> null, r -> { }, e -> { });
        job.cancel();
        assertEquals(JobState.RUNNING, job.state());
        assertEquals(1, manager.activeCount());
    }
}
