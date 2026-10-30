package com.aresstack.ollamamanager.domain.model;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ModelValuesTest {

    @Test
    public void addsLatestTagWhenMissing() {
        assertEquals("gemma3:latest", ModelIdentity.named("gemma3").name());
    }

    @Test
    public void sameArtifactRequiresBothDigestsAndEquality() {
        ModelIdentity known = new ModelIdentity("qwen3:8b", "sha256:1", "Q4_K_M");

        assertTrue(known.sameArtifactAs(new ModelIdentity("qwen3:8b", "sha256:1", "Q4_K_M")));
        assertFalse(known.sameArtifactAs(ModelIdentity.named("qwen3:8b")));
        assertFalse(ModelIdentity.named("qwen3:8b").sameArtifactAs(known));
        assertFalse(ModelIdentity.named("qwen3:8b").sameArtifactAs(ModelIdentity.named("qwen3:8b")));
        assertFalse(known.sameArtifactAs(new ModelIdentity("qwen3:8b", "sha256:2", "Q4_K_M")));
        assertFalse(known.sameArtifactAs(new ModelIdentity("qwen3:8b", "sha256:1", "Q8_0")));
    }

    @Test
    public void latestTagWithChangedDigestIsDifferentArtifact() {
        ModelIdentity before = new ModelIdentity("gemma3", "sha256:old", null);
        ModelIdentity after = new ModelIdentity("gemma3:latest", "sha256:new", null);

        assertFalse(before.sameArtifactAs(after));
    }

    @Test
    public void requestOptionsUseOllamaNames() {
        Map<String, Object> options = new ModelOptions(4096, 128, 0.0, 42).toRequestOptions();

        assertEquals(4096, options.get("num_ctx"));
        assertEquals(128, options.get("num_predict"));
        assertEquals(42, options.get("seed"));
        assertTrue(ModelOptions.none().toRequestOptions().isEmpty());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void requestOptionsAreImmutable() {
        new ModelOptions(4096, null, null, null).toRequestOptions().put("num_ctx", 1);
    }

    @Test
    public void reportsGpuShareAsGiven() {
        LoadedModel model = new LoadedModel(ModelIdentity.named("a"), 1000, 250, 4096, null);

        assertEquals(0.25, model.gpuShare(), 0.0001);
    }
}
