package com.aresstack.ollamamanager.swing.job;

/**
 * Blocking work that runs off the EDT. It reports progress and honours cancellation through its context.
 */
@FunctionalInterface
public interface JobWork<T> {

    T run(JobContext context) throws Exception;
}
