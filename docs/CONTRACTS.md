# Contracts for parallel implementation

Base package: `com.aresstack.ollamamanager`. All contracts below are owned by the coordinator.
Changes go through a coordinator PR first; feature branches only implement them.

## Error and cancellation contract (`application.common`)

- Every port method throws the checked `OperationException` with an `ErrorKind`. Adapters map library
  or IO exceptions to a kind and never leak them.
- `CancellationToken`: long-running methods call `throwIfCancelled()` between units of work and register
  `onCancel(Runnable)` to abort blocking IO (close stream, destroy owned process). `onCancel` returns a
  `CancellationToken.Registration`; close it with an explicit `try { ... } finally { registration.close(); }`
  when the guarded work ends, so tokens reused across operations do not accumulate callbacks. Close it only
  after the guarded resource is finished (for example not while an owned child process still runs).
  Integration duty: #11, #12, #14 and #18 must close the registrations at their existing call sites and test
  token reuse across several requests. After cancellation they throw
  `OperationCancelledException` (`ErrorKind.CANCELLED`) and clean up partial results.
- `ProgressListener<T>` is called from background threads; callers dispatch to the EDT themselves.
- `TransferProgress(phase, completedBytes, totalBytes)`; `totalBytes = -1` when unknown.
- `LifecycleLock`: one instance per process, created in the composition root and passed to
  `ServerLifecycleService` and `PortableInstallationService`. Start, stop, restart, attaching an external
  server and uninstall (usage check plus deletion) run under it; downloads and installs never take it. It
  never waits: a concurrent operation fails fast with `INVALID_STATE`. `OwnedServerUsage` is the only
  `InstallationUsage` implementation; it counts every installation as in use while a start or stop runs.

## Setup assistant (`application.setup`)

- `SetupService` only coordinates and delegates: `detectExisting` (runtime discovery),
  `detectRunning` (API version, `null` when nothing answers), `attachExisting` (verify via API, then
  `attachExternal`), `recommendPackages` (hardware probe plus `PackageAdvisor`), `installLatest` and
  `uninstall` (portable installation use cases). It holds no lock of its own, so the lifecycle safety also
  applies to callers that use the underlying services directly. The UI gets it via `ConsoleServices.setup()`.

## Ports (`application.port`)

| Port | Implemented by | Key methods |
|---|---|---|
| `OllamaRuntimePort`, `ManagedServer` | #3 | `discover`, `start`, `stop(server, graceMillis)`, `recentLog` |
| `OllamaApiPort` | #6 | `version`, `installedModels`, `loadedModels`, `show`, `pull`, `preload`, `unload`, `delete` |
| `ReleaseCatalogPort` | #4 | `latestStable`, `findRelease(version)` (SHA-256 filled from `sha256sum.txt`) |
| `DistributionInstallerPort` | #4 | `install`, `installedVersions`, `uninstall` |
| `HardwareProbePort` | #5 | `probe` |
| `GpuTelemetryPort` | #5 | `sample` (live GPU usage; empty list when no tool is available) |
| `ConfigurationRepository` | done (`JsonConfigurationRepository`) | `load`, `save` – only called by `ConfigurationService` |
| `BenchmarkMetricsPort` | #8 | `record`, `findValid` |

Model identity rule: digests come only from `/api/tags` or `/api/ps`. `show()` returns `ModelDetails`
without a digest. `ModelIdentity.sameArtifactAs` requires both digests to be known and equal, so a profile
or benchmark result for a model without a digest is never treated as valid.

