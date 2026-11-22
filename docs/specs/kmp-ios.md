# Spec: Kotlin Multiplatform + iOS

- Status: **done** (2026-11-22). Built per Appendix C.
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

## Decisions for the owner (all settled 2026-11-21: recommendations accepted)

- **D1. iOS minimum version and devices.** Recommend iOS 17+, iPhone only, simulator on Apple silicon (no x86 simulator). Why: Metal GPU and the memory a 2.5 GB model needs.
- **D2. Rename `localrag-android` to `localrag` and move the package to `com.ayushig.localrag`.** Recommend yes: version is 0.1.0 and only the demo consumes it, so the break is free now and costly later. Alternative: keep the old names and add an iOS-only module (worse, two public APIs).
- **D3. Hilt → Koin in the demo.** Recommend Koin: smallest change that works on iOS. Alternative: hand-written DI (more code, no dependency).
- **D4. iOS ships BM25-only plus generation, no embeddings, until the Swift spike shows a usable embedder.** Recommend accept.
- **D5. Keep the Android demo module path `:app`?** Recommend rename to `:androidApp` for clarity; Maestro and scripts need a small update.
- **D6. Physical iPhone available for the on-device check (A11)?** Needed for speed numbers and the GPU path. Needs your answer.

---

# Appendix A. Failure analysis

Read from the current code on `main` (`ebf7086`). Items marked **Unverified** are not confirmed and each has a spike in "Spikes" below. Likelihood and impact are my estimate: H high, M medium, L low.

## A1. Silent failures (worst kind: nothing errors, quality drops)

| # | Failure | Where in today's code | L / I | Detected by | Mitigation |
|---|---|---|---|---|---|
| F1 | Tokenizer or regex behaves differently on iOS, so BM25 tokens differ and ranking drifts | `text/Tokenizer.kt` (`lowercaseChar`, character classification), `Markdown.kt`, `Chunker.kt`, `FrontMatterParser.kt` (regexes with `DOT_MATCHES_ALL`, `\d`, `\s`). JVM and Kotlin/Native use different Unicode tables and regex engines | M / H | A1, A3 with a Unicode corpus | Parity test includes accents, Devanagari, curly quotes, em dash, `₹`, emoji, NBSP. Prefer explicit char ranges over `\d`/`\s` in index paths |
| F2 | Same query, different tie order (floating point last-digit, or map iteration order) | `Bm25Index`, `ReciprocalRankFusion`, `VectorIndex` | M / M | A1 compares ranked ids, scores with 1e-9 tolerance | Deterministic tie-break by chunk id. Grep core for `HashMap`/`HashSet` iteration that reaches a ranking |
| F3 | Figure strings differ on iOS, so the output gate rejects true answers | `core/Formatters.kt` uses `NumberFormat` and `Locale.US`. The gate matches `\d(?:[\d,.]*\d)?` verbatim (`OutputGate.kt:96`). A different separator, narrow NBSP, or U+2212 minus breaks it | H if a platform formatter is used / H | A9 | Hand-written formatter, fixed locale, no platform formatter. Test with the device locale set to `de_DE` and `hi_IN` |
| F4 | Date text differs (`d MMM yyyy, HH:mm`, local zone) | `Formatters.asOf`, `ZoneId.systemDefault()` | M / L | A9 | Explicit English month table, injected clock and zone in tests |
| F5 | Vectors read in the wrong byte order or wrong length | `BundleReader.toFloatArrayLittleEndian` | L / H | A2, A4 | Explicit little-endian decode; round-trip test on iOS with a real vectors bundle |
| F6 | `BundleReader` cannot read the JVM-written zip on iOS (data descriptors, deflate, entry order) | `BundleWriter` streams entries with `ZipOutputStream`; reader walks `ZipInputStream` | M / H (loud, not silent) | A2 on iOS | Reader reads whole bytes then parses. Spike: okio zip vs a minimal own reader. Golden bundle committed as a test resource and read on both platforms |
| F7 | The "core is pure" check passes while checking nothing | `verifyNoPlatformDependencies` reads `runtimeClasspath`, which does not exist on a KMP module the same way. CI calls it by name | H / H | A8, CI | Rewrite the task for every target's compile classpath. Add a test that deliberately adds a forbidden dependency and expects red |

