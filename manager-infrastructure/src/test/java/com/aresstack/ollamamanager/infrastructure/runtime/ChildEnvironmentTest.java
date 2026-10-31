package com.aresstack.ollamamanager.infrastructure.runtime;

import com.aresstack.ollamamanager.domain.server.ServerSettingCatalog;
import com.aresstack.ollamamanager.domain.server.ServerSettings;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class ChildEnvironmentTest {

    @Test
    public void removesInheritedManagedVariablesInAnySpelling() {
        Map<String, String> environment = new HashMap<String, String>();
        environment.put("Path", "C:\\Windows");
        environment.put("ollama_host", "0.0.0.0:1");
        environment.put("Ollama_Num_Parallel", "8");
        environment.put("OLLAMA_KV_CACHE_TYPE", "q4_0");
        environment.put("OLLAMA_DEBUG", "1");

        ChildEnvironment.apply(environment, ServerSettings.defaults().with(ServerSettingCatalog.PARALLELISM, 2));

        assertEquals("C:\\Windows", environment.get("Path"));
        assertEquals("1", environment.get("OLLAMA_DEBUG"));
        assertEquals("127.0.0.1:11434", environment.get("OLLAMA_HOST"));
        assertEquals("2", environment.get("OLLAMA_NUM_PARALLEL"));
        assertFalse(environment.containsKey("ollama_host"));
        assertFalse(environment.containsKey("Ollama_Num_Parallel"));
        assertFalse(environment.containsKey("OLLAMA_KV_CACHE_TYPE"));
    }
}
