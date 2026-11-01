package com.aresstack.ollamamanager.application.configuration;

import com.aresstack.ollamamanager.application.common.CancellationToken;
import com.aresstack.ollamamanager.application.port.ConfigurationRepository;
import com.aresstack.ollamamanager.application.port.InstalledEnvironmentPort;
import com.aresstack.ollamamanager.domain.configuration.ManagerConfiguration;
import com.aresstack.ollamamanager.domain.server.Endpoint;
import com.aresstack.ollamamanager.domain.server.ServerSetting;
import com.aresstack.ollamamanager.domain.server.ServerSettingCatalog;
import com.aresstack.ollamamanager.domain.server.ServerSettings;
import org.junit.Test;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ExistingSettingsImportTest {

    private final Map<String, String> variables = new LinkedHashMap<String, String>();
    private final MemoryRepository repository = new MemoryRepository();
    private final ConfigurationService configuration = new ConfigurationService(repository);
    private final ExistingSettingsImport existing = new ExistingSettingsImport(configuration,
        new InstalledEnvironmentPort() {
            @Override
            public Map<String, String> ollamaVariables(CancellationToken cancellation) {
                return variables;
            }

            @Override
            public String sourceDescription() {
                return "test environment";
            }
        });

    @Test
    public void takesOverTheModelFolderAsAnExplicitChoice() throws Exception {
        File models = new File("X:\\ollama\\models").getAbsoluteFile();
        variables.put("ollama_models", models.getPath());

        List<ServerSetting<?>> imported = existing.importUnchosen(CancellationToken.none());

        ServerSettings stored = configuration.current().desiredServerSettings();
        assertEquals(1, imported.size());
        assertTrue(stored.isChosen(ServerSettingCatalog.MODEL_ROOT));
        assertEquals(models, stored.modelRoot());
        assertEquals(models.getPath(), stored.toEnvironment().get("OLLAMA_MODELS"));
        assertTrue(existing.detect(CancellationToken.none()).matches(stored, ServerSettingCatalog.MODEL_ROOT));
    }

    @Test
    public void neverOverwritesAChoiceMadeInTheManager() throws Exception {
        configuration.update(current -> current.withDesiredServerSettings(current.desiredServerSettings()
            .with(ServerSettingCatalog.PARALLELISM, 4)));
        variables.put("OLLAMA_NUM_PARALLEL", "8");
        variables.put("OLLAMA_FLASH_ATTENTION", "1");
        int saves = repository.saves;

        List<ServerSetting<?>> imported = existing.importUnchosen(CancellationToken.none());

        ServerSettings stored = configuration.current().desiredServerSettings();
        assertEquals(Integer.valueOf(4), stored.parallelism());
        assertEquals(Boolean.TRUE, stored.flashAttention());
        assertEquals(1, imported.size());
        assertEquals(saves + 1, repository.saves);

        assertTrue(existing.importUnchosen(CancellationToken.none()).isEmpty());
        assertEquals(saves + 1, repository.saves);
    }

    @Test
    public void acceptsOllamaHostFormsAndSkipsMalformedValues() throws Exception {
        variables.put("OLLAMA_HOST", "0.0.0.0");
        variables.put("OLLAMA_KEEP_ALIVE", "forever");
        variables.put("OLLAMA_ORIGINS", "*");

        ExistingSettingsImport.Detected detected = existing.detect(CancellationToken.none());

        assertEquals(new Endpoint("0.0.0.0", 11434), detected.settings().bind());
        assertFalse(detected.values().containsKey(ServerSettingCatalog.KEEP_ALIVE));
        assertTrue(detected.rejected().containsKey("OLLAMA_KEEP_ALIVE"));
        assertEquals(1, detected.values().size());
    }

    @Test
    public void writesNothingWithoutConfiguredVariables() throws Exception {
        assertTrue(existing.importUnchosen(CancellationToken.none()).isEmpty());
        assertEquals(0, repository.saves);
    }

    private static final class MemoryRepository implements ConfigurationRepository {
        private ManagerConfiguration stored = ManagerConfiguration.defaults();
        int saves;

        @Override
        public ManagerConfiguration load() {
            return stored;
        }

        @Override
        public void save(ManagerConfiguration configuration) {
            stored = configuration;
            saves++;
        }
    }
}
