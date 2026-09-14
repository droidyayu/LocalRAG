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

All seven phases are implemented. The demo app answers portfolio questions from formatted data and
documentation questions through the library, with generation running on a pushed Gemma model (see
`scripts/push_model.sh`) and a documentation search screen driving `retrieveOnly`.

Architecture split: the SDK is a stateless engine. It hosts LiteRT-LM, loads bundles,
retrieves, generates one-shot answers on fresh per-call conversations, runs the output gate, and
provides the configurable agent runner (`runAgent(query, AgentConfig)`); its only shared mutable
state is the single-flight guard, and loaded models/bundle are read-only resources. All business
logic lives in the app: tool definitions and figure rendering (`AssistantToolDefinitions`),
prompt copy (`AGENT_SYSTEM_PROMPT` in `AgentPrompts.kt`), transcript labels, and all UI. The
keyword router, templates, echo-era engine shims, and settings debug UI are deleted — with no
model on device the assistant says it has no information rather than guessing. The composed
precomputed → generated → extractive pipeline is deleted with them: uncalled after the agent
cutover, docs Q&A now runs through `runAgent` with the host app's tools.

- **Embedder (phase 4).** Neither candidate from the original plan: LiteRT-LM ships an
  `EmbeddingEngine`, so runtime queries embed through it (`QueryEmbedder`) and build-time
  embedding runs through the same engine via `tools/embed/embed.py` on `litert-lm-api`, pinned to
  the same version. The manifest parity check compares every field and drops vectors loudly on any
  mismatch. Open: whether `EmbeddingEngine` accepts a bare `.tflite` (see `tools/embed/README.md`).
  The JVM strategy is explicitly refused by the plugin with an error telling the user SIDECAR or
  NONE.
- **Generation (phase 5).** The output gate runs buffered before any token is emitted, in that
  order as specified. One `Engine` per process is enforced by `EngineSlot` (a second creation is
  refused); one in-flight call at a time is enforced by `SingleFlight` (a new call cancels the
  previous). Both models unload on `onTrimMemory(TRIM_MEMORY_COMPLETE)` and the library keeps
  retrieving from BM25.
- **Clusters (phase 6).** `clustersFile` feeds precomputed answers matched by BM25 over the
  questions, so they work with no models loaded.
- **Eval (phase 7).** `queries.tsv` (~105 queries) asserts hit-rate@1 ≥ 70% and hit-rate@4 ≥ 85%;
  plus a golden byte-identical bundle test, a BM25 snapshot round-trip parity test, and a printed
  fixture ranking for human review. Degradation behavior (BM25-only, no generator) is expressed in
  the `Ready` state flags rather than a separate matrix, deliberately instead of instrumented tests.

Recorded deviations from the original plan: the extension splits `sidecarCommand` into
`sidecarExecutable` / `sidecarScript` / `sidecarArguments` (the script is a content-tracked file
input, which a command list cannot express); the plugin is three tasks (parse/embed/pack) wired
through the AGP Variant API rather than `mergeAssets` by name; embedding defaults to off so a
fresh checkout builds BM25-only; `VectorIndex` floors near-zero cosine scores so fusion cannot
promote noise.

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