## A2. Crashes and data loss

| # | Failure | Where | L / I | Detected by | Mitigation |
|---|---|---|---|---|---|
| F8 | iOS kills the app (memory) with a 2.5 GB model loaded | `Generator` holds one `Engine`; Gemma 4 E2B is ~2.58 GB. iPhones with 6–8 GB RAM have a per-app limit well under that. **Unverified:** the exact limit for this model | H / H | A11, a memory soak flow | Request the increased-memory-limit entitlement; offer the 270M model as the iOS default; unload on memory warning; show the device RAM in the debug line. Decide the default model in spike S4 |
| F9 | Memory warning arrives on the main thread while a generation runs on a background thread, and the engine is closed mid-call | `LocalRag.unloadModels()` closes `generator` while `runAgent` may be inside `generate`. Fields `bundle`, `retriever`, `embedder`, `generator`, `config` are plain `var`s with no lock. **This race exists on Android today**; iOS warnings are far more frequent | M / H | New test: unload during a running turn | `unloadModels` goes through `SingleFlight` and waits for the in-flight call; state changes under one mutex |
| F10 | A new call "cancels" the previous one but the Swift generation keeps running, so two calls hit one native conversation | `SingleFlight.enter` cancels the old `Job` and does not wait for it. Kotlin cancellation does not stop a Swift `Task` by itself. **Unverified:** whether LiteRT-LM Swift can cancel (docs show no cancel API) | M / H | A7 with the Swift engine, plus a slow scripted engine | Seam gets `cancel()`. `SingleFlight` joins the previous owner before starting the next. Swift side wraps generation in a cancellable `Task` and checks `Task.isCancelled` between chunks |
| F11 | Two engines alive at once after a model switch (`updateConfig` → release → create) because the old Swift object is freed later by ARC | `EngineSlot` counts Kotlin-side only | M / H | Model-switch flow, memory soak | `close()` on the seam must be synchronous and completed before `EngineSlot.release()`. Swift engine owns an explicit `shutdown()` |
| F12 | Downloaded-bundle swap leaves a half file or is lost | `BundleLoader.install` uses `File.renameTo` | L / M | New test | iOS: `replaceItemAt`, written to Application Support, excluded from backup. Keep the "newer content version wins" rule |
| F13 | Android's GPU probe and CPU fallback is Android-only, so iOS has no fallback when Metal loads but decodes garbage | `Generator.createOrNull`, `probeBackend` | M / M | A11 | Lift the policy (try GPU, probe for "OK", fall back to CPU, release the slot once) into common code over a `createEngine(backend)` seam |

## A3. Build and tooling

