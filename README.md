# Ollama Manager

Standalone desktop manager and optimizer for a local [Ollama](https://ollama.com) server.
Java 8 + Swing, MIT licensed. Windows x64 is the first supported platform.

> Status: foundation (issue #2). The window is a minimal shell; server control, downloads,
> hardware detection, model management and benchmarks follow in issues #3–#8.

## Requirements

- Java Runtime 8 or newer on `PATH` (the manager is built and tested on Java 8).
- Ollama itself is **not** bundled. It is either an existing installation or a portable
  distribution the manager downloads from the official GitHub releases on explicit request (issue #4).

## Run

```
java -jar ollama-manager.jar
```

On Windows the ZIP distribution also contains `ollama-manager.cmd`, which starts the JAR with `javaw`
(no console window). `java -jar ollama-manager.jar --smoke-test` checks that all modules load and exits
without opening a window.

## Build

```
./gradlew clean build        # Windows: gradlew.bat clean build
```

Run Gradle with a **JDK 8** (`JAVA_HOME`) so tests execute on a real Java 8 runtime. The build
deliberately stays on Gradle 8.x, the last line that can run on Java 8; its deprecation notice about
Java 8 is expected. The wrapper distribution is pinned by SHA-256.

Outputs:

- `manager-swing/build/executable/ollama-manager.jar` – executable JAR with all modules
- `manager-swing/build/distributions/ollama-manager-<version>.zip` – JAR, launcher, README, LICENSE

`build` also runs `verifyArchitecture` (forbidden imports per layer) and the `java -jar --smoke-test`.

## Architecture

| Module | Content | May depend on |
|---|---|---|
| `manager-domain` | Immutable value objects and rules | nothing |
| `manager-application` | Ports, error and cancellation contract, use cases | domain |
| `manager-infrastructure` | Adapters: process, HTTP, GitHub, ZIP, hardware, JSON persistence | application, domain |
| `manager-swing` | Swing UI and composition root (`OllamaManagerApp`) | all |

Contracts and file ownership for parallel work: [docs/CONTRACTS.md](docs/CONTRACTS.md).

## Security and lifecycle semantics

- The default bind address is `127.0.0.1:11434`; nothing is exposed to the network by default.
- Server settings are passed only to the child process via `ProcessBuilder.environment()`. Global or user
  Windows environment variables are never written.
- `OLLAMA_NUM_PARALLEL`, `OLLAMA_MAX_LOADED_MODELS`, `OLLAMA_MAX_QUEUE`, `OLLAMA_CONTEXT_LENGTH`,
  `OLLAMA_KV_CACHE_TYPE`, `OLLAMA_FLASH_ATTENTION`, `OLLAMA_KEEP_ALIVE` and `OLLAMA_MODELS` are server-wide
  and need a restart of a server the manager started. Per-request options such as `num_ctx` do not, and
  only affect requests the manager itself sends.
- The manager stops only servers it started itself. Already running Ollama instances are attached
  read-only and never stopped.
- Preferences are stored in `%LOCALAPPDATA%\AresStack\OllamaManager\configuration.json`
  (`~/.ollama-manager` elsewhere), written atomically with a `.bak` copy for recovery. No credentials.

## Test evidence

Automated tests use fakes only. Native Ollama, download and GPU behaviour require the manual Windows
checks named in each issue and are not claimed by CI.

## License

MIT © 2026 AresStack. Bundled libraries: see [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md).
Ollama is a separate project under its own license.
