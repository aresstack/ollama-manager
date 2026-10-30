# Native acceptance (issue #16)

The `manager-acceptance` module is a test harness, not a product feature. It is not part of
`ollama-manager.jar`, and `build`/CI only compile it and run its guard unit tests. It runs only through:

```
gradlew :manager-acceptance:nativeAcceptance -PnativeAcceptance=true [-Pacceptance.<key>=<value> ...]
```

Without `-PnativeAcceptance=true` the task fails before doing anything.

## Safety rules enforced in code (`AcceptanceGuard`)

- The reference server (`127.0.0.1:11434` by default) is only read: `/api/version`, `/api/tags`, `/api/ps`.
  A background watch polls its version every 2 s and fails the run on an outage or version change.
- Every server start goes through `GuardedRuntime`, which refuses port 11434, the reference port, non-loopback
  binds and model roots outside the work directory without probing the port first, so a reference server
  that is down never makes its port usable. The run aborts at startup if `acceptance.port` equals
  `acceptance.referencePort`. Pull, preload, unload and delete go only to an endpoint whose live
  server this run started itself (`ManagedServer` handle).
- Everything is written below a fresh work directory named `ollama-acceptance-*` (default
  `%TEMP%\ollama-acceptance-<timestamp>`). It must be new or empty and must not overlap `OLLAMA_MODELS`
  or `%USERPROFILE%\.ollama`. Install root: `<workDir>\install`, `OLLAMA_MODELS`: `<workDir>\models`.
- Delete only removes the test model if it was absent before the run and its manifest lies in the test
  model root. Pre-existing names are never deleted.
- Only processes this run started are stopped. The installed Ollama service is never touched.
- A skipped check is reported as `SKIP`, never as `PASS`.

## Stages

Select with `-Pacceptance.stages=` (comma-separated, or `all`). `readonly` always runs. Default: `readonly,hardware`.

| Stage | Opt-in effect | Checks |
|---|---|---|
| `readonly` | GET on reference | version, tag and ps count, GPU evidence of loaded models |
| `hardware` | runs `nvidia-smi` (30 s timeout, killed on hang), CIM | probe vs `nvidia-smi` (name, driver, VRAM ±64 MiB), advisor = `[BASE]` for NVIDIA |
| `install` | downloads official BASE ZIP (~1–2 GB per version) | cancel during download leaves no version, restart from scratch, SHA-256 manifest, extract, `ollama.exe --version`, active version, download time and free space logged, uninstall refused while the version runs (also when it is **not** active), rollback/re-activate; ROCm/MLX reported as SKIP (no download) |
| `process` | starts `ollama serve` on the test port | stop of foreign handle refused (no start is ever attempted on the reference port), cancel start, `/api/version`, `isInUse`, second start on same port refused, stop, restart |
| `models` | pulls the test model into `<workDir>\models`, loads it on the GPU | cancel pull, resume pull, `/api/tags`, preload + `/api/ps` placement and evidence, unload, guarded delete |

## Properties

| Property | Default | Meaning |
|---|---|---|
| `acceptance.workDir` | `%TEMP%\ollama-acceptance-<timestamp>` | must start with `ollama-acceptance-` |
| `acceptance.port` | `11534` | test server port |
| `acceptance.referencePort` | `11434` | read-only reference server |
| `acceptance.ollamaVersion` | latest stable | e.g. `v0.40.13` |
| `acceptance.rollbackVersion` | none | older tag, enables the "running but not active" and rollback checks |
| `acceptance.binaryDir` | none | use an existing `ollama.exe` directory for `process`/`models` without `install` (only the executable is used, with the test port and test model root) |
| `acceptance.testModel` | `smollm2:135m` | small model for the `models` stage |
| `acceptance.minFreeGb` | `5` | `models` is skipped below this free space |

## Recommended run on the RTX 5080 machine (Windows 11, Java 8)

```bat
rem 1. Read-only and hardware only
gradlew :manager-acceptance:nativeAcceptance -PnativeAcceptance=true

rem 2. Full run with two versions (downloads two BASE ZIPs)
gradlew :manager-acceptance:nativeAcceptance -PnativeAcceptance=true ^
  -Pacceptance.stages=all -Pacceptance.ollamaVersion=v0.40.13 -Pacceptance.rollbackVersion=<older tag>
```

The Gradle daemon JVM runs the harness, so `JAVA_HOME` must point to the Java 8 JDK. Before and after,
`ollama ps` and `ollama list` against 11434 should show the same state. Each run writes
`<workDir>\report.txt` with OS, Java, GPU, driver and Ollama versions and every PASS/FAIL/SKIP line; attach it
to the affected PRs as evidence. The exit code is non-zero if any check failed. The work directory is kept for
inspection; delete it manually afterwards.