| # | Failure | Why | L / I | Mitigation |
|---|---|---|---|---|
| F14 | Kotlin 2.3.20 may not support Xcode 27.2 (very new). **Unverified** | Kotlin/Native pins supported Xcode ranges | M / H, and it blocks everything | Spike S1 first: build one `iosSimulatorArm64` target and run one test. If it fails, stop and pick the Kotlin version that supports the installed Xcode before any code moves |
| F15 | AGP 9 does not allow the old Kotlin-multiplatform + `com.android.library` pairing. **Unverified:** the new `com.android.kotlin.multiplatform.library` plugin and the root `buildscript` override of KGP (`build.gradle.kts` comment) | Build files already carry a version hack | M / H | Spike S1. Keep the hack comment up to date |
| F16 | KMP core inside an included build: the Gradle plugin and the Android SDK must pick the JVM variant, not another target | Dependency substitution with platform attributes can be ambiguous. `AGENTS.md` already records a Java 11 vs 17 attribute problem | M / H | Phase 2 gate. Keep `jvmTarget` 11 and the language-level pin on the jvm target |
| F17 | Compose Multiplatform version and Kotlin 2.3.20 do not line up; Material3 and navigation APIs differ from AndroidX | CMP has its own versioning | M / M | Spike S1 includes a hello-world CMP screen on both platforms |
| F18 | Swift package `LiteRTLM` (docs example `from: "0.12.0"`) vs Android `litertlm = "0.17.0"`: different versions may disagree on model file format. **Unverified** | `AGENTS.md` pins build-time and runtime embedding to one version | M / M | Pin the Swift package to the version matching 0.17.0 if one exists; otherwise record the gap in `AGENTS.md` and test the same `.litertlm` file on both |
| F19 | Docs bundle does not reach the iOS app. The plugin writes assets through the AGP variant API, which has no iOS equivalent | `localrag-gradle-plugin` | H / H (loud) | New plugin output dir for iOS, copied by an Xcode run-script phase; a build check that the file is in the `.app` |
| F20 | iOS CI cost and time | Needs a macOS runner; Kotlin/Native builds are slow; the model cannot be in CI | H / M | Scripted engine lane (see Appendix B). Real-model lane runs by hand or nightly on a Mac |
| F21 | Swift is Early Preview; API can change under a minor bump | Docs label | M / M | One Swift file owns all LiteRT calls; exact version pin; upgrade is its own change |

## A4. Maestro-specific traps found in today's flows

| # | Trap | Evidence | Fix |
|---|---|---|---|
| M1 | `- back` is used to hide the keyboard before tapping Send. iOS has no back key | every question in `01_portfolio`, `02_docs`, `03_honesty` | Use `hideKeyboard`, or tap a neutral area; one common flow |
| M2 | Runner is Android-only: `adb devices`, `adb logcat`, `adb exec-out screencap`, `adb shell cmd package` | `run_assistant_tests.sh` | Split into `lib/android.sh` and `lib/ios.sh` (`xcrun simctl install/launch/io screenshot/spawn log`) |
| M3 | Logs for the report come from logcat tags `LocalRagChat`, `LocalRagAgent`, `LocalRag`. Kotlin `println` on iOS is not in the unified log | runner | iOS logger seam writes via `os_log` with subsystem and category; runner filters the same three names |
| M4 | UI elements are found by visible text and `contentDescription` ("Ask something", "Send", "More", "Clear chat"). Compose on iOS may not expose placeholder text to the accessibility tree. **Unverified** | `ChatScreen.kt:476–510` | Add `testTag` on every element a flow touches; flows use `id:`. Spike S2 confirms CMP maps tags to `accessibilityIdentifier` |
| M5 | `visible:` is a regex. The fallback text has `.` and `—`; the ready chip has `·` | flows | Keep strings in one `maestro/strings.yaml` (or env) and escape them; assert on a test tag where possible |
| M6 | Answers depend on the real model choosing the right tool in the strict grammar, so the 10-question suite can flake or time out (3 min per turn, 30 min total, CPU-only simulator is slower) | `maestro/README.md` | Add a deterministic lane with a scripted engine (Appendix B) and keep the real-model suite as the second lane |
| M7 | Model file lives in the app sandbox. Reinstall or `clearState` wipes it; the container path changes | `ModelFileLocator` uses `getExternalFilesDir` | `push_model_ios.sh` resolves the container with `simctl get_app_container … data` every run; flows never `clearState` except the no-model flow |
| M8 | Open-source Maestro drives the iOS **simulator** only. A physical iPhone cannot be driven by it **(Unverified: confirm for the installed Maestro version)** | Maestro docs | A11 stays a manual, recorded run |
| M9 | Ready label differs by platform: iOS has no embedder at first, so it reads `ready · bm25` even on an embeddings build | `ChatScreen.kt:584` | Expected label is a per-platform value in the runner |

## A5. What I could not check from the code

