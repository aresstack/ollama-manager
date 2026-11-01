package com.aresstack.ollamamanager.acceptance;

import com.aresstack.ollamamanager.application.common.CancellationToken;
import com.aresstack.ollamamanager.application.common.ErrorKind;
import com.aresstack.ollamamanager.application.common.OperationCancelledException;
import com.aresstack.ollamamanager.application.common.OperationException;
import com.aresstack.ollamamanager.application.common.ProgressListener;
import com.aresstack.ollamamanager.application.common.TransferProgress;
import com.aresstack.ollamamanager.application.hardware.GpuCompatibilityRules;
import com.aresstack.ollamamanager.application.hardware.GpuUsageEvidence;
import com.aresstack.ollamamanager.application.hardware.PackageAdvisor;
import com.aresstack.ollamamanager.application.hardware.PackageRecommendation;
import com.aresstack.ollamamanager.application.hardware.SupportOverrides;
import com.aresstack.ollamamanager.application.installation.PortableInstallationService;
import com.aresstack.ollamamanager.application.model.ModelService;
import com.aresstack.ollamamanager.application.port.ManagedServer;
import com.aresstack.ollamamanager.domain.distribution.DistributionPackage;
import com.aresstack.ollamamanager.domain.hardware.GpuDescriptor;
import com.aresstack.ollamamanager.domain.hardware.GpuVendor;
import com.aresstack.ollamamanager.domain.hardware.HardwareSnapshot;
import com.aresstack.ollamamanager.domain.model.InstalledModel;
import com.aresstack.ollamamanager.domain.model.LoadedModel;
import com.aresstack.ollamamanager.domain.model.ModelOptions;
import com.aresstack.ollamamanager.domain.server.Endpoint;
import com.aresstack.ollamamanager.domain.server.OllamaInstallation;
import com.aresstack.ollamamanager.domain.server.ServerSettingCatalog;
import com.aresstack.ollamamanager.domain.server.ServerSettings;
import com.aresstack.ollamamanager.infrastructure.distribution.GitHubReleaseCatalog;
import com.aresstack.ollamamanager.infrastructure.distribution.PortableDistributionInstaller;
import com.aresstack.ollamamanager.infrastructure.hardware.CommandResult;
import com.aresstack.ollamamanager.infrastructure.hardware.ProcessCommandRunner;
import com.aresstack.ollamamanager.infrastructure.hardware.SystemHardwareProbe;
import com.aresstack.ollamamanager.infrastructure.ollamaapi.HttpOllamaApiClient;
import com.aresstack.ollamamanager.infrastructure.runtime.ProcessOllamaRuntime;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Native acceptance run for issue #16. Started only by the {@code nativeAcceptance} Gradle task.
 * <p>
 * The reference server (default {@code 127.0.0.1:11434}) is only read: version, tags and ps. Every write goes to
 * a test server this run starts itself on another port with {@code OLLAMA_MODELS} below a fresh work directory,
 * and passes {@link AcceptanceGuard} first.
 */
public final class NativeAcceptanceMain {

    static final List<String> ALL_STAGES = Arrays.asList("readonly", "hardware", "install", "process", "models");
    private static final long GRACE_MILLIS = 10000;

    private final AcceptanceReport report;
    private final AcceptanceGuard guard;
    private final Set<String> stages;
    private final Endpoint reference;
    private final Endpoint testEndpoint;
    private final String testModel;
    private final long minFreeBytes;
    private final HttpOllamaApiClient api = new HttpOllamaApiClient();
    private final ModelService models = new ModelService(api);
    private final ProcessOllamaRuntime runtime;
    private final GuardedRuntime starter;
    private final File installRoot;
    private final File modelRoot;
    private OllamaInstallation binary;
    private ManagedServer testServer;

    private NativeAcceptanceMain(AcceptanceReport report, AcceptanceGuard guard, Set<String> stages, int referencePort,
                                 int testPort, String testModel, long minFreeBytes) {
        this.report = report;
        this.guard = guard;
        this.stages = stages;
        this.reference = new Endpoint("127.0.0.1", referencePort);
        this.testEndpoint = new Endpoint("127.0.0.1", testPort);
        this.testModel = testModel;
        this.minFreeBytes = minFreeBytes;
        this.installRoot = guard.requireInsideWorkDir(new File(guard.workDir(), "install"));
        this.modelRoot = guard.requireInsideWorkDir(new File(guard.workDir(), "models"));
        this.runtime = new ProcessOllamaRuntime(installRoot);
        this.starter = new GuardedRuntime(runtime, guard);
    }

