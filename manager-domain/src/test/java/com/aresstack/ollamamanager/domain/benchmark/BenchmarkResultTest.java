package com.aresstack.ollamamanager.domain.benchmark;

import com.aresstack.ollamamanager.domain.hardware.GpuFingerprint;
import com.aresstack.ollamamanager.domain.model.ModelIdentity;
import com.aresstack.ollamamanager.domain.profile.ProfileScope;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class BenchmarkResultTest {

    private static final ProfileScope SCOPE = new ProfileScope(ModelIdentity.named("a"), GpuFingerprint.CPU_ONLY,
        "0.12.0", "chat");

    @Test
    public void computesWallClockThroughput() {
        BenchmarkResult result = new BenchmarkResult(SCOPE, 4, 8, 0, 2000, 4000, 900, 1500, 0);

        assertEquals(500.0, result.throughputTokensPerSecond(), 0.0001);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsMoreFailuresThanRequests() {
        new BenchmarkResult(SCOPE, 4, 8, 9, 0, 1, 0, 0, 0);
    }
}