- Whether `Dispatchers.IO` and the Kotlin atomics API are stable enough on Kotlin/Native in the chosen versions (both likely fine; confirm in S1).
- Swift `EmbeddingEngine`, the cancel API, the minimum iOS version (docs silent).
- How many chunks the docs corpus makes and whether `queries.tsv` has enough Unicode rows to catch F1 (needs a read of the fixture in phase 0).
- `DocsSearchScreen` strings. The Maestro flows below name them as placeholders to fill from the file.

## A6. Spikes (cheap, early, each can stop the project)

| Spike | Question | Done when | Kills the plan if |
|---|---|---|---|
| S1 | Does Kotlin 2.3.20 + AGP 9.3.2 + Gradle 9.5 + CMP build for iosSimulatorArm64 on Xcode 27.2, with an included build? | One common test passes on JVM and iOS sim; one CMP screen shows on both | Xcode unsupported and no Kotlin version fixes it |
| S2 | Does CMP on iOS expose test tags and text to Maestro? | Maestro taps an `id:` and reads text on a CMP screen on the simulator | Tags do not surface (fall back to native SwiftUI shell for tests) |
| S3 | Swift LiteRT-LM: load a `.litertlm`, generate, cancel, and embed? | A Swift command-line or small app prints a reply; cancel behavior recorded | Cannot cancel, or generation unusable on device |
| S4 | Does the model fit on target iPhones? | Peak memory measured for E2B and 270M on a real device | Neither fits; iOS ships retrieval-only |

---

# Appendix B. Maestro test plan: a full RAG app on both platforms

Goal: prove the whole chain on a running app, not just the parts. The chain is: Markdown docs → build-time index → bundle packed into the app → bundle loaded on device → retrieval → agent turn with the app's tools → output gate → label and text on screen. Same flows on Android and iOS.

## B1. Four lanes

| Lane | What runs | Model | Where | When | Why |
|---|---|---|---|---|---|
| L1 | Unit and parity tests (`commonTest`, JVM and iOS sim) | none | CI (macOS for iOS) | every push | Fast, catches F1–F7 |
| L2 | **Scripted lane**: Maestro on the real app with a scripted generation engine | none (fake engine returns canned plans) | CI emulator + CI simulator | every PR | Deterministic. Exercises real bundle, real retrieval, real `AgentRunner`, real tools, real gate, real UI. Only the model is replaced |
| L3 | **Real-model lane**: today's 10 questions plus the new flows | Gemma (270M on simulator, E2B on device) | Developer Mac, Android device | before merge of phases 3, 5, 6; nightly | Proves the model can drive the grammar |
| L4 | Manual physical iPhone | E2B | iPhone | phase 6 and release | Speed, GPU, memory (F8) |

The scripted engine is a `GenerationEngine` implementation (the seam from the spec). It returns plan text in the real agent grammar, taken from the fixtures `AgentRunnerTest` and `AgentProtocolTest` already use. It is selected by a debug launch argument (`launchApp: arguments:`), never in release builds. It also supports a slow mode (adds delay) for cancel and memory flows.

## B2. What gets asserted

Only code-owned text and ids, never model wording (same rule as today). Ids come from `testTag`s added in common code.

| Fixed output | Source |
|---|---|
| `from your account`, `from the help documentation` | `ChatScreen.kt:291-292` |
| the fallback sentence word for word | `NoInformationFallback` |
| `ready · bm25` / `ready · hybrid` | `ChatScreen.kt:584` |
| doc titles in retrieval results | front matter in `app/src/main/docs/*.md` |
| bundle line: chunk count and content version | **new** debug line in the chat top bar (reads `LocalRagState.Ready`) |

## B3. Flows

Numbering keeps today's 01–03; scripted flows reuse the same questions so the two lanes compare directly. Platform differences use `runFlow: when: platform:`.

