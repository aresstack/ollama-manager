package com.aresstack.ollamamanager.application.setup;

import com.aresstack.ollamamanager.application.common.CancellationToken;
import com.aresstack.ollamamanager.application.common.ErrorKind;
import com.aresstack.ollamamanager.application.common.OperationException;
import com.aresstack.ollamamanager.application.hardware.GpuCompatibilityRules;
import com.aresstack.ollamamanager.application.hardware.PackageAdvisor;
import com.aresstack.ollamamanager.application.installation.PortableInstallationService;
import com.aresstack.ollamamanager.application.port.DistributionInstallerPort;
import com.aresstack.ollamamanager.application.port.HardwareProbePort;
import com.aresstack.ollamamanager.application.port.OllamaApiPort;
import com.aresstack.ollamamanager.application.port.OllamaRuntimePort;
import com.aresstack.ollamamanager.application.port.ReleaseCatalogPort;
import com.aresstack.ollamamanager.application.server.ServerLifecycleService;
import com.aresstack.ollamamanager.domain.server.Endpoint;
import com.aresstack.ollamamanager.domain.server.RuntimeOwnership;
import com.aresstack.ollamamanager.domain.server.ServerState;
import com.aresstack.ollamamanager.domain.server.ServerStatus;
import org.junit.Test;

import java.io.File;
import java.lang.reflect.Proxy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

public class SetupServiceTest {

    private OperationException versionFailure;
    private final OllamaApiPort api = stub(OllamaApiPort.class, () -> {
        if (versionFailure != null) {
            throw versionFailure;
        }
        return "0.12.1";
    });
    private final OllamaRuntimePort runtime = stub(OllamaRuntimePort.class, null);
    private final ServerLifecycleService lifecycle = new ServerLifecycleService(runtime, 0);
    private final SetupService setup = new SetupService(runtime, api, lifecycle,
        stub(HardwareProbePort.class, null), new PackageAdvisor(new GpuCompatibilityRules()),
        new PortableInstallationService(stub(ReleaseCatalogPort.class, null),
            stub(DistributionInstallerPort.class, null), new File("portable").getAbsoluteFile(),
            installation -> false));

    @Test
    public void detectRunningReportsNothingWhenNoServerAnswers() throws Exception {
        versionFailure = new OperationException(ErrorKind.CONNECTION_FAILED, "refused");

        assertNull(setup.detectRunning(Endpoint.DEFAULT, CancellationToken.none()));
    }

    @Test
    public void detectRunningPropagatesForeignServices() {
        versionFailure = new OperationException(ErrorKind.NOT_OLLAMA, "not ollama");
        try {
            setup.detectRunning(Endpoint.DEFAULT, CancellationToken.none());
            fail("a foreign service must not look like a free port");
        } catch (OperationException e) {
            assertEquals(ErrorKind.NOT_OLLAMA, e.kind());
        }
    }

    @Test
    public void attachExistingVerifiesThroughApiAndAttachesAsExternal() throws Exception {
        ServerStatus status = setup.attachExisting(Endpoint.DEFAULT, CancellationToken.none());

        assertEquals(ServerState.RUNNING, status.state());
        assertEquals(RuntimeOwnership.EXTERNAL, status.ownership());
        assertEquals("0.12.1", status.reportedVersion());
    }

    private interface Answer {
        Object get() throws OperationException;
    }

    /** Proxy whose methods all return {@code answer}, or fail if no answer is given. */
    private static <T> T stub(Class<T> type, Answer answer) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
            (proxy, method, args) -> {
                if (answer == null) {
                    throw new UnsupportedOperationException(method.getName());
                }
                return answer.get();
            }));
    }
}
