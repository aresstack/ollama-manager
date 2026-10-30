package com.aresstack.ollamamanager.application.port;

import com.aresstack.ollamamanager.application.common.OperationException;
import com.aresstack.ollamamanager.domain.benchmark.BenchmarkResult;
import com.aresstack.ollamamanager.domain.profile.ProfileScope;

import java.util.List;

/**
 * Stores benchmark results with their exact scope. Implemented in issue #8.
 */
public interface BenchmarkMetricsPort {

    void record(BenchmarkResult result) throws OperationException;

    /** Return all results whose scope is still valid for {@code current}. */
    List<BenchmarkResult> findValid(ProfileScope current) throws OperationException;
}