| Flow | Lanes | Steps | Pass condition |
|---|---|---|---|
| `00_boot` | L2, L3 | launch, wait for ready chip, read bundle line | chip is the expected value for the platform (M9); chunk count and content version equal the values the build printed |
| `10_retrieval` | L2, L3 | open Retrieval tab, search each query from `queries.tsv` (flow generated from the file) | top-1 title equals the expected title for every row, same on Android and iOS |
| `11_retrieval_empty` | L2 | search a nonsense string | empty state shown, no crash |
| `20_portfolio` | L2, L3 | today's `01_portfolio` questions | `from your account` after each; "Find my holding" shows the fallback |
| `21_docs` | L2, L3 | today's `02_docs` | `from the help documentation` after the three covered questions; password reset shows the fallback |
| `22_honesty` | L2, L3 | greeting, "Should I buy more stocks?" | no label, no fallback on the greeting; refusal text on the advice question |
| `23_cancel` | L2 (slow mode) | send, Stop, send again | second answer arrives; no double answer; log shows one call in flight (F10, A7) |
| `24_followup` | L2, L3 | ask worth, then "and the profit?" | second answer carries `from your account`; history never counted as grounding |
| `25_model_switch` | L2, L3 | overflow menu, switch model, ask | engine reloads, ready chip returns, answer arrives; no second engine (F11) |
| `30_memory_warning` | L2 | debug deep link triggers the same path as `onTrimMemory` / `didReceiveMemoryWarning`, then ask a docs question | chip drops to bm25-only and retrieval still works; generation falls back; next load recovers (F9) |
| `31_relaunch` | L2, L3 | stop and relaunch | selected model persists; chat starts clean |
| `40_no_model` | L2 | fresh state, no model file | missing-model banner shown; docs search still works; assistant shows the fallback |
| `50_bad_bundle` | L2 | launch argument points at a fixture bundle with a mismatched embedding block | vectors dropped, BM25 used, reason logged (A4) |
| `60_embeddings` | L3, Android only | build with `-PlocalRagEmbed=true` | `ready · hybrid` on Android; `ready · bm25` on iOS until a Swift embedder exists |

Fixtures: a corrupted bundle, a mismatched-embedding bundle, and the real bundle, all produced by the plugin so the test also covers the plugin.

## B4. Runner

- `maestro/run_assistant_tests.sh --platform android|ios --lane scripted|model`.
- Platform helpers: `maestro/lib/android.sh` (adb) and `maestro/lib/ios.sh` (`xcrun simctl`: boot, install, launch with arguments, `io booted screenshot`, `spawn booted log show --predicate`). Shared report format stays: `REPORT.md`, per-flow log, screenshot after each flow, filtered log.
- `scripts/push_model_ios.sh e2b|270m` mirrors `push_model.sh`. It resolves the data container each run (M7).
- Ready label and the per-platform expectations come from one file, not from `sed` into run copies. Keep the run-copy trick only if Maestro still cannot interpolate in that position.
- Exit code is non-zero on any failed flow, so CI can gate on it.

## B5. Which tests gate which phase

| Phase | Must be green |
|---|---|
| 1 | L1 on JVM and iOS sim; A1, A2, A3, A8; S1 done |
| 3 | L1; L3 Android suite unchanged (A12) |
| 4 | S3 and S4 done; Swift engine unit tests; L1 |
| 5 | L2 Android full; L3 Android |
| 6 | L2 on iOS simulator; L3 on iOS simulator with 270M; L4 once |
| 7 | all lanes; docs updated; this spec set to `done` |

## B6. Not covered, on purpose

- Exact model wording and exact figure text from the model. Figures are checked by unit tests (A9) and by reading screenshots.
- Speed and battery. Measured by hand in L4.
- Over-the-air bundle download: the seam exists, no downloader is built.

---

# Appendix C. Factory execution plan (sf-conductor / sf-build)

Rule from the factory: a script does deterministic work, Muse does reading and bulk writing, Claude owns intent, failing tests, gates, concurrency and review verdicts. The failure analysis (Appendix A) is the research that makes each Muse brief precise: every brief below quotes the files, lines and traps it needs, so Muse does not rediscover them.

