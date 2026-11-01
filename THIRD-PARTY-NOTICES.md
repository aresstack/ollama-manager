# Third-party notices

Ollama Manager bundles the following libraries in `ollama-manager.jar`. The libraries in the first table are
licensed under the Apache License 2.0. A complete copy of that license is shipped as `licenses/Apache-2.0.txt` in the ZIP
and as `META-INF/licenses/Apache-2.0.txt` in the JAR.

| Library | Version | License |
|---|---|---|
| [Gson](https://github.com/google/gson) | 2.11.0 | Apache License 2.0 |
| [Error Prone annotations](https://github.com/google/error-prone) (Gson dependency) | 2.27.0 | Apache License 2.0 |
| [OkHttp](https://github.com/square/okhttp) | 4.12.1 | Apache License 2.0 |
| [Okio](https://github.com/square/okio) (`okio`, `okio-jvm`) | 3.6.0 | Apache License 2.0 |
| [Kotlin standard library](https://github.com/JetBrains/kotlin) (`kotlin-stdlib`, `-common`, `-jdk7`, `-jdk8`) | 1.9.10 | Apache License 2.0 |
| [JetBrains annotations](https://github.com/JetBrains/java-annotations) | 13.0 | Apache License 2.0 |
| [Apache Commons Math](https://commons.apache.org/proper/commons-math/) | 3.6.1 | Apache License 2.0 |
| [iirj](https://github.com/berndporr/iirj) | 1.7 | Apache License 2.0 |

Apache Commons Math ships `META-INF/NOTICE.txt`, which is kept unchanged in the JAR.

The model installer (module `manager-askai`) is taken over from
[askai-java8](https://github.com/Miguel0888/askai-java8) together with AresStack's
`win-proxy-java` 0.1.0-beta.4 and `directml-model-catalog` 0.2.1, all MIT License, Copyright (c) 2026 AresStack
(`licenses/MIT-AresStack.txt`). SLF4J (`slf4j-api`, `slf4j-nop` 1.7.36) is MIT License
(`licenses/MIT-SLF4J.txt`). GraalJS, which win-proxy-java uses only to evaluate PAC scripts, is not bundled.

Parsing of ollama.com library pages uses Jodd, licensed under the BSD 2-Clause License. Its license text
(Copyright (c) 2003-present, Jodd Team) is shipped as `licenses/BSD-2-Clause-Jodd.txt` (ZIP) and
`META-INF/licenses/BSD-2-Clause-Jodd.txt` (JAR).

| Library | Version | License |
|---|---|---|
| [Jodd Lagarto](https://github.com/oblac/jodd-lagarto) | 6.0.6 | BSD 2-Clause |
| [Jodd Util](https://github.com/oblac/jodd-util) (Lagarto dependency) | 6.1.0 | BSD 2-Clause |

None of these libraries ships an Apache `NOTICE` file. OkHttp contains
`okhttp3/internal/publicsuffix/NOTICE`, which is kept unchanged in the JAR at that path:

> `publicsuffixes.gz` is compiled from The Public Suffix List
> (https://publicsuffix.org/list/public_suffix_list.dat). It is subject to the terms of the Mozilla Public
> License, v. 2.0. A copy is shipped as `licenses/MPL-2.0.txt` (ZIP) and `META-INF/licenses/MPL-2.0.txt` (JAR);
> the source form is available at the URL above.

Ollama itself is not bundled. Portable Ollama distributions are downloaded by the user from the official
GitHub releases and keep their upstream license files.
