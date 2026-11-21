# Spec: Kotlin Multiplatform + iOS

- Status: **draft** (not agreed; do not build until the owner says so)
- Risk tier: **medium** (public SDK contract and build system change; no auth, money, or user data)
- Scope: this repository only (LocalRAG). Nothing here goes into the platform repo or any service repo.

## Goal

Run the LocalRAG library and the demo app on iOS as well as Android, from one Kotlin codebase.
The Android app keeps working at every step. The bundle format does not change.

## Decisions already made by the owner

- Scope: library and demo app. Demo UI becomes Compose Multiplatform (CMP).
- iOS engine: use what LiteRT-LM recommends. Findings below.
- Process: spec first, then phased build with Muse, each phase reviewed before merge.

## What LiteRT-LM offers for iOS (read from Google's docs, Nov 2026)

- Official Swift API, Swift Package Manager, product `LiteRTLM`, repo `google-ai-edge/LiteRT-LM`. Status: **Early Preview**.
- CPU and Metal GPU on iOS. No NPU on iOS.
- `Engine`, `Conversation`, `sendMessage`, `sendMessageStream`, tools, `SamplerConfig(topK, topP, temperature)`, `maxNumTokens`, writable `cacheDir`. Same `.litertlm` model files as Android.
- **No Kotlin on iOS.** The Kotlin API is documented for Android and JVM only. So the iOS engine must be written in Swift.
- **Unverified:** a Swift `EmbeddingEngine`, a cancel API, the minimum iOS version, and pinning Swift package version against the Android `litertlm = 0.17.0`. Each needs a spike before it is relied on.

## Design

### Module layout (target)

```
localrag-tooling/localrag-core     KMP: jvm + iosArm64 + iosSimulatorArm64   (was JVM only)
localrag-tooling/localrag-gradle-plugin   unchanged, consumes the jvm variant of core
localrag/                          KMP SDK: androidTarget + iOS targets      (was localrag-android)
localrag-ios/                      Swift package: LiteRT-LM engine implementing the SDK seams
shared/                            KMP + CMP demo: domain, data, ui, view models
androidApp/                        thin Android shell (was :app)
iosApp/                            thin Xcode shell, links the shared framework
```

### Core (`localrag-core`)

- Index, tokenizer, chunker, front matter, cluster matcher, output gate, BM25, RRF, vector index move to `commonMain` unchanged in behaviour. This is what keeps CI-built indexes and on-phone queries identical, now on iOS too.
- JVM-only pieces to replace in common code: `java.time.LocalDate/ChronoUnit` → `kotlinx-datetime`; `java.nio.ByteBuffer` → explicit little-endian byte helpers; `java.util.zip` and `java.io` streams → `okio` (zip file system) or `kotlinx-io`. `BundleWriter` may stay in `jvmMain` (only the plugin writes bundles). `BundleReader` must be common.
- The rule "no Android, no LiteRT in core" stays. `verifyNoPlatformDependencies` is extended to every target's classpath, not just the JVM one.
- The Kotlin language level pin (2.2, so the Gradle plugin can load core) stays for the jvm target. Unverified whether the KMP Gradle plugin allows that in an included build; spike in phase 1.

### SDK (`localrag`)