## C1. Branches and gates

- Integration branch: `kmp-ios` (cut from `main` after this spec merges). Muse tasks run in sibling worktrees `.wt-<slug>` on `oc/<slug>`, cut from `kmp-ios`.
- Claude merges a task into `kmp-ios` only when the owner says "merge" for it. Nothing goes to `main` without the owner's explicit go-ahead; the final `kmp-ios` → `main` pull request is the last gate.
- Briefs, Muse logs and the shared research file live outside the repo (`~/Ayushi/.factory-kmp-ios/`), so nothing factory-related lands in this repo or in any platform repo.
- Each task is at most 400 changed lines and at most 3 review rounds, on the primary model first. Escalate only per the sf-build policy. The allowance script runs before every Muse launch.
- Every task ends with evidence: commands run, real output tails, what was not run.

## C2. Before any Muse run (Claude, scripts)

| Step | Who | Done when |
|---|---|---|
| M0 Muse sandbox check | Claude, one scratch task | Muse can run `./gradlew -p localrag-tooling :localrag-core:test` in a worktree. **Unverified** whether its sandbox has the Android SDK or Xcode. Anything it cannot run (Android build, `xcodebuild`, emulator, Maestro) is run by Claude after each round |
| `scripts/verify.sh` | Claude writes (deterministic, not Muse) | One command prints an evidence table: core tests (JVM, then iOS sim), SDK tests, demo unit tests, `assembleDebug`, `verifyNoPlatformDependencies`, golden-bundle hash. Tied to the head commit. The existing GitHub workflow is ubuntu-only and cannot run iOS, so this local script is the iOS gate |
| Baseline | script | `verify.sh` green on `main`; golden bundle hash and `queries.tsv` ranking stored as test resources |
| Failing tests first | Claude | A1–A9 skeletons committed, red for the right reason, before Muse starts the phase they belong to |

## C3. Delegation matrix

"Tests" always means Claude writes them first and Muse makes them pass without editing them. Phase numbers match the Phases list in the spec body.

