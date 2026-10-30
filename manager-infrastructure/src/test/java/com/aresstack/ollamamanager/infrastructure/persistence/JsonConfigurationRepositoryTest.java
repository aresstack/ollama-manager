package com.aresstack.ollamamanager.infrastructure.persistence;

import com.aresstack.ollamamanager.application.common.ErrorKind;
import com.aresstack.ollamamanager.application.common.OperationException;
import com.aresstack.ollamamanager.domain.configuration.ManagerConfiguration;
import com.aresstack.ollamamanager.domain.hardware.GpuFingerprint;
import com.aresstack.ollamamanager.domain.model.ModelIdentity;
import com.aresstack.ollamamanager.domain.model.ModelOptions;
import com.aresstack.ollamamanager.domain.profile.ModelProfile;
import com.aresstack.ollamamanager.domain.profile.ProfileScope;
import com.aresstack.ollamamanager.domain.server.InstallationKind;
import com.aresstack.ollamamanager.domain.server.KvCacheType;
import com.aresstack.ollamamanager.domain.server.ServerSettingCatalog;
import com.aresstack.ollamamanager.domain.server.ServerSettings;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class JsonConfigurationRepositoryTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void returnsDefaultsWhenNothingStored() throws Exception {
        assertEquals(ServerSettings.defaults(), repository().load().desiredServerSettings());
    }

    @Test
    public void roundTripsCompleteConfiguration() throws Exception {
        ManagerConfiguration configuration = sample();

        repository().save(configuration);
        ManagerConfiguration restored = repository().load();

        assertEquals(configuration.desiredServerSettings(), restored.desiredServerSettings());
        assertEquals(InstallationKind.MANAGED_PORTABLE, restored.selectedInstallationKind());
        assertEquals(configuration.selectedBinaryDirectory(), restored.selectedBinaryDirectory());
        assertEquals("p1", restored.activeProfileId());
        ModelProfile profile = restored.findProfile("p1");
        assertEquals(3, profile.revision());
        assertEquals(configuration.findProfile("p1").scope(), profile.scope());
        assertEquals(configuration.findProfile("p1").modelOptions(), profile.modelOptions());
        assertEquals(configuration.findProfile("p1").serverSettings(), profile.serverSettings());
    }

    @Test
    public void writesFormatVersionOne() throws Exception {
        repository().save(sample());

        assertTrue(read(configFile()).contains("\"formatVersion\": 1"));
    }

    @Test
    public void recoversFromBackupWhenPrimaryIsDamaged() throws Exception {
        repository().save(sample());
        repository().save(sample().withActiveProfileId(null));
        Files.write(configFile().toPath(), "{\"formatVersion\": 1, \"serverSett".getBytes(StandardCharsets.UTF_8));

        ManagerConfiguration recovered = repository().load();

        assertEquals("p1", recovered.activeProfileId());
    }

    @Test
    public void keepsValidBackupWhenSavingOverDamagedPrimary() throws Exception {
        repository().save(sample());
        repository().save(sample());
        Files.write(configFile().toPath(), "{broken".getBytes(StandardCharsets.UTF_8));

        repository().save(sample().withActiveProfileId(null));

        assertEquals("p1", ConfigurationDocumentMapper.fromDocument(
            com.aresstack.ollamamanager.infrastructure.json.Json.parse(read(
                new File(configFile().getPath() + ".bak")))).activeProfileId());
    }

    @Test
    public void rejectsUnsupportedFormatVersionWithoutBackup() throws Exception {
        Files.write(configFile().toPath(), "{\"formatVersion\": 2}".getBytes(StandardCharsets.UTF_8));
        try {
            repository().load();
            fail("expected failure");
        } catch (OperationException e) {
            assertEquals(ErrorKind.INVALID_CONFIGURATION, e.kind());
        }
    }

    @Test
    public void leavesNoTemporaryFiles() throws Exception {
        repository().save(sample());
        repository().save(sample());

        String[] names = folder.getRoot().list();
        assertEquals(2, names.length);
    }

    private JsonConfigurationRepository repository() {
        return new JsonConfigurationRepository(folder.getRoot());
    }

    private File configFile() {
        return new File(folder.getRoot(), JsonConfigurationRepository.FILE_NAME);
    }

    private static String read(File file) throws Exception {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    private static ManagerConfiguration sample() {
        ServerSettings settings = ServerSettings.defaults()
            .with(ServerSettingCatalog.PARALLELISM, 4)
            .with(ServerSettingCatalog.KV_CACHE, KvCacheType.Q8_0)
            .with(ServerSettingCatalog.FLASH_ATTENTION, true);
        ModelProfile profile = new ModelProfile("p1", "Batch \"summaries\"", 3,
            new ProfileScope(new ModelIdentity("llama3.1:8b", "sha256:abc", "Q4_K_M"),
                new GpuFingerprint("NVIDIA|RTX 5080|17179869184|581.57"), "0.12.6", "batch"),
            settings.with(ServerSettingCatalog.PARALLELISM, 12), new ModelOptions(8192, 256, 0.2, 7));
        return new ManagerConfiguration(settings, InstallationKind.MANAGED_PORTABLE,
            new File("portable", "v0.12.6").getAbsoluteFile(), java.util.Collections.singletonList(profile), "p1");
    }
}