- Public API moves to `commonMain`. `Context` and `File` leave the public signature: a `LocalRagPlatform` value (Android: application context; iOS: nothing) and string paths in the config.
- Two seams, both internal to the SDK but implementable from Swift:
  - `GenerationEngine { generate(system, prompt): String? ; close() }`
  - `EmbeddingEngine { dimensions; embed(text): FloatArray?; close() }`
  Android actuals wrap `litertlm-android` (today's `Generator` and `QueryEmbedder`). iOS gets Swift implementations passed in at `LocalRag.create(...)`. Kotlin cannot call Swift directly, so Swift implements a Kotlin-exported interface; no LiteRT type crosses the public API (existing rule holds).
- The agent loop (`AgentRunner`, `AgentProtocol`, `OutputGate`, `SingleFlight`, `Retriever`, `BundleLoader`) is already engine-agnostic text in, text out: it moves to `commonMain`. `Dispatchers.IO` → `Dispatchers.Default`-based IO dispatcher; `AtomicBoolean` → Kotlin atomics (opt-in) or atomicfu; `Log` → small logger seam.
- Memory: Android `onTrimMemory` and iOS `didReceiveMemoryWarning` both call one public `unload()`. The "one engine per process" `EngineSlot` rule applies on iOS too.
- iOS ships with **BM25-only retrieval until a Swift embedder is proven** (a supported state per `AGENTS.md`). Generation on iOS needs the Swift engine from phase 4.
- AGP 9 changes how KMP Android libraries are declared (new Android-KMP library plugin). **Unverified**; confirmed in phase 3.

### Demo app (`shared`, `androidApp`, `iosApp`)

| Today | Becomes |
|---|---|
| Hilt (+KSP) | Koin (Hilt is JVM-only) |
| `androidx.lifecycle` ViewModel, `SavedStateHandle`, `hiltViewModel` | KMP `lifecycle-viewmodel`, `koinViewModel`; nav args via type-safe routes |
| `navigation-compose` | CMP navigation (same API) |
| `java.time`, `NumberFormat`, `Locale`, `UUID` | `kotlinx-datetime`, small `expect/actual` money/number formatter (must keep identical figure strings), `kotlin.uuid` |
| `Context`, `File`, `Log`, `Build`, `ComponentCallbacks2` | `expect/actual` for model file locator, settings store, logger, memory-warning hook |
| `material3-adaptive-navigation-suite` | keep if CMP ships it; else `NavigationBar` (**Unverified**) |
| Android resources (drawables, manifest) | CMP resources |

- Domain and use cases do not change except the date and number types. Layering rules from `AGENTS.md` still hold.
- Docs bundle: the plugin keeps writing `docs.localrag`. Android reads it as an asset; iOS reads it from the app bundle via a Gradle task wired into an Xcode run-script phase.
- Model on iOS: not bundled (2.5 GB). Simulator: copy with `xcrun simctl` into the app container (new `scripts/push_model_ios.sh`). Device: same script via the file sharing or a debug download. Simulator runs CPU; speed is judged on a physical iPhone only.

## Phases (each lands on its own branch; each under ~400 changed lines where it can, bigger ones are split)

0. **Baseline.** Record current output: golden bundle hash, ranking fixture, all unit tests green, `:app:assembleDebug` green. Done when the baseline script exists and passes.
1. **Core to KMP.** Move to common, add iOS targets, replace JVM APIs. Done when JVM tests pass unchanged, iOS-native tests pass, and the golden bundle is byte-identical.
2. **Plugin check.** Plugin still applies and `generateLocalRagBundle` still byte-identical. Usually zero code; a phase so it cannot be skipped.
3. **SDK to KMP.** `localrag` module, seams, Android actuals, `commonTest`. Android demo still passes its tests and the Maestro suite.
4. **Swift engine.** `localrag-ios` Swift package, spike the unverified items (embedder, cancel, min iOS, version pin), implement `GenerationEngine`.
5. **Demo to CMP.** `shared` + `androidApp`, Hilt → Koin. Android behaviour unchanged.
6. **iOS app.** `iosApp`, bundle packaging, model push script, runs on simulator; Maestro iOS flows.
7. **Docs.** `AGENTS.md`, `README.md`, `tools/embed` notes updated; this spec marked done.

## Acceptance tests (given / when / then)

| # | Given | When | Then | Lives in |
|---|---|---|---|---|
| A1 | The golden docs fixture | Core runs on JVM and on iOS native | Same chunk ids and same top-4 ranking for every row of `queries.tsv` | `localrag-core/src/commonTest/.../ParityTest.kt` |
| A2 | The fixture docs | `generateLocalRagBundle` runs after the move | Bundle bytes equal the pre-move golden | `localrag-core` `EvalHarnessTest` (existing golden test, kept) |
| A3 | Hit-rate queries | Eval runs on iOS native | hit-rate@1 ≥ 70% and @4 ≥ 85%, as on JVM | `commonTest/.../EvalHarnessTest.kt` |
| A4 | A bundle with vectors and a mismatched manifest | SDK loads on either platform | Vectors dropped, BM25 used, reason logged | `localrag/src/commonTest/.../BundleLoaderTest.kt` |
| A5 | No model on device | Assistant asked a docs question | Fixed "no information" fallback on both platforms | `shared/src/commonTest/.../ChatViewModelTest.kt` |
| A6 | A generated answer with a figure not in the data | Output gate runs | Rejected, verbatim figure rule intact | `localrag/src/commonTest/.../OutputGateTest.kt` (moved from core tests) |
| A7 | Two calls in a row | Second starts before first ends | First cancelled, one in flight | `commonTest/.../SingleFlightTest.kt` |
| A8 | Any commonMain or iOS classpath | `verifyNoPlatformDependencies` runs | Fails on any Android or LiteRT artifact in core | `localrag-core/build.gradle.kts` task |
| A9 | Portfolio fixtures | Figures are formatted on JVM and on iOS | Strings are identical (e.g. grouping, currency symbol, sign) | `shared/src/commonTest/.../FormattingTest.kt` |
| A10 | iOS simulator, docs bundle, no model | App launched, docs question asked | Docs search returns ranked passages, assistant shows the fallback | `maestro/flows/ios/` |
| A11 | iPhone with Gemma model pushed | Docs question asked | Grounded answer through `runAgent` on the Swift engine | manual, recorded in `maestro/report/` |
| A12 | The Android demo after phase 5 | Existing Maestro suite runs | Same ten questions pass as before | `maestro/run_assistant_tests.sh` |

## Rules check

- Core stays free of Android and LiteRT: kept and now enforced on every target.
- No LiteRT type in the public API: kept; Swift implements SDK seams only.
- Model never produces a number: unchanged; figure formatting becomes common code with a parity test (A9).
- BM25-only is a supported state: this is the iOS default until a Swift embedder is proven.
- Plugin configuration-cache safe: untouched.
- **Bends:** (1) public SDK package and artifact change, see decision D2; (2) Hilt removed from the demo, see D3.

## Risks

- Swift LiteRT-LM is Early Preview; its API can change. Mitigation: one Swift file owns all LiteRT calls; version pinned.
- KMP plugin + AGP 9 + Gradle 9.5 + an included build is untested together. Mitigation: phase 1 spike first, stop if it fails.
- CMP on iOS rendering differences. Mitigation: screenshots in the phase 6 report.
- 2.5 GB model on a simulator is slow and uses a lot of disk. Mitigation: CPU backend, small Gemma 3 270M for simulator checks.
- Muse writes code; Claude reviews. Phase 1 and the Swift engine are the two places Claude should read every line.

## Decisions for the owner

- **D1. iOS minimum version and devices.** Recommend iOS 17+, iPhone only, simulator on Apple silicon (no x86 simulator). Why: Metal GPU and the memory a 2.5 GB model needs.
- **D2. Rename `localrag-android` to `localrag` and move the package to `com.ayushig.localrag`.** Recommend yes: version is 0.1.0 and only the demo consumes it, so the break is free now and costly later. Alternative: keep the old names and add an iOS-only module (worse, two public APIs).
- **D3. Hilt → Koin in the demo.** Recommend Koin: smallest change that works on iOS. Alternative: hand-written DI (more code, no dependency).
- **D4. iOS ships BM25-only plus generation, no embeddings, until the Swift spike shows a usable embedder.** Recommend accept.
- **D5. Keep the Android demo module path `:app`?** Recommend rename to `:androidApp` for clarity; Maestro and scripts need a small update.
- **D6. Physical iPhone available for the on-device check (A11)?** Needed for speed numbers and the GPU path. Needs your answer.