    public static void main(String[] args) throws Exception {
        File workDir = new File(required("acceptance.workDir"));
        int referencePort = Integer.parseInt(
            System.getProperty("acceptance.referencePort", String.valueOf(Endpoint.DEFAULT_PORT)));
        int testPort = Integer.parseInt(System.getProperty("acceptance.port", "11534"));
        AcceptanceGuard guard = new AcceptanceGuard(workDir,
            AcceptanceGuard.defaultProtectedRoots(System.getenv("OLLAMA_MODELS"), System.getProperty("user.home")),
            referencePort);
        guard.requireFreshWorkDir();
        guard.requireWritableEndpoint(new Endpoint("127.0.0.1", testPort));
        Set<String> stages = parseStages(System.getProperty("acceptance.stages", "readonly,hardware"));
        if (!guard.workDir().mkdirs() && !guard.workDir().isDirectory()) {
            throw new IllegalStateException("cannot create " + guard.workDir());
        }
        int exitCode;
        try (AcceptanceReport report = new AcceptanceReport(new File(guard.workDir(), "report.txt"))) {
            NativeAcceptanceMain run = new NativeAcceptanceMain(report, guard, stages, referencePort, testPort, System.getProperty("acceptance.testModel", "smollm2:135m"),
                Long.parseLong(System.getProperty("acceptance.minFreeGb", "5")) * 1024L * 1024L * 1024L);
            run.execute();
            report.info("summary " + report.summary() + " report=" + new File(guard.workDir(), "report.txt"));
            exitCode = report.failed() == 0 ? 0 : 1;
        }
        System.exit(exitCode);
    }

    static Set<String> parseStages(String value) {
        Set<String> result = new LinkedHashSet<String>();
        for (String part : value.split(",")) {
            String stage = part.trim().toLowerCase(Locale.ROOT);
            if (stage.equals("all")) {
                result.addAll(ALL_STAGES);
            } else if (!stage.isEmpty()) {
                if (!ALL_STAGES.contains(stage)) {
                    throw new IllegalArgumentException("unknown stage " + stage + ", expected " + ALL_STAGES);
                }
                result.add(stage);
            }
        }
        result.add("readonly");
        return result;
    }

    private void execute() {
        report.info("java.version=" + System.getProperty("java.version") + " os=" + System.getProperty("os.name")
            + " " + System.getProperty("os.arch") + " stages=" + stages + " workDir=" + guard.workDir()
            + " testEndpoint=" + testEndpoint + " reference=" + reference);
        String javaVersion = System.getProperty("java.version");
        report.check(javaVersion.startsWith("1.8."), "harness runs on Java 8",
            "java.version=" + javaVersion + " java.home=" + System.getProperty("java.home"));
        ReferenceWatch watch = new ReferenceWatch();
        watch.start();
        try {
            // Keep the install root until process and models have used its binary.
            runThenCleanUp(Arrays.<Runnable>asList(
                () -> runStage("readonly", this::readOnlyReference),
                () -> runStage("hardware", this::hardware),
                () -> runStage("install", this::install),
                () -> runStage("process", this::process),
                () -> runStage("models", this::models)), () -> {
                    if (testServer != null && testServer.isAlive()) {
                        stopQuietly(testServer);
                    }
                    if (stages.contains("install")) {
                        cleanUpInstallRoot();
                    }
                });
        } finally {
            runtime.stopAll(GRACE_MILLIS);
            watch.finish();
        }
    }

    /** Run all stages in order and the clean-up once afterwards, also when a stage throws. */
    static void runThenCleanUp(List<Runnable> stagesInOrder, Runnable cleanUp) {
        try {
            for (Runnable stage : stagesInOrder) {
                stage.run();
            }
        } finally {
            cleanUp.run();
        }
    }

    private interface Stage {
        void run() throws Exception;
    }

    private void runStage(String name, Stage stage) {
        if (!stages.contains(name)) {
            report.skip("stage " + name, "not selected");
            return;
        }
        report.info("=== stage " + name);
        try {
            stage.run();
        } catch (Exception e) {
            report.fail("stage " + name, "aborted: " + e);
        }
    }

