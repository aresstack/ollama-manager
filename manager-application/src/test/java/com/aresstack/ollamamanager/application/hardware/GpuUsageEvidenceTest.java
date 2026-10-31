package com.aresstack.ollamamanager.application.hardware;

import com.aresstack.ollamamanager.domain.model.LoadedModel;
import com.aresstack.ollamamanager.domain.model.ModelIdentity;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

public class GpuUsageEvidenceTest {

    private static LoadedModel loaded(long size, long vram) {
        return new LoadedModel(new ModelIdentity("llama3:8b", "sha256:abc", "Q4_K_M"), size, vram, 8192, null);
    }

    @Test
    public void derivesEvidenceOnlyFromReportedVram() {
        assertEquals(GpuUsageEvidence.NO_MODEL_LOADED,
            GpuUsageEvidence.fromLoadedModels(Collections.<LoadedModel>emptyList()));
        assertEquals(GpuUsageEvidence.CPU_ONLY, GpuUsageEvidence.fromLoadedModels(Arrays.asList(loaded(100, 0))));
        assertEquals(GpuUsageEvidence.PARTIAL_OFFLOAD,
            GpuUsageEvidence.fromLoadedModels(Arrays.asList(loaded(100, 60))));
        assertEquals(GpuUsageEvidence.FULL_GPU,
            GpuUsageEvidence.fromLoadedModels(Arrays.asList(loaded(100, 100), loaded(50, 50))));
    }
}
