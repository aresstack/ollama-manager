package com.aresstack.ollamamanager.infrastructure.catalog;

import com.aresstack.ollamamanager.application.catalog.CatalogModel;
import com.aresstack.ollamamanager.application.catalog.CatalogVariant;
import com.aresstack.ollamamanager.application.common.CancellationToken;
import com.aresstack.ollamamanager.infrastructure.distribution.FakeHttpServer;
import com.aresstack.ollamamanager.infrastructure.distribution.HttpTransport;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class HuggingFaceCatalogTest {

    private FakeHttpServer server;
    private HuggingFaceCatalog catalog;

    @Before
    public void setUp() throws Exception {
        server = new FakeHttpServer();
        catalog = new HuggingFaceCatalog(new HttpTransport(server.resolver(), 2000, 2000),
            HuggingFaceCatalog.DEFAULT_BASE);
    }

    @After
    public void tearDown() {
        server.close();
    }

    @Test
    public void searchListsGgufRepositories() throws Exception {
        server.text("/api/models", "[{\"id\":\"unsloth/Qwen3-8B-GGUF\",\"downloads\":1234567,"
            + "\"pipeline_tag\":\"text-generation\",\"lastModified\":\"2026-05-01T10:00:00.000Z\"},"
            + "{\"id\":\"bad id\"}]");

        List<CatalogModel> models = catalog.search("qwen3 8b", CancellationToken.none());

        assertEquals(1, models.size());
        assertEquals("unsloth/Qwen3-8B-GGUF", models.get(0).id());
        assertEquals("1.2M", models.get(0).popularity());
        assertEquals("2026-05-01", models.get(0).updated());
        assertTrue(models.get(0).capabilities().contains("text-generation"));
    }

    @Test
    public void variantsAreHfPullNamesPerQuantizationWithShardSizesSummed() throws Exception {
        server.text("/api/models/unsloth/Qwen3-8B-GGUF/tree/main", "["
            + file("README.md", 10)
            + "," + file("Qwen3-8B-Q4_K_M.gguf", 5_000_000_000L)
            + "," + file("Qwen3-8B-Q8_0.gguf", 8_700_000_000L)
            + "," + file("mmproj-F16.gguf", 900_000_000L)
            + "," + file("BF16/Qwen3-8B-BF16-00001-of-00002.gguf", 9_000_000_000L)
            + "," + file("BF16/Qwen3-8B-BF16-00002-of-00002.gguf", 7_400_000_000L)
            + "]");

        List<CatalogVariant> variants = catalog.variants("unsloth/Qwen3-8B-GGUF", CancellationToken.none());

        assertEquals(3, variants.size());
        assertEquals("hf.co/unsloth/Qwen3-8B-GGUF:Q4_K_M", variants.get(0).pullName());
        assertEquals("5.0GB", variants.get(0).size());
        assertTrue(variants.get(0).recommended());
        assertFalse(variants.get(1).recommended());
        assertEquals("hf.co/unsloth/Qwen3-8B-GGUF:BF16", variants.get(2).pullName());
        assertEquals("16.4GB", variants.get(2).size());
    }

    @Test
    public void quantizationFromFileNames() {
        assertEquals("IQ3_XXS", HuggingFaceCatalog.quantization("model.IQ3_XXS.gguf"));
        assertEquals("UD-Q4_K_XL", HuggingFaceCatalog.quantization("Qwen3-30B-A3B-UD-Q4_K_XL.gguf"));
        assertEquals("Q5_K_S", HuggingFaceCatalog.quantization("llama-3.2-3b-instruct-q5_k_s.gguf"));
        assertNull(HuggingFaceCatalog.quantization("mmproj-model-f16.gguf"));
        assertNull(HuggingFaceCatalog.quantization("model.safetensors"));
    }

    private static String file(String path, long size) {
        return "{\"type\":\"file\",\"path\":\"" + path + "\",\"size\":" + size + ",\"lfs\":{\"size\":" + size + "}}";
    }
}
