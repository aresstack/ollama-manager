package com.aresstack.ollamamanager.application.port;

import com.aresstack.ollamamanager.application.common.CancellationToken;
import com.aresstack.ollamamanager.application.common.OperationException;
import com.aresstack.ollamamanager.domain.hardware.GpuUsageSample;

import java.util.List;

/**
 * Reads live GPU usage, for example via {@code nvidia-smi}. Implemented in issue #5; the UI only sees this port.
 * Returns an empty list when no supported tool is available; that is not an error and not a claim of zero usage.
 */
public interface GpuTelemetryPort {

    List<GpuUsageSample> sample(CancellationToken cancellation) throws OperationException;
}
