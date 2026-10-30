# Third-party notices

Ollama Manager bundles the following libraries in `ollama-manager.jar`. All are licensed under the
Apache License 2.0. A complete copy of that license is shipped as `licenses/Apache-2.0.txt` in the ZIP
and as `META-INF/licenses/Apache-2.0.txt` in the JAR.

| Library | Version | License |
|---|---|---|
| [Gson](https://github.com/google/gson) | 2.11.0 | Apache License 2.0 |
| [Error Prone annotations](https://github.com/google/error-prone) (Gson dependency) | 2.27.0 | Apache License 2.0 |
| [OkHttp](https://github.com/square/okhttp) | 4.12.1 | Apache License 2.0 |
| [Okio](https://github.com/square/okio) (`okio`, `okio-jvm`) | 3.6.0 | Apache License 2.0 |
| [Kotlin standard library](https://github.com/JetBrains/kotlin) (`kotlin-stdlib`, `-common`, `-jdk7`, `-jdk8`) | 1.9.10 | Apache License 2.0 |
| [JetBrains annotations](https://github.com/JetBrains/java-annotations) | 13.0 | Apache License 2.0 |

None of these libraries ships an Apache `NOTICE` file. OkHttp contains
`okhttp3/internal/publicsuffix/NOTICE`, which is kept unchanged in the JAR at that path:

> `publicsuffixes.gz` is compiled from The Public Suffix List
> (https://publicsuffix.org/list/public_suffix_list.dat). It is subject to the terms of the Mozilla Public
> License, v. 2.0. A copy is shipped as `licenses/MPL-2.0.txt` (ZIP) and `META-INF/licenses/MPL-2.0.txt` (JAR);
> the source form is available at the URL above.

Ollama itself is not bundled. Portable Ollama distributions are downloaded by the user from the official
GitHub releases and keep their upstream license files.
