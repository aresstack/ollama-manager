package com.aresstack.ollamamanager.domain.server;

import org.junit.Test;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ServerSettingsTest {

    @Test
    public void defaultsBindToLoopbackAndLeaveEverythingElseUnset() {
        Map<String, String> environment = ServerSettings.defaults().toEnvironment();

        assertEquals(Collections.singletonMap("OLLAMA_HOST", "127.0.0.1:11434"), environment);
        assertTrue(ServerSettings.defaults().bind().isLoopback());
        assertNull(ServerSettings.defaults().parallelism());
    }

    @Test
    public void mapsEverySettingToItsEnvironmentVariable() {
        ServerSettings settings = ServerSettings.defaults()
            .with(ServerSettingCatalog.PARALLELISM, 4)
            .with(ServerSettingCatalog.LOADED_MODELS, 2)
            .with(ServerSettingCatalog.QUEUE, 64)
            .with(ServerSettingCatalog.CONTEXT_LENGTH, 8192)
            .with(ServerSettingCatalog.KV_CACHE, KvCacheType.Q8_0)
            .with(ServerSettingCatalog.FLASH_ATTENTION, true)
            .with(ServerSettingCatalog.KEEP_ALIVE, "10m")
            .with(ServerSettingCatalog.MODEL_ROOT, new File("/models").getAbsoluteFile());

        Map<String, String> environment = settings.toEnvironment();

        assertEquals("4", environment.get("OLLAMA_NUM_PARALLEL"));
        assertEquals("2", environment.get("OLLAMA_MAX_LOADED_MODELS"));
        assertEquals("64", environment.get("OLLAMA_MAX_QUEUE"));
        assertEquals("8192", environment.get("OLLAMA_CONTEXT_LENGTH"));
        assertEquals("q8_0", environment.get("OLLAMA_KV_CACHE_TYPE"));
        assertEquals("1", environment.get("OLLAMA_FLASH_ATTENTION"));
        assertEquals("10m", environment.get("OLLAMA_KEEP_ALIVE"));
        assertEquals(new File("/models").getAbsolutePath(), environment.get("OLLAMA_MODELS"));
        assertEquals(9, environment.size());
    }

    @Test
    public void rejectsInvalidValues() {
        assertRejected(ServerSettingCatalog.PARALLELISM, 0);
        assertRejected(ServerSettingCatalog.PARALLELISM, 257);
        assertRejected(ServerSettingCatalog.KEEP_ALIVE, "five minutes");
        assertRejected(ServerSettingCatalog.MODEL_ROOT, new File("relative"));
    }

    @Test
    public void withReturnsCopyAndLeavesOriginalUnchanged() {
        ServerSettings original = ServerSettings.defaults();

        ServerSettings changed = original.with(ServerSettingCatalog.PARALLELISM, 8);

        assertNull(original.parallelism());
        assertEquals(Integer.valueOf(8), changed.parallelism());
    }

    @Test
    public void nullResetsToDefault() {
        ServerSettings settings = ServerSettings.defaults()
            .with(ServerSettingCatalog.BIND, new Endpoint("127.0.0.1", 11500))
            .with(ServerSettingCatalog.BIND, null);

        assertEquals(Endpoint.DEFAULT, settings.bind());
    }

    @Test
    public void reportsRestartRequiredDifferences() {
        ServerSettings applied = ServerSettings.defaults().with(ServerSettingCatalog.PARALLELISM, 2);
        ServerSettings desired = applied.with(ServerSettingCatalog.PARALLELISM, 4)
            .with(ServerSettingCatalog.KV_CACHE, KvCacheType.Q4_0);

        List<ServerSetting<?>> differences = desired.differencesTo(applied);

        assertEquals(2, differences.size());
        assertTrue(differences.contains(ServerSettingCatalog.PARALLELISM));
        assertTrue(differences.contains(ServerSettingCatalog.KV_CACHE));
        assertTrue(desired.requiresRestartComparedTo(applied));
        assertFalse(applied.requiresRestartComparedTo(applied));
    }

    @Test
    public void encodedMapRoundTrips() {
        ServerSettings settings = ServerSettings.defaults()
            .with(ServerSettingCatalog.PARALLELISM, 12)
            .with(ServerSettingCatalog.FLASH_ATTENTION, false)
            .with(ServerSettingCatalog.KV_CACHE, KvCacheType.F16);

        ServerSettings restored = ServerSettings.fromEncodedMap(settings.toEncodedMap());

        assertEquals(settings, restored);
        assertEquals(settings.toEnvironment(), restored.toEnvironment());
    }

    @Test
    public void equalSettingsHaveEqualHashCodes() {
        ServerSettings upper = ServerSettings.defaults().with(ServerSettingCatalog.BIND, Endpoint.parse("LOCALHOST:11434"));
        ServerSettings lower = ServerSettings.defaults().with(ServerSettingCatalog.BIND, Endpoint.parse("localhost:11434"));

        assertEquals(upper, lower);
        assertEquals(upper.hashCode(), lower.hashCode());
    }

    @Test
    public void decodingIgnoresUnknownIdsButRejectsInvalidValues() {
        Map<String, String> encoded = new LinkedHashMap<String, String>();
        encoded.put("futureSetting", "x");
        assertEquals(ServerSettings.defaults(), ServerSettings.fromEncodedMap(encoded));

        encoded.put("parallelism", "-3");
        try {
            ServerSettings.fromEncodedMap(encoded);
            fail("expected rejection");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("Parallel requests"));
        }
    }

    @Test
    public void everyCatalogEntryHasUserFacingMetadataAndRequiresRestart() {
        for (ServerSetting<?> setting : ServerSettingCatalog.all()) {
            assertFalse(setting.label().startsWith("OLLAMA_"));
            assertTrue(setting.environmentVariable().startsWith("OLLAMA_"));
            assertFalse(setting.tooltip().isEmpty());
            assertTrue(setting.isRestartRequired());
            assertEquals(setting, ServerSettingCatalog.byId(setting.id()));
        }
    }

    private static <T> void assertRejected(ServerSetting<T> setting, T value) {
        try {
            ServerSettings.defaults().with(setting, value);
            fail("expected " + value + " to be rejected for " + setting);
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().startsWith(setting.label()));
        }
    }
}
