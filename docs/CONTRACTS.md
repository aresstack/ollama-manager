# Contracts for parallel implementation

Base package: `com.aresstack.ollamamanager`. All contracts below are owned by the coordinator.
Changes go through a coordinator PR first; feature branches only implement them.

## Error and cancellation contract (`application.common`)

- Every port method throws the checked `OperationException` with an `ErrorKind`. Adapters map library
  or IO exceptions to a kind and never leak them.
- `CancellationToken`: long-running methods call `throwIfCancelled()` between units of work and register
  `onCancel(Runnable)` to abort blocking IO (close stream, destroy owned process). After cancellation they
  throw `OperationCancelledException` (`ErrorKind.CANCELLED`) and clean up partial results.
- `ProgressListener<T>` is called from background threads; callers dispatch to the EDT themselves.
- `TransferProgress(phase, completedBytes, totalBytes)`; `totalBytes = -1` when unknown.

## Ports (`application.port`)

| Port | Implemented by | Key methods |
|---|---|---|
| `OllamaRuntimePort`, `ManagedServer` | #3 | `discover`, `start`, `stop(server, graceMillis)`, `recentLog` |
| `OllamaApiPort` | #6 | `version`, `installedModels`, `loadedModels`, `show`, `pull`, `preload`, `unload`, `delete` |
| `ReleaseCatalogPort` | #4 | `latestStable` (SHA-256 filled from `sha256sum.txt`) |
| `DistributionInstallerPort` | #4 | `install`, `installedVersions`, `uninstall` |
| `HardwareProbePort` | #5 | `probe` |
| `ConfigurationRepository` | done (`JsonConfigurationRepository`) | `load`, `save` |
| `BenchmarkMetricsPort` | #8 | `record`, `findValid` |

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
- `model.ModelIdentity(name, digest, quantization)`, `model.InstalledModel(...)`, `model.LoadedModel(...)`
- `model.ModelOptions(numCtx, numPredict, temperature, seed)` – per request only
- `hardware.GpuDescriptor(vendor, name, vramBytes, driverVersion, dedicated)`,
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
| Coordinator | root `build.gradle`, `settings.gradle`, `gradle/`, `gradlew*`, `.github/`, `README.md`, `LICENSE`, `docs/`, all of `manager-domain`, `manager-application/.../application/common`, `manager-application/.../application/port`, `manager-infrastructure/.../infrastructure/json`, `.../infrastructure/persistence`, `manager-swing/.../swing/OllamaManagerApp.java` (composition root) |
| #3 Process | `manager-infrastructure/.../infrastructure/runtime/**`, `manager-application/.../application/server/**` (lifecycle use cases) and their tests |
| #4 Distribution | `manager-infrastructure/.../infrastructure/distribution/**`, `manager-application/.../application/installation/**` |
| #5 Hardware | `manager-infrastructure/.../infrastructure/hardware/**`, `manager-application/.../application/hardware/**` |
| #6 Model API | `manager-infrastructure/.../infrastructure/ollamaapi/**`, `manager-application/.../application/model/**`, `.../application/profile/**` |
| #7 Swing | `manager-swing/.../swing/**` except `OllamaManagerApp.java` |
| #8 Benchmark | `manager-infrastructure/.../infrastructure/benchmark/**`, `manager-application/.../application/benchmark/**` |

Workers needing a contract change or a new library dependency open a coordinator request instead of
editing owned files. Shared helpers (for example a fake HTTP server for tests) go into the requesting
package first and are promoted by the coordinator if a second worker needs them.
