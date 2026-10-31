package com.aresstack.ollamamanager.infrastructure.runtime;

import java.io.IOException;

/**
 * Starts a configured process. Tests replace it with fake processes; production uses {@link ProcessBuilder#start()}.
 */
public interface ProcessStarter {

    Process start(ProcessBuilder builder) throws IOException;

    static ProcessStarter system() {
        return ProcessBuilder::start;
    }
}
