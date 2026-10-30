package com.aresstack.ollamamanager.application.hardware;

import com.aresstack.ollamamanager.application.common.CancellationToken;
import com.aresstack.ollamamanager.application.common.OperationException;

import java.util.List;

/**
 * Optional live GPU statistics. Returns an empty list when the vendor tool is missing or fails.
 * Kept in this package until the coordinator promotes it to {@code application.port}.
 */
public interface GpuStatisticsSource {

    List<GpuStatistics> read(CancellationToken cancellation) throws OperationException;
}