    // Stage readonly: GET only, against the reference server.
    private void readOnlyReference() {
        try {
            String version = api.version(reference, CancellationToken.none());
            List<InstalledModel> tags = api.installedModels(reference, CancellationToken.none());
            List<LoadedModel> loaded = api.loadedModels(reference, CancellationToken.none());
            report.pass("reference read-only", "version=" + version + " tags=" + tags.size() + " loaded=" + loaded.size()
                + " evidence=" + GpuUsageEvidence.fromLoadedModels(loaded));
            for (LoadedModel model : loaded) {
                report.info("reference ps " + model.identity().name() + " gpuShare=" + model.gpuShare());
            }
        } catch (OperationException e) {
            report.skip("reference read-only", "no Ollama at " + reference + ": " + e.kind() + " " + e.getMessage());
        }
    }

    // Stage hardware: real probe and advisor, cross-checked against nvidia-smi.
    private void hardware() throws Exception {
        long started = System.currentTimeMillis();
        HardwareSnapshot snapshot = SystemHardwareProbe.forCurrentSystem().probe(CancellationToken.none());
        report.info("probe millis=" + (System.currentTimeMillis() - started) + " os=" + snapshot.operatingSystem()
            + " arch=" + snapshot.architecture() + " cpu=" + snapshot.cpuName());
        List<GpuDescriptor> nvidia = new ArrayList<GpuDescriptor>();
        for (GpuDescriptor gpu : snapshot.gpus()) {
            report.info("probe gpu vendor=" + gpu.vendor() + " name=" + gpu.name() + " vramBytes=" + gpu.vramBytes()
                + " driver=" + gpu.driverVersion());
            if (gpu.vendor() == GpuVendor.NVIDIA) {
                nvidia.add(gpu);
            }
        }
        List<String> smi;
        try {
            smi = nvidiaSmi();
        } catch (OperationException e) {
            report.fail("nvidia-smi agrees with probe", "nvidia-smi did not complete: " + e.kind() + " " + e.getMessage());
            smi = Collections.emptyList();
            nvidia = null;
        }
        if (nvidia == null) {
            report.info("nvidia-smi comparison not counted as GPU evidence");
        } else if (smi == null) {
            report.skip("nvidia-smi agrees with probe", "nvidia-smi not available");
        } else {
            for (String line : smi) {
                report.info("nvidia-smi " + line);
            }
            report.check(smi.size() == nvidia.size() && matches(smi, nvidia), "nvidia-smi agrees with probe",
                "smi=" + smi + " probe=" + nvidia.size() + " NVIDIA GPU(s)");
        }
        PackageRecommendation recommendation = new PackageAdvisor(new GpuCompatibilityRules())
            .recommend(snapshot, SupportOverrides.none(), false);
        report.info("advisor packages=" + recommendation.packages() + " confidence=" + recommendation.confidence()
            + " reasons=" + recommendation.reasons());
        if (nvidia != null && !nvidia.isEmpty()) {
            report.check(recommendation.packages().equals(EnumSet.of(DistributionPackage.BASE)),
                "advisor selects BASE only for NVIDIA", "packages=" + recommendation.packages());
        } else {
            report.skip("advisor selects BASE only for NVIDIA", "no NVIDIA GPU detected");
        }
    }

    private static boolean matches(List<String> smi, List<GpuDescriptor> gpus) {
        for (int i = 0; i < smi.size(); i++) {
            String[] fields = smi.get(i).split(",");
            GpuDescriptor gpu = gpus.get(i);
            if (fields.length < 3 || !fields[0].trim().equalsIgnoreCase(gpu.name())
                || !fields[1].trim().equals(gpu.driverVersion())) {
                return false;
            }
            long smiMib = Long.parseLong(fields[2].trim().split(" ")[0]);
            if (Math.abs(smiMib * 1024L * 1024L - gpu.vramBytes()) > 64L * 1024L * 1024L) {
                return false;
            }
        }
        return true;
    }