| Phase | Task | Executor | Brief must carry (from the research) |
|---|---|---|---|
| 0 | `verify.sh`, golden capture | Claude + script | existing commands from `AGENTS.md`; CI file |
| 0 | Risk scan of core for F1 and F2 | Muse, read-only (`muse-read.sh`) | list every `HashMap` iteration that reaches a ranking, every regex using `\d`/`\s`/`DOT_MATCHES_ALL`, every `lowercase`/`isLetterOrDigit` in `text/`, `document/`, `index/`; output is a capped table of `path:line`. Claude verifies the cited lines |
| 1 | S1 toolchain spike | Claude (version choice is an architecture decision) | F14–F17: Kotlin 2.3.20 vs Xcode 27.2, AGP 9 KMP plugin, root `buildscript` KGP override, language-level pin, included build |
| 1 | `git mv` core to `commonMain`/`commonTest`, `jvmMain` for `BundleWriter` | Muse | exact file list (25 files), keep behaviour, no logic edits |
| 1 | `java.time` → `kotlinx-datetime` in `DocumentValidator` and the two document files | Muse | `today` becomes an injected value; `LocalDate.parse` failure must still map to null |
| 1 | `BundleReader` without `java.io`/`ByteBuffer`; little-endian decode | Muse after Claude's tests | F5, F6: committed golden bundle read test, vectors round-trip, zip written by `BundleWriter` (data descriptors) must read on iOS |
| 1 | Parity harness + Unicode fixture | Muse writes harness; Claude picks fixture rows and expected ranking | F1, F2: ids and order compared, scores within 1e-9; accents, Devanagari, `₹`, curly quotes, em dash, NBSP, emoji |
| 1 | Rewrite `verifyNoPlatformDependencies` + negative test | Claude (it is a gate) | F7: every target classpath; test that adding a forbidden dependency turns it red |
| 2 | Plugin still builds the byte-identical bundle | script | `generateLocalRagBundle` hash equals baseline |
| 3 | Rename `localrag-android` → `localrag`, package change across SDK and demo imports | Muse (mechanical; one diff may exceed 400 lines as a pure move, say so in the brief) | decision D2; no logic edits; imports only |
| 3 | Seam interfaces, `SingleFlight` joins the previous call, `unloadModels` under one lock | Claude writes tests and the seam; Muse implements | F9, F10, F11: slow fake engine, unload during a running turn, model switch never has two engines. 3 runs for flakiness |
| 3 | Lift the GPU-probe + CPU-fallback policy to common over `createEngine(backend)` | Muse | F13: `Generator.createOrNull` and `probeBackend` are the source; behaviour must not change on Android |
| 3 | Android actuals wrapping `Generator`, `QueryEmbedder`; bundle load via platform file API | Muse | F12: `BundleLoader` uses `Context.assets`, `filesDir`, `File.renameTo`; `SignatureVerifier` takes `java.io.File` in the public API, so it must move to a path or bytes |
| 4 | S3 Swift spike (load, generate, cancel, embed) | Claude on a real device | F8, F10, F18 |
| 4 | Swift package `localrag-ios`: engine implementing the seams, unit tests with a fake | Muse writes from the Swift API facts in this spec; Claude builds and tests with `xcodebuild` | Swift `Engine`, `EngineConfig(modelPath:backend:maxNumTokens:cacheDir:)`, `sendMessageStream`, `SamplerConfig`; one file owns all LiteRT calls (F21); explicit `shutdown()` (F11); cancellation via `Task` (F10) |
| 5 | `git mv` `domain/`, `data/`, `ui/`, `core/` to `shared/commonMain` | Muse | file list from the app tree; Android build must stay green |
| 5 | `Formatters` common rewrite | Claude writes `FormattingTest` golden strings first; Muse implements | F3, F4: hand-written, fixed locale, English month table, same strings as today's `FormattersTest` |
| 5 | Hilt → Koin: `AppModule`, `LocalRagModule`, 4 ViewModels, 4 screens | Muse | the 20 files that mention Hilt; `hiltViewModel` → `koinViewModel`; `SavedStateHandle` nav args |
| 5 | `expect/actual` for `ModelFileLocator`, `ModelSelectionStore`, logger, memory hook | Muse | Android actual = today's code unchanged |
| 5 | `testTag` on every element Maestro touches; debug bundle line; scripted engine + launch argument | Muse | M4, B2, B1; debug-only, absent from release |
| 6 | `iosApp` shell, bundle packaging run-script phase | Muse drafts a text project description; Claude builds | F19; check the file is inside the `.app` |
| 6 | `scripts/push_model_ios.sh`, `maestro/lib/{android,ios}.sh`, runner split | Muse | M2, M3, M7; `xcrun simctl` commands |
| 6 | Maestro flows 00–60 | Muse writes from the B3 table; Claude runs them | M1, M4, M5, M9; `hideKeyboard`, `id:`, per-platform ready label |
| 7 | `AGENTS.md`, `README.md` updates | Muse drafts, Claude edits | folder layout, build commands, rules still true |

## C4. Review and verify

- Every diff is read in full by Claude before merge (sf-build step 7). For the concurrency tasks, the iOS engine and the verification gate, read every line twice.
- `sf-review` after phase 3 (engineering, QA), after phase 5 (design, QA) and after phase 6 (all lenses, plus ops for the runner). Muse lenses report; Claude verifies each cited `path:line`.
- Evidence: `verify.sh` table per pull request. Maestro lanes run on the developer Mac and are listed as "not run" when no device or simulator is available.
- Record every task with `record-review.sh`. Run `sf-retro` at the end of phases 3 and 6 and after any task that needed a Muse correction round.

## C5. What Muse cannot do here, so Claude does

