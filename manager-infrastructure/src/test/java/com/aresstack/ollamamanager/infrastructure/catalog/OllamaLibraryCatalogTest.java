package com.aresstack.ollamamanager.infrastructure.catalog;

import com.aresstack.ollamamanager.application.catalog.CatalogModel;
import com.aresstack.ollamamanager.application.catalog.CatalogVariant;
import com.aresstack.ollamamanager.application.common.CancellationToken;
import com.aresstack.ollamamanager.application.common.OperationException;
import com.aresstack.ollamamanager.infrastructure.distribution.FakeHttpServer;
import com.aresstack.ollamamanager.infrastructure.distribution.HttpTransport;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class OllamaLibraryCatalogTest {

    private FakeHttpServer server;
    private OllamaLibraryCatalog catalog;
    private final OllamaLibraryHtmlParser parser = new OllamaLibraryHtmlParser();

    @Before
    public void setUp() throws Exception {
        server = new FakeHttpServer();
        catalog = new OllamaLibraryCatalog(new HttpTransport(server.resolver(), 2000, 2000),
            OllamaLibraryCatalog.DEFAULT_BASE);
    }

    @After
    public void tearDown() {
        server.close();
    }

    @Test
    public void searchReturnsLibraryModelsWithCapabilitiesAndSizes() throws Exception {
        server.text("/search", fixture("search-devstral.html"));

        List<CatalogModel> models = catalog.search("devstral", CancellationToken.none());

        CatalogModel devstral = find(models, "devstral-small-2");
        assertNotNull(devstral);
        assertTrue(devstral.description().length() > 10);
        assertTrue(devstral.capabilities().contains("vision"));
        assertTrue(devstral.capabilities().contains("tools"));
        assertFalse(devstral.capabilities().contains("pulls"));
        assertTrue(devstral.sizes().contains("24b"));
        assertFalse(devstral.popularity().isEmpty());
    }

    @Test
    public void variantsAreDeduplicatedAndSkipCloudTags() throws Exception {
        server.text("/library/devstral-small-2/tags", fixture("library-devstral-small-2.html"));

        assertDevstral(catalog.variants("devstral-small-2", CancellationToken.none()));
    }

    @Test
    public void variantsFromCopyCommandPage() throws Exception {
        assertDevstral(parser.parseVariants("devstral-small-2", fixture("library-devstral-small-2-tags.html")));
    }

    @Test
    public void changedHtmlFailsLoudly() throws Exception {
        try {
            parser.parseSearchResults(fixture("changed.html"));
            fail("expected drift error");
        } catch (OperationException expected) {
            assertTrue(expected.getMessage().contains("changed"));
        }
    }

    @Test
    public void rejectsInvalidNamesBeforeAnyRequest() {
        try {
            catalog.variants("../etc", CancellationToken.none());
            fail("expected rejection");
        } catch (OperationException expected) {
            assertTrue(server.requests.isEmpty());
        }
    }

    private static void assertDevstral(List<CatalogVariant> variants) {
        assertEquals(2, variants.size());
        assertNotNull(variant(variants, "devstral-small-2:latest"));
        assertNull(variant(variants, "devstral-small-2:24b-cloud"));
        CatalogVariant b24 = variant(variants, "devstral-small-2:24b");
        assertNotNull(b24);
        assertEquals("15GB", b24.size());
        assertEquals("384K context window", b24.context());
        assertEquals(Arrays.asList("Text", "Image"), b24.inputs());
        assertTrue(b24.recommended());
        assertFalse(variant(variants, "devstral-small-2:latest").recommended());
    }

    private static CatalogModel find(List<CatalogModel> models, String id) {
        for (CatalogModel model : models) {
            if (model.id().equals(id)) {
                return model;
            }
        }
        return null;
    }

    private static CatalogVariant variant(List<CatalogVariant> variants, String pullName) {
        for (CatalogVariant variant : variants) {
            if (variant.pullName().equals(pullName)) {
                return variant;
            }
        }
        return null;
    }

    private String fixture(String name) throws IOException {
        InputStream in = getClass().getResourceAsStream("/catalog/ollama/" + name);
        assertNotNull("fixture missing: " + name, in);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) >= 0) {
                out.write(buffer, 0, read);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            in.close();
        }
    }
}
