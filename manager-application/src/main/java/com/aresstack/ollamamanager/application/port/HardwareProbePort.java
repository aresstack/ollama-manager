package com.aresstack.ollamamanager.application.port;

import com.aresstack.ollamamanager.application.common.CancellationToken;
import com.aresstack.ollamamanager.application.common.OperationException;
import com.aresstack.ollamamanager.domain.hardware.HardwareSnapshot;

/**
 * Detects CPU, memory and GPUs without administrator rights. Implemented in issue #5.
 * Unknown values stay {@code null}; tool absence is not an error.
 */
public interface HardwareProbePort {

    HardwareSnapshot probe(CancellationToken cancellation) throws OperationException;
}