Environment rule: `ProcessBuilder.environment()` starts as a copy of the parent environment. The runtime
adapter (#3) removes every name from `ServerSettingCatalog.environmentVariables()` before applying
`ServerSettings.toEnvironment()`, so an unset setting means Ollama's built-in default.

JSON rule: all JSON goes through `infrastructure.json.Json`, which runs Gson in strict mode and rejects
empty input, comments, unquoted keys, unescaped control characters and trailing content.

Configuration rule: every change to settings or profiles goes through
`application.configuration.ConfigurationService.update(ConfigurationChange)`, which runs load, change and save
under the lock of the repository instance. `ConfigurationChange.apply` may throw `OperationException` (for
example `INVALID_STATE` on a revision conflict); validation therefore runs under the lock and nothing is saved
on rejection. `current()` reads the stored state.

Integration duty (binding before the contract counts as fulfilled): the composition root creates exactly one
`ConfigurationRepository` and one `ConfigurationService` and passes that service to every use case. #12 must
migrate every profile and activation write path (`ModelProfileRepository.update`, `ProfileActivationService`)
to it, and server-settings changes use it as well. No use case or UI component calls
`ConfigurationRepository.save` directly or keeps its own configuration lock. A second repository instance for the
same file is not protected and must not be created. This PR provides the mechanism; the guarantee holds only
after that migration.

GPU rule: the UI reads live GPU statistics only through `GpuTelemetryPort`; it never knows `nvidia-smi` or
infrastructure classes. `GpuDescriptor.computeCapability()` is optional (`null` = unknown). Unknown hardware
properties are never presented as confirmed GPU support; only runtime evidence (for example `/api/ps`) confirms
that Ollama actually uses the GPU.

Ownership rule: only a `ManagedServer` handle returned by `start` proves ownership. `stop` on any other
handle fails with `NOT_OWNED`. External servers are represented only as `OllamaInstallation.external(...)`.

## Domain types and public constructors (`domain.*`)

- `server.Endpoint(host, port)`, `Endpoint.parse("host:port")`, `Endpoint.DEFAULT` = `127.0.0.1:11434`
- `server.OllamaInstallation(kind, version, binaryDirectory, endpoint)`, `OllamaInstallation.external(version, endpoint)`
- `server.InstallationKind`: `MANAGED_PORTABLE`, `MANAGED_INSTALLED`, `EXTERNAL_ATTACHED`
- `server.RuntimeOwnership`: `OWNED`, `EXTERNAL`, `NONE`; `server.ServerState`
- `server.ServerStatus(state, ownership, installation, reportedVersion, message)`, `ServerStatus.stopped()`
- `server.ServerSetting<T>`: typed descriptor (id, label, tooltip, `OptionType`, default/unset, range,
  choices, validation, environment variable, restart flag). `server.ServerSettingCatalog` lists all entries.
- `server.ServerSettings`: `defaults()`, `with(setting, value)`, typed getters, `toEnvironment()`,
  `differencesTo(other)`, `requiresRestartComparedTo(applied)`, `toEncodedMap()` / `fromEncodedMap(...)`
- `model.ModelIdentity(name, digest, quantization)`, `model.ModelDetails(...)`, `model.InstalledModel(...)`, `model.LoadedModel(...)`
- `model.ModelOptions(numCtx, numPredict, temperature, seed)` – per request only
- `hardware.GpuDescriptor(vendor, name, vramBytes, driverVersion, dedicated[, computeCapability])`,
  `hardware.GpuUsageSample(gpuName, usedVramBytes, totalVramBytes, utilizationPercent, sampledAtEpochMillis)`,
  `hardware.HardwareSnapshot(os, arch, cpuName, logicalProcessors, totalMemoryBytes, gpus)`, `hardware.GpuFingerprint`
- `profile.ProfileScope(model, gpuFingerprint, ollamaVersion, workload)`,
  `profile.ModelProfile(id, name, revision, scope, serverSettings, modelOptions)` (the user optimization profile),
  `profile.ProfileActivationPlan.plan(profile, appliedSettings, ownership)` → `ActivationMode`
- `benchmark.BenchmarkResult(scope, parallelism, totalRequests, failedRequests, completedOutputTokens,
  elapsedMillis, p50, p95, measuredAt)`
- `distribution.DistributionPackage` (`BASE`, `ROCM`, `MLX`), `distribution.ReleaseAsset`, `distribution.OllamaRelease`
- `configuration.ManagerConfiguration` (`FORMAT_VERSION = 1`)

Unknown values are `null` (documented per getter), never guessed.

## File ownership for parallel work

| Owner | Paths |
|---|---|
| Coordinator | root `build.gradle`, `settings.gradle`, `gradle/`, `gradlew*`, `.github/`, `README.md`, `LICENSE`, `docs/`, all of `manager-domain`, `manager-application/.../application/common`, `manager-application/.../application/port`, `manager-application/.../application/configuration`, `manager-infrastructure/.../infrastructure/json`, `.../infrastructure/persistence`, `manager-swing/.../swing/OllamaManagerApp.java` (composition root) |
| #3 Process | `manager-infrastructure/.../infrastructure/runtime/**`, `manager-application/.../application/server/**` (lifecycle use cases) and their tests |
| #4 Distribution | `manager-infrastructure/.../infrastructure/distribution/**`, `manager-application/.../application/installation/**` |
| #5 Hardware | `manager-infrastructure/.../infrastructure/hardware/**`, `manager-application/.../application/hardware/**` |
| #6 Model API | `manager-infrastructure/.../infrastructure/ollamaapi/**`, `manager-application/.../application/model/**`, `.../application/profile/**` |
| #7 Swing | `manager-swing/.../swing/**` except `OllamaManagerApp.java` |
| #8 Benchmark | `manager-infrastructure/.../infrastructure/benchmark/**`, `manager-application/.../application/benchmark/**` |

Workers needing a contract change or a new library dependency open a coordinator request instead of
editing owned files. Shared helpers (for example a fake HTTP server for tests) go into the requesting
package first and are promoted by the coordinator if a second worker needs them.