    /**
     * Run {@code nvidia-smi} with an enforced timeout; the runner kills the process when it hangs.
     * Returns {@code null} when the tool is missing.
     */
    private static List<String> nvidiaSmi() throws OperationException {
        CommandResult result = new ProcessCommandRunner(StandardCharsets.UTF_8).run(Arrays.asList("nvidia-smi",
            "--query-gpu=name,driver_version,memory.total", "--format=csv,noheader,nounits"), 30000,
            CancellationToken.none());
        if (!result.isFound()) {
            return null;
        }
        if (!result.isSuccess()) {
            throw new OperationException(ErrorKind.PROCESS_FAILED, "nvidia-smi exited with " + result.exitCode());
        }
        List<String> lines = new ArrayList<String>();
        for (String line : result.stdout().split("\\r?\\n")) {
            if (!line.trim().isEmpty()) {
                lines.add(line.trim());
            }
        }
        return lines;
    }

    // Stage install: official BASE ZIP into the test install root, optional second version for rollback.
    private void install() throws Exception {
        PortableDistributionInstaller installer = PortableDistributionInstaller.defaults();
        PortableInstallationService service = new PortableInstallationService(GitHubReleaseCatalog.defaults(),
            installer, installRoot, runtime);
        String version = System.getProperty("acceptance.ollamaVersion", "").trim();
        String rollbackVersion = System.getProperty("acceptance.rollbackVersion", "").trim();
        Set<DistributionPackage> base = EnumSet.of(DistributionPackage.BASE);

        // Cancel first, then restart the same download from scratch: covers abort and restart without a duplicate.
        cancelInstall(service, version);

        OllamaInstallation older = null;
        if (!rollbackVersion.isEmpty()) {
            older = timedInstall(service, rollbackVersion, base);
            report.pass("install rollback version", older.toString());
        }
        OllamaInstallation installed = timedInstall(service, version, base);
        report.pass("install BASE with SHA-256 check", installed.toString());
        verifyVersion(installed);
        report.check(installed.equals(installer.active(installRoot)), "installed version is active",
            "active=" + installer.active(installRoot));
        binary = installed;
        report.skip("ROCm/MLX add-on layout", "not downloaded for an NVIDIA BASE acceptance (issue #16)");

        if (older == null) {
            report.skip("running non-active version blocks uninstall", "set -Pacceptance.rollbackVersion");
            report.skip("rollback to previous version", "set -Pacceptance.rollbackVersion");
            usageGateOnActive(service, installed);
            return;
        }
        OllamaInstallation previous = older;
        verifyVersion(previous);
        ManagedServer server = startTestServer(previous);
        try {
            report.check(!older.equals(installer.active(installRoot)), "older version is not active",
                "active=" + installer.active(installRoot));
            expectFailure("running non-active version blocks uninstall", ErrorKind.INVALID_STATE,
                () -> service.uninstall(previous));
        } finally {
            runtime.stop(server, GRACE_MILLIS);
        }
        installer.activate(older, installRoot);
        report.check(older.equals(installer.active(installRoot)), "rollback to previous version",
            "active=" + installer.active(installRoot));
        installer.activate(installed, installRoot);
        report.check(installed.equals(installer.active(installRoot)), "re-activate newer version",
            "active=" + installer.active(installRoot));
        service.uninstall(previous);
        report.check(!service.installedVersions().contains(previous), "uninstall stopped non-active version",
            "versions=" + service.installedVersions());
    }

    /** Remove every portable version and download of this run; the installed Ollama is never below it. */
    private void cleanUpInstallRoot() {
        runtime.stopAll(GRACE_MILLIS);
        File root = guard.requireInsideWorkDir(installRoot);
        if (!root.exists()) {
            return;
        }
        try {
            java.nio.file.Files.walkFileTree(root.toPath(), new java.nio.file.SimpleFileVisitor<java.nio.file.Path>() {
                @Override
                public java.nio.file.FileVisitResult visitFile(java.nio.file.Path file,
                                                               java.nio.file.attribute.BasicFileAttributes attributes)
                    throws java.io.IOException {
                    java.nio.file.Files.delete(file);
                    return java.nio.file.FileVisitResult.CONTINUE;
                }

                @Override
                public java.nio.file.FileVisitResult postVisitDirectory(java.nio.file.Path dir, java.io.IOException failure)
                    throws java.io.IOException {
                    java.nio.file.Files.delete(dir);
                    return java.nio.file.FileVisitResult.CONTINUE;
                }
            });
            report.check(!root.exists(), "clean up portable installations", "removed " + root);
        } catch (java.io.IOException e) {
            report.fail("clean up portable installations", root + ": " + e);
        }
    }

