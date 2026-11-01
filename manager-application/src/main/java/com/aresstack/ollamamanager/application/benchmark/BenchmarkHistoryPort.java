package com.aresstack.ollamamanager.application.benchmark;

import com.aresstack.ollamamanager.application.common.OperationException;

import java.util.List;

/**
 * Stores finished benchmark reports so that runs can be compared later.
 */
public interface BenchmarkHistoryPort {

    void save(BenchmarkReport report) throws OperationException;

    /** Return all stored reports, newest first. Unreadable entries are skipped. */
    List<BenchmarkReport> list() throws OperationException;

    void delete(String id) throws OperationException;
}
