package com.aresstack.ollamamanager.application.configuration;

import com.aresstack.ollamamanager.application.common.ErrorKind;
import com.aresstack.ollamamanager.application.common.OperationException;
import com.aresstack.ollamamanager.application.port.ConfigurationRepository;
import com.aresstack.ollamamanager.domain.configuration.ManagerConfiguration;
import com.aresstack.ollamamanager.domain.hardware.GpuFingerprint;
import com.aresstack.ollamamanager.domain.model.ModelIdentity;
import com.aresstack.ollamamanager.domain.model.ModelOptions;
import com.aresstack.ollamamanager.domain.profile.ModelProfile;
import com.aresstack.ollamamanager.domain.profile.ProfileScope;
import com.aresstack.ollamamanager.domain.server.ServerSettingCatalog;
import com.aresstack.ollamamanager.domain.server.ServerSettings;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class ConfigurationServiceTest {

    @Test
    public void concurrentUpdatesAreNotLost() throws Exception {
        SlowRepository repository = new SlowRepository();
        ConfigurationService service = new ConfigurationService(repository);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<Future<?>>();
        for (int i = 0; i < 40; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                return service.update(config -> config.withDesiredServerSettings(config.desiredServerSettings()
                    .with(ServerSettingCatalog.QUEUE, queueOf(config) + 1)));
            }));
        }
        start.countDown();
        for (Future<?> future : futures) {
            future.get();
        }
        pool.shutdown();

        assertEquals(40, queueOf(service.current()));
        assertEquals(40, repository.saves.get());
    }

    @Test
    public void servicesOnSameRepositoryShareTheLock() throws Exception {
        SlowRepository repository = new SlowRepository();
        ConfigurationService settingsService = new ConfigurationService(repository);
        ConfigurationService profileService = new ConfigurationService(repository);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<Future<?>>();
        for (int i = 0; i < 20; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                return settingsService.update(config -> config.withDesiredServerSettings(
                    config.desiredServerSettings().with(ServerSettingCatalog.QUEUE, queueOf(config) + 1)));
            }));
            int revision = i + 1;
            futures.add(pool.submit(() -> {
                start.await();
                return profileService.update(config -> config.withProfile(profile(revision)));
            }));
        }
        start.countDown();
        for (Future<?> future : futures) {
            future.get();
        }
        pool.shutdown();

        ManagerConfiguration result = settingsService.current();
        assertEquals(20, queueOf(result));
        assertEquals(1, result.profiles().size());
        assertEquals(40, repository.saves.get());
    }

    @Test
    public void checkedRejectionSavesNothing() throws Exception {
        SlowRepository repository = new SlowRepository();
        ConfigurationService service = new ConfigurationService(repository);

        try {
            service.update(config -> {
                throw new OperationException(ErrorKind.INVALID_STATE, "revision conflict");
            });
            fail("expected failure");
        } catch (OperationException expected) {
            assertEquals(ErrorKind.INVALID_STATE, expected.kind());
            assertEquals(0, repository.saves.get());
        }
    }

    @Test
    public void failingChangeSavesNothing() throws Exception {
        SlowRepository repository = new SlowRepository();
        ConfigurationService service = new ConfigurationService(repository);

        try {
            service.update(config -> {
                throw new IllegalStateException("rejected");
            });
            fail("expected failure");
        } catch (IllegalStateException expected) {
            assertEquals(0, repository.saves.get());
        }
    }

    private static ModelProfile profile(int revision) {
        return new ModelProfile("p1", "Chat", revision,
            new ProfileScope(ModelIdentity.named("a"), GpuFingerprint.CPU_ONLY, "0.12.0", "chat"),
            ServerSettings.defaults(), ModelOptions.none());
    }

    private static int queueOf(ManagerConfiguration config) {
        Integer queue = config.desiredServerSettings().queue();
        return queue == null ? 0 : queue;
    }

    /** Widen the read-modify-write window so lost updates would show up without the lock. */
    private static final class SlowRepository implements ConfigurationRepository {
        private volatile ManagerConfiguration stored = ManagerConfiguration.defaults();
        private final AtomicInteger saves = new AtomicInteger();

        @Override
        public ManagerConfiguration load() {
            ManagerConfiguration snapshot = stored;
            Thread.yield();
            return snapshot;
        }

        @Override
        public void save(ManagerConfiguration configuration) {
            Thread.yield();
            stored = configuration;
            saves.incrementAndGet();
        }
    }
}