    private OllamaInstallation timedInstall(PortableInstallationService service, String version,
                                            Set<DistributionPackage> packages) throws OperationException {
        String label = version.isEmpty() ? "latest" : version;
        long freeBefore = installRoot.getParentFile().getUsableSpace();
        long started = System.currentTimeMillis();
        OllamaInstallation result = version.isEmpty()
            ? service.installLatest(packages, progress("install " + label), CancellationToken.none())
            : service.installVersion(version, packages, progress("install " + label), CancellationToken.none());
        report.info("install " + label + " millis=" + (System.currentTimeMillis() - started) + " freeBytesBefore="
            + freeBefore + " freeBytesAfter=" + installRoot.getParentFile().getUsableSpace());
        return result;
    }

    private void usageGateOnActive(PortableInstallationService service, OllamaInstallation installed) throws Exception {
        ManagedServer server = startTestServer(installed);
        try {
            expectFailure("running active version blocks uninstall", ErrorKind.INVALID_STATE,
                () -> service.uninstall(installed));
        } finally {
            runtime.stop(server, GRACE_MILLIS);
        }
    }

    /** Cancel a second download early and verify the install root is unchanged. */
    private void cancelInstall(PortableInstallationService service, String version) throws Exception {
        List<OllamaInstallation> before = service.installedVersions();
        CancellationToken token = new CancellationToken();
        ProgressListener<TransferProgress> cancelOnFirstBytes = progress -> {
            if (progress.completedBytes() > 0) {
                token.cancel();
            }
        };
        try {
            if (version.isEmpty()) {
                service.installLatest(EnumSet.of(DistributionPackage.BASE), cancelOnFirstBytes, token);
            } else {
                service.installVersion(version, EnumSet.of(DistributionPackage.BASE), cancelOnFirstBytes, token);
            }
            report.fail("cancel install", "install finished despite cancellation");
        } catch (OperationCancelledException expected) {
            report.check(before.equals(service.installedVersions()), "cancel install leaves versions unchanged",
                "versions=" + service.installedVersions());
        }
    }

    private void verifyVersion(OllamaInstallation installation) throws OperationException {
        List<OllamaInstallation> found = runtime.discover(Collections.singletonList(installation.binaryDirectory()),
            CancellationToken.none());
        String reported = null;
        for (OllamaInstallation candidate : found) {
            if (AcceptanceGuard.canonical(candidate.binaryDirectory())
                .equals(AcceptanceGuard.canonical(installation.binaryDirectory()))) {
                reported = candidate.version();
            }
        }
        report.check(reported != null && installation.version().contains(reported), "ollama --version",
            "expected=" + installation.version() + " reported=" + reported);
    }

    // Stage process: own ollama serve on the test port; start, conflicts, cancel, restart, stop.
    private void process() throws Exception {
        OllamaInstallation installation = binary != null ? binary : binaryFromProperty();
        if (installation == null) {
            report.skip("stage process", "no binary: run stage install or set -Pacceptance.binaryDir");
            return;
        }
        binary = installation;
        expectFailure("stop of a foreign server is refused", ErrorKind.NOT_OWNED,
            () -> runtime.stop(foreignHandle(), 0));

        cancelStart(installation);

        ManagedServer server = startTestServer(installation);
        String version = api.version(testEndpoint, CancellationToken.none());
        report.pass("own server answers /api/version", "version=" + version + " endpoint=" + testEndpoint);
        report.check(runtime.isInUse(installation), "runtime reports installation in use", installation.toString());
        expectFailure("second start on same port is refused", ErrorKind.PORT_IN_USE,
            () -> starter.start(installation, testSettings(), CancellationToken.none()));

        runtime.stop(server, GRACE_MILLIS);
        report.check(!server.isAlive() && !reachable(testEndpoint), "stop own server",
            "exitCode=" + server.exitCode());
        report.check(!runtime.isInUse(installation), "installation not in use after stop", installation.toString());

        testServer = startTestServer(installation);
        report.check(reachable(testEndpoint), "restart own server", "endpoint=" + testEndpoint);
    }

