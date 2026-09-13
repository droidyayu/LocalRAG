# LocalRAG — working notes

Two things live here:

1. **LocalRAG**, a reusable Android library that answers help and FAQ questions fully on-device
   from Markdown documentation owned by the host app.
2. **A demo app** (`:app`) — a fintech UI plus an assistant — that is the library first consumer
   and the place the library is exercised on a real device.

See `README.md` for the product picture and `tools/embed/README.md` for the embedding sidecar.

## Layout

```
LocalRAG/
├── settings.gradle.kts         includeBuild("localrag-tooling"); :app, :localrag-android
├── localrag-tooling/           an included build, isolated from the root
│   ├── localrag-core/          pure Kotlin/JVM. No Android, no LiteRT, no Gradle API
│   └── localrag-gradle-plugin/ plugin id com.ayushig.localrag
├── localrag-android/           the AAR, namespace com.ayushig.localrag.android
├── app/                        demo host, com.ayushig.localrag.demo
└── tools/embed/                embedding sidecar scripts
```

A Gradle plugin cannot be applied by a sibling project in the same build, and `buildSrc` cannot be
published, so the plugin and the core it shares with the runtime sit in an included build.
`:localrag-android` depends on `com.ayushig.localrag:localrag-core:0.1.0` and Gradle substitutes
the included project.

## Rules

- **`localrag-core` takes no Android and no LiteRT dependency, ever.** It is shared by the build
  plugin and the runtime, and that shared code is the only thing guaranteeing an index built on CI
  matches queries typed on a phone. A drift there does not error, it quietly degrades retrieval.
- **The model never produces a number.** Figures come from data and are rendered by shared
  formatting code. This holds for the demo app portfolio answers today and for library generation
  when it lands.
- **No LiteRT type crosses the `LocalRag` public API.** Model paths go in as strings.
- **BM25-only is a supported state, not a broken build.** A bundle with no vectors and no
  embedding block in its manifest is correct output.
- The manifest embedding block is a parity contract: any mismatch drops the vectors and falls back
  to BM25 rather than silently retrieving from a different vector space.
- Build-time and runtime embeddings use the same LiteRT-LM version, pinned together in
  `gradle/libs.versions.toml` and `tools/embed/requirements.txt`.
- Validation messages name the file and the line. Content authors are not engineers.
- Plugin code is configuration-cache safe: no `Project` at execution time, injected
  `ExecOperations`, every task property annotated.

### Demo app

MVVM plus clean architecture, `presentation` → `domain` ← `data`, domain depends on nothing. The
app uses `ui/` rather than `presentation/`. ViewModels call use cases, never repositories. Screens
are stateless and take an immutable state plus event lambdas.

## Current state

Phases 1 to 3 are done: core, plugin, and on-device BM25 retrieval with zero models. The demo app
ships a 53-chunk bundle in its assets and a documentation search screen driving `retrieveOnly`.

Not built yet: the embedder (phase 4), the output gate and generation (phase 5), precomputed
cluster answers (phase 6), the evaluation harness (phase 7). The LiteRT-LM generation spike in the
demo app assistant still runs on a fake echo repository because the Gemma model was never pushed.

## Build

```bash
./gradlew :app:assembleDebug            # builds core, plugin, bundle and app in one invocation
./gradlew :app:generateLocalRagBundle   # bundle only
./gradlew -p localrag-tooling :localrag-core:test
./gradlew :localrag-android:testDebugUnitTest
./gradlew publishToMavenLocal           # plus -p localrag-tooling for core and the plugin
```

Add `-PlocalRagEmbed=true` to exercise the embedding path with the deterministic hash sidecar.

Kotlin 2.3.20 · AGP 9.3.2 · Gradle 9.5 · Compose BOM 2025.12.00 · minSdk 26 · target/compileSdk 37.
`localrag-core` compiles on JDK 17 but emits Java 11 bytecode, because the Android consumers
declare Java 11 and a variant advertising 17 would fail their resolution.

Dependencies go through `gradle/libs.versions.toml`. The included build loads the same catalog.