- Run Xcode or the iOS simulator, drive Maestro, read a physical device (S3, S4, A11).
- Choose toolchain versions (S1) and decide the iOS default model (S4).
- Own `verifyNoPlatformDependencies`, the engine seams, `SingleFlight`, the output gate and its tests.

## C6. Open items before the first Muse run

1. D1–D6 answered (the spec stays `draft` until then).
2. Owner confirms the `kmp-ios` integration branch.
3. M0 result: what Muse's sandbox can run.
4. Autonomy level for this repo (how many merges the owner wants to approve one by one).

---

# Appendix D. Decisions and operating rules (2026-11-21)

- D1 iOS 17+, iPhone only. D2 rename to `localrag`. D3 Koin. D4 iOS without embeddings at first. D5 `:androidApp`. D8 branch and Muse sandbox check: yes.
- **D6 not answered.** Assumed: no physical iPhone. iOS is proven on the simulator (CPU), the iOS default model is Gemma 3 270M, and F8 (memory) stays marked unverified until a device check happens.
- **Approval:** Claude reviews every Muse diff and merges tasks into `kmp-ios` on green evidence. The owner does not approve individual merges.
- **Delivery:** when everything works (all lanes in B5 green, as far as the environment can run them, with the gaps listed), Claude opens one pull request `kmp-ios` -> `main` with the evidence bundle. The owner reviews and merges that one.
- Operating model: the owner gives intent; Claude asks only questions that change the outcome, has Muse write the code, reviews, tests, and hands over working code.

---

# Appendix E. Spike results and plan changes (2026-11-21)

| Spike | Result |
|---|---|
| S1 toolchain | **Passed.** Kotlin 2.3.20 + AGP 9.3.2 + Gradle 9.5/9.8 + Xcode 27.2 build and test for the iOS simulator. AGP 9 needs `com.android.kotlin.multiplatform.library` for KMP modules; works first try. Included-build substitution of a KMP core works (JVM variant is picked). CMP 1.12.2 and Koin 4.2.2 work; androidx navigation and lifecycle KMP 2.10/2.11 are built with a newer Kotlin than 2.3.20, so the pins are navigation 2.9.2 and lifecycle 2.9.6. |
| S3 Swift engine (macOS, CPU, E2B model, LiteRT-LM Swift 0.17.0, same version as Android) | **Passed.** Engine initializes in under a second when cached; the "reply with exactly: OK" probe returns OK, so the shared GPU probe and fallback policy applies unchanged. `sendMessageStream` stops within a millisecond of `Task.cancel()` (50 chunks, not the full 300): cancellation works, which F10 depended on. **Do not set `maxNumTokens` to 512**: the engine fails to allocate tensors ("DYNAMIC_UPDATE_SLICE"); leave it unset (default). **Swift 0.17.0 does ship an `EmbeddingEngine` actor** (the docs page does not mention it), so iOS hybrid retrieval is possible; D4 stays as the first-pass default and the embedder moves to a follow-up. SwiftPM minimum iOS is 15; our target stays iOS 17. |
| S4 memory on a physical iPhone | **Open.** Needs the iPhone reconnected. E2B is 2.4 GB. |
| F6 zip reading on iOS | Passed: okio `openZip` reads the JVM-written bundle; golden test on the simulator. |
| F1 and F2 parity | Passed: 45 tokenizer traps, 8 documents, 22 queries and a 1,700-value formatter golden agree between JVM and the iOS simulator. |

## Changes to the plan

- The Swift engine lives in the iOS app target (`iosApp/`), not in a separate `localrag-ios` package. A separate package would need a binary dependency on the Gradle-built Kotlin framework, which is a build output. Host apps copy the three small Swift files.
- `NavigationSuiteScaffold` is replaced by `Scaffold` + `NavigationBar` in common code (the adaptive suite is not in stable Compose Multiplatform).
- Muse found and Claude confirmed the concurrency defects listed in the task records (SingleFlight chain, embedder closed under a query, lost model switch). They are fixed and pinned by tests.