    private void cancelStart(OllamaInstallation installation) throws Exception {
        CancellationToken token = new CancellationToken();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<ManagedServer> start = executor.submit(() -> starter.start(installation, testSettings(), token));
            Thread.sleep(300);
            token.cancel();
            try {
                ManagedServer started = start.get(30, TimeUnit.SECONDS);
                report.skip("cancel server start", "server was ready within 300 ms; stopping it");
                runtime.stop(started, GRACE_MILLIS);
            } catch (java.util.concurrent.ExecutionException e) {
                boolean cancelled = e.getCause() instanceof OperationCancelledException;
                report.check(cancelled && !reachable(testEndpoint), "cancel server start", "cause=" + e.getCause());
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private OllamaInstallation binaryFromProperty() throws OperationException {
        String dir = System.getProperty("acceptance.binaryDir", "").trim();
        if (dir.isEmpty()) {
            return null;
        }
        List<OllamaInstallation> found = runtime.discover(Collections.singletonList(new File(dir)),
            CancellationToken.none());
        for (OllamaInstallation candidate : found) {
            if (AcceptanceGuard.canonical(candidate.binaryDirectory()).equals(AcceptanceGuard.canonical(new File(dir)))) {
                report.info("using existing binary " + candidate + " (only its executable is used)");
                return candidate;
            }
        }
        throw new OperationException(ErrorKind.NOT_FOUND, "no working ollama executable in " + dir);
    }

    // Stage models: pull, cancel/resume, preload, ps, unload and delete of the test model on the test server.
    private void models() throws Exception {
        if (testServer == null || !testServer.isAlive()) {
            OllamaInstallation installation = binary != null ? binary : binaryFromProperty();
            if (installation == null) {
                report.skip("stage models", "no binary: run stage install or set -Pacceptance.binaryDir");
                return;
            }
            testServer = startTestServer(installation);
        }
        guard.requireOwnedTestEndpoint(testEndpoint, testServer);
        long free = modelRoot.getUsableSpace();
        if (free < minFreeBytes) {
            report.skip("stage models", "only " + free + " bytes free in " + modelRoot + ", need " + minFreeBytes);
            return;
        }
        List<String> before = new ArrayList<String>();
        for (InstalledModel model : api.installedModels(testEndpoint, CancellationToken.none())) {
            before.add(model.identity().name());
        }
        report.info("test model root " + modelRoot + " models before=" + before);

        CancellationToken token = new CancellationToken();
        AtomicBoolean sawBytes = new AtomicBoolean();
        try {
            models.pull(testEndpoint, testModel, progress -> {
                if (progress.completedBytes() > 0 && sawBytes.compareAndSet(false, true)) {
                    token.cancel();
                }
            }, token);
            report.skip("cancel pull", "pull finished before the first progress could cancel it");
        } catch (OperationCancelledException e) {
            report.pass("cancel pull", "cancelled after first progress");
        }
        models.pull(testEndpoint, testModel, progress("pull " + testModel), CancellationToken.none());
        boolean listed = false;
        for (InstalledModel model : api.installedModels(testEndpoint, CancellationToken.none())) {
            if (AcceptanceGuard.normalizeName(model.identity().name()).equals(AcceptanceGuard.normalizeName(testModel))) {
                listed = true;
                report.info("pulled " + model);
            }
        }
        report.check(listed, "resume pull and list in /api/tags", testModel);

        LoadedModel loaded = models.preloadAndVerify(testEndpoint, testModel, ModelOptions.none(), "5m",
            CancellationToken.none());
        List<LoadedModel> ps = api.loadedModels(testEndpoint, CancellationToken.none());
        report.pass("preload and /api/ps", "size=" + loaded.sizeBytes() + " vram=" + loaded.vramBytes()
            + " ctx=" + loaded.contextLength() + " evidence=" + GpuUsageEvidence.fromLoadedModels(ps));
        models.unload(testEndpoint, testModel, CancellationToken.none());
        report.check(waitUntilUnloaded(), "unload", "ps after unload=" + api.loadedModels(testEndpoint,
            CancellationToken.none()).size());

        guard.requireOwnedTestEndpoint(testEndpoint, testServer);
        guard.requireDeletableTestModel(testModel, before, testServer.appliedSettings().modelRoot());
        models.delete(testEndpoint, testModel, testModel, CancellationToken.none());
        boolean stillListed = false;
        for (InstalledModel model : api.installedModels(testEndpoint, CancellationToken.none())) {
            stillListed |= AcceptanceGuard.normalizeName(model.identity().name())
                .equals(AcceptanceGuard.normalizeName(testModel));
        }
        report.check(!stillListed, "delete test model in test model root", testModel + " root=" + modelRoot);
    }

    private boolean waitUntilUnloaded() throws Exception {
        for (int i = 0; i < 20; i++) {
            if (api.loadedModels(testEndpoint, CancellationToken.none()).isEmpty()) {
                return true;
            }
            Thread.sleep(500);
        }
        return false;
    }

    private ServerSettings testSettings() {
        return ServerSettings.defaults()
            .with(ServerSettingCatalog.BIND, testEndpoint)
            .with(ServerSettingCatalog.MODEL_ROOT, modelRoot);
    }

    private ManagedServer startTestServer(OllamaInstallation installation) throws OperationException {
        ServerSettings settings = testSettings();
        ManagedServer server = starter.start(installation, settings, CancellationToken.none());
        report.info("started own server " + server.installation() + " modelRoot=" + settings.modelRoot());
        return server;
    }

    private void stopQuietly(ManagedServer server) {
        try {
            runtime.stop(server, GRACE_MILLIS);
            report.info("stopped own server " + server.installation());
        } catch (OperationException e) {
            report.fail("stop own server on exit", e.toString());
        }
    }

    private boolean reachable(Endpoint endpoint) {
        try {
            api.version(endpoint, CancellationToken.none());
            return true;
        } catch (OperationException e) {
            return false;
        }
    }

    private interface Action {
        void run() throws Exception;
    }

    private void expectFailure(String check, ErrorKind expected, Action action) {
        try {
            action.run();
            report.fail(check, "no error, expected " + expected);
        } catch (OperationException e) {
            report.check(e.kind() == expected, check, e.kind() + ": " + e.getMessage());
        } catch (Exception e) {
            report.fail(check, "unexpected " + e);
        }
    }

    private ProgressListener<TransferProgress> progress(String label) {
        return new ProgressListener<TransferProgress>() {
            private long lastPercent = -10;

            @Override
            public void onProgress(TransferProgress progress) {
                long percent = Math.round(progress.fraction() * 100);
                if (percent >= lastPercent + 10) {
                    lastPercent = percent;
                    report.info(label + " " + progress);
                }
            }
        };
    }

    private ManagedServer foreignHandle() {
        final OllamaInstallation external = OllamaInstallation.external(OllamaInstallation.UNKNOWN_VERSION, reference);
        return new ManagedServer() {
            @Override
            public OllamaInstallation installation() {
                return external;
            }

            @Override
            public ServerSettings appliedSettings() {
                return ServerSettings.defaults();
            }

            @Override
            public boolean isAlive() {
                return true;
            }

            @Override
            public Integer exitCode() {
                return null;
            }
        };
    }

    private static String required(String key) {
        String value = System.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException("missing system property " + key);
        }
        return value.trim();
    }

    /** Polls the reference server during the whole run and reports outages or a version change. */
    private final class ReferenceWatch extends Thread {

        private volatile boolean running = true;
        private String initialVersion;
        private int polls;
        private int failures;
        private final Set<String> versions = new LinkedHashSet<String>();

        ReferenceWatch() {
            super("reference-watch");
            setDaemon(true);
        }

        @Override
        public void run() {
            while (running) {
                poll();
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }

        private synchronized void poll() {
            polls++;
            try {
                String version = api.version(reference, CancellationToken.none());
                if (initialVersion == null) {
                    initialVersion = version;
                }
                versions.add(version);
            } catch (OperationException e) {
                failures++;
            }
        }

        void finish() {
            running = false;
            interrupt();
            try {
                join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            poll();
            synchronized (this) {
                if (initialVersion == null) {
                    report.skip("reference stays reachable and unchanged", "no Ollama at " + reference);
                } else {
                    report.check(failures == 0 && versions.size() == 1, "reference stays reachable and unchanged",
                        "polls=" + polls + " failures=" + failures + " versions=" + versions);
                }
            }
        }
    }
}
