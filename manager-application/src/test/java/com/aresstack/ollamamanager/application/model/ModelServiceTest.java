package com.aresstack.ollamamanager.application.model;

import com.aresstack.ollamamanager.application.common.CancellationToken;
import com.aresstack.ollamamanager.application.common.ErrorKind;
import com.aresstack.ollamamanager.application.common.OperationException;
import com.aresstack.ollamamanager.application.common.ProgressListener;
import com.aresstack.ollamamanager.application.common.TransferProgress;
import com.aresstack.ollamamanager.application.port.OllamaApiPort;
import com.aresstack.ollamamanager.domain.model.InstalledModel;
import com.aresstack.ollamamanager.domain.model.LoadedModel;
import com.aresstack.ollamamanager.domain.model.ModelDetails;
import com.aresstack.ollamamanager.domain.model.ModelIdentity;
import com.aresstack.ollamamanager.domain.model.ModelOptions;
import com.aresstack.ollamamanager.domain.server.Endpoint;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ModelServiceTest {

    private final FakeApi api = new FakeApi();
    private final ModelService service = new ModelService(api);

    @Test
    public void inventoryDistinguishesInstalledAndLoaded() throws Exception {
        api.installed = Arrays.asList(installed("llama3.1:8b", "aaa"), installed("qwen3:4b", "bbb"));
        api.loaded = Collections.singletonList(loaded("llama3.1:8b", "aaa", 100, 60));

        List<ModelEntry> entries = service.inventory(Endpoint.DEFAULT, CancellationToken.none());

        assertEquals(2, entries.size());
        assertTrue(entries.get(0).isLoaded());
        assertEquals(ModelPlacement.SPLIT, entries.get(0).placement());
        assertFalse(entries.get(1).isLoaded());
        assertNull(entries.get(1).placement());
    }

    @Test
    public void loadedModelWithOtherDigestIsSeparateEntry() throws Exception {
        api.installed = Collections.singletonList(installed("llama3.1:8b", "new"));
        api.loaded = Collections.singletonList(loaded("llama3.1:8b", "old", 100, 100));

        List<ModelEntry> entries = service.inventory(Endpoint.DEFAULT, CancellationToken.none());

        assertEquals(2, entries.size());
        assertFalse(entries.get(0).isLoaded());
        assertFalse(entries.get(1).isInstalled());
        assertEquals("old", entries.get(1).identity().digest());
    }

    @Test
    public void unknownDigestIsNeverMerged() throws Exception {
        api.installed = Arrays.asList(installed("llama3.1:8b", null), installed("qwen3:4b", "bbb"));
        api.loaded = Arrays.asList(loaded("llama3.1:8b", "aaa", 100, 100), loaded("qwen3:4b", null, 100, 100));

        List<ModelEntry> entries = service.inventory(Endpoint.DEFAULT, CancellationToken.none());

        assertEquals(4, entries.size());
        assertFalse(entries.get(0).isLoaded());
        assertFalse(entries.get(1).isLoaded());
        assertFalse(entries.get(2).isInstalled());
        assertFalse(entries.get(3).isInstalled());
    }

    @Test
    public void placementFollowsReportedBytesOnly() {
        assertEquals(ModelPlacement.GPU, ModelPlacement.of(loaded("a", "d", 100, 100)));
        assertEquals(ModelPlacement.SPLIT, ModelPlacement.of(loaded("a", "d", 100, 99)));
        assertEquals(ModelPlacement.CPU, ModelPlacement.of(loaded("a", "d", 100, 0)));
    }

    @Test
    public void preloadReturnsPlacementFromPs() throws Exception {
        api.loaded = Collections.singletonList(loaded("llama3.1:latest", "aaa", 100, 0));
        LoadedModel model = service.preloadAndVerify(Endpoint.DEFAULT, "llama3.1",
            new ModelOptions(4096, null, null, null), "10m", CancellationToken.none());
        assertEquals(ModelPlacement.CPU, ModelPlacement.of(model));
        assertEquals(Arrays.asList("preload llama3.1 10m"), api.calls);
    }

    @Test
    public void preloadFailsWhenModelIsNotReportedLoaded() throws Exception {
        try {
            service.preloadAndVerify(Endpoint.DEFAULT, "llama3.1", ModelOptions.none(), null,
                CancellationToken.none());
            fail();
        } catch (OperationException e) {
            assertEquals(ErrorKind.INVALID_STATE, e.kind());
        }
    }

    @Test
    public void deleteRequiresMatchingConfirmation() throws Exception {
        for (String confirmation : new String[] {null, "other:1b"}) {
            try {
                service.delete(Endpoint.DEFAULT, "llama3.1:8b", confirmation, CancellationToken.none());
                fail();
            } catch (OperationException e) {
                assertEquals(ErrorKind.INVALID_STATE, e.kind());
            }
        }
        assertTrue(api.calls.isEmpty());
        service.delete(Endpoint.DEFAULT, "llama3.1", "llama3.1:latest", CancellationToken.none());
        assertEquals(Arrays.asList("delete llama3.1:latest"), api.calls);
    }

    private static InstalledModel installed(String name, String digest) {
        return new InstalledModel(new ModelIdentity(name, digest, "Q4_K_M"), 100, "llama", "8B", null);
    }

    private static LoadedModel loaded(String name, String digest, long size, long vram) {
        return new LoadedModel(new ModelIdentity(name, digest, "Q4_K_M"), size, vram, 4096, null);
    }

    private static final class FakeApi implements OllamaApiPort {
        List<InstalledModel> installed = Collections.emptyList();
        List<LoadedModel> loaded = Collections.emptyList();
        final List<String> calls = new ArrayList<String>();

        @Override
        public String version(Endpoint endpoint, CancellationToken cancellation) {
            return "0.12.6";
        }

        @Override
        public List<InstalledModel> installedModels(Endpoint endpoint, CancellationToken cancellation) {
            return installed;
        }

        @Override
        public List<LoadedModel> loadedModels(Endpoint endpoint, CancellationToken cancellation) {
            return loaded;
        }

        @Override
        public ModelDetails show(Endpoint endpoint, String modelName, CancellationToken cancellation) {
            return new ModelDetails(modelName, null, null, null, null, null);
        }

        @Override
        public void pull(Endpoint endpoint, String modelName, ProgressListener<TransferProgress> listener,
                         CancellationToken cancellation) {
            calls.add("pull " + modelName);
        }

        @Override
        public void preload(Endpoint endpoint, String modelName, ModelOptions options, String keepAlive,
                            CancellationToken cancellation) {
            calls.add("preload " + modelName + " " + keepAlive);
        }

        @Override
        public void unload(Endpoint endpoint, String modelName, CancellationToken cancellation) {
            calls.add("unload " + modelName);
        }

        @Override
        public void delete(Endpoint endpoint, String modelName, CancellationToken cancellation) {
            calls.add("delete " + modelName);
        }
    }
}
