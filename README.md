# LocalRAG

An Android app that pairs a **fintech app UI** with an **in-app assistant**. The assistant answers
FAQ-style and account-specific questions so users can understand the app, their account status, and
what to do next — without leaving the screen they're on or filing a support ticket.

## What we're building

Two halves that work together:

**1. The fintech app UI**
A Compose-based fintech client — portfolio home and category detail screens, an assistant tab,
and a documentation retrieval tab. This is the surface the user actually uses, and its
repository data is also the source of truth the assistant answers from.

**2. The assistant**
A conversational help assistant embedded in the app. It handles the kinds of questions users
ask support:

- **FAQ / product questions** — "How do I deposit funds?", "What are the brokerage charges?",
  "How do I close my account?"
- **App status and account questions** — "What is my portfolio worth?", "How are my metals
  doing?", "What is my margin status?", "Is my KYC verification complete?"
- **Follow-ups** — "and the profit?", "what about metals?" — resolved against the recent
  turns passed in with each request

Answers are grounded in retrieved context — product docs, FAQ content, policy text, and the user's
own in-app state — rather than generated free-hand. That grounding is the *RAG* in LocalRAG, and
keeping retrieval and user data **on-device** is the *Local*.

## Why on-device

Fintech data is sensitive. Account balances, transaction history, and KYC status should not be
shipped to a third party to answer "why is my transfer pending?". Keeping the index and the
user-context lookup local means the assistant can be specific about the user's situation without
that data leaving the device.

## Current state

The repository holds two things: the **LocalRAG library** (`localrag-tooling/localrag-core`,
`localrag-tooling/localrag-gradle-plugin`, `localrag-android` — see `AGENTS.md`) and the **demo
app** (`:app`), which is the library's first consumer:

- Fintech UI — portfolio home and category detail screens backed by repository data.
- Assistant — a chat surface answering portfolio questions from formatted data (figures are
  rendered, never generated) and documentation questions through agent turns over the
  library, with a pushed Gemma model. Turns show a working card with live tool steps while
  running and expandable function/document details afterwards; anything unanswerable meets
  a fixed fallback, never a guess.
- Documentation search screen driving the library's `retrieveOnly`, plus a Markdown corpus in
  `app/src/main/docs` that the Gradle plugin indexes into the app bundle at build time.

The app follows the layer structure below, except it uses `ui/` rather than `presentation/`.

## Architecture

**MVVM + Clean Architecture.** Three layers, with dependencies pointing inward: `presentation`
depends on `domain`; `data` depends on `domain`; `domain` depends on nothing.

```
┌──────────────────────────────────────────────────────────┐
│  presentation        Compose screens, ViewModels,        │
│                      UI state + UI events                │
└───────────────────────────┬──────────────────────────────┘
                            │ calls use cases
┌───────────────────────────▼──────────────────────────────┐
│  domain              entities, repository interfaces,    │
│  (pure Kotlin)       use cases — no Android imports      │
└───────────────────────────▲──────────────────────────────┘
                            │ implements interfaces
┌───────────────────────────┴──────────────────────────────┐
│  data                repository impls, local/remote      │
│                      data sources, DTOs + mappers        │
└──────────┬──────────────────────────────┬────────────────┘
           │                              │
┌──────────▼─────────────┐   ┌────────────▼────────────────┐
│  Knowledge index       │   │  App state                  │
│  FAQ, policy, product  │   │  accounts, txns, KYC,       │
│  docs (embedded, local)│   │  card status (local store)  │
└────────────────────────┘   └─────────────────────────────┘
```

**Layer rules:**

- **domain** — pure Kotlin. Entities, repository *interfaces*, and assistant models. No Android
  or framework types, no Compose, no Room/Retrofit.
- **data** — implements the domain repository interfaces, plus the assistant's tool
  definitions and prompt copy. Owns data sources, DTOs, and mappers between DTO ↔ domain
  entity. Domain types never leak DTOs outward.
- **presentation** (`ui/`) — Compose screens are stateless and driven by an immutable UI state
  exposed as `StateFlow` from a ViewModel. ViewModels call use cases, never repositories or
  data sources directly — except the chat screen, which drives the LocalRAG SDK and the
  app-owned tools directly. User actions go in as UI events.

Feature packages under `ui/` group screen + ViewModel + UI state together; `domain` and
`data` are grouped by layer-then-type. The on-disk layout is shown under Current layout
below.

**The assistant path through the layers:** a question enters `ChatViewModel`, which assembles
the recent turns as history and calls the SDK's `runAgent` with the app's tool definitions.
The SDK plans tool calls in a strict grammar, executes them against `data/` (portfolio
repository, `retrieveOnly` over the local bundle), gates the final text, and returns an
outcome. The ViewModel streams the text into UI state with the tool calls and source
titles the answer was built from, and the chat screen renders them as expandable details.

**Decided, and built:**

- DI framework — Hilt, wired up in `di/`
- On-device inference runtime — Gemma 4 E2B IT or Gemma 3 270M IT on LiteRT-LM, pushed
  with `scripts/push_model.sh e2b|270m` and switchable in the assistant's overflow menu
  (the small model runs the same grammar, tools, and gate, just faster and weaker)
- Retrieval is BM25-only by default; the embedding path (build-time sidecar plus runtime
  `QueryEmbedder`) exists but no embedding model ships, so hybrid retrieval is unproven
  on device
- Vector store — brute-force dot product over normalized vectors in `localrag-core`, no database
- Generation runs fully on-device; retrieved context never leaves the phone
- The knowledge corpus lives in the host app (`app/src/main/docs`) and is indexed by the
  `com.ayushig.localrag` Gradle plugin into `assets/localrag/docs.localrag` at build time

## Tech stack

| | |
|---|---|
| Language | Kotlin 2.3.20 |
| Architecture | MVVM + Clean Architecture (`ui` / `domain` / `data`) |
| UI | Jetpack Compose, Material 3 (`NavigationSuiteScaffold` for adaptive layout) |
| Build | Gradle (Kotlin DSL), AGP 9.3.2, version catalog in `gradle/libs.versions.toml` |
| SDK | `minSdk` 26, `targetSdk` / `compileSdk` 37 |
| Package | `com.ayushig.localrag.demo` (library: `com.ayushig.localrag`) |
| On-device AI | LiteRT-LM 0.17.0 (generation + embeddings) |

## Building

```bash
./gradlew assembleDebug      # build the debug APK
./gradlew installDebug       # build and install on a connected device/emulator
./gradlew test               # unit tests
./gradlew connectedAndroidTest   # instrumented tests (device required)
```

Or open the project in Android Studio and run the `app` configuration.

## Verifying the assistant

```bash
./gradlew :app:generateLocalRagBundle   # rebuild the docs bundle only
python3 tools/agent-poc/agent_poc.py demo   # gate + sizes, no device needed
./maestro/run_assistant_tests.sh        # on-device Q&A suite (model must be pushed);
                                        # writes maestro/report/ with screenshots + logcat
```

## Current layout

What exists on disk today. The library modules are documented in `AGENTS.md`.

```
app/src/main/java/com/ayushig/localrag/demo/
├── core/                    # shared formatting (figures are rendered, never generated)
├── domain/                  # entities, repository interfaces, assistant models — no Android imports
│   ├── assistant/           # fixed fallback copy
│   ├── model/               # portfolio models, chat transcript, tool-call records
│   ├── repository/          # repository interfaces
│   └── usecase/portfolio/   # one portfolio use case per screen query
├── data/                    # repository implementations, local stores, DTOs + mappers
│   ├── assistant/           # tool definitions, agent prompt copy
│   ├── portfolio/           # fake data + calculations
│   └── repository/          # fake portfolio repository
├── di/                      # Hilt modules (LocalRagModule provides the singleton LocalRag)
└── ui/                      # screens, ViewModels, navigation, theme
    ├── chat/                # assistant surface: working card, turn details, suggestions
    ├── docs/                # documentation search over retrieveOnly()
    └── portfolio/           # home + category detail screens
app/src/main/docs/           # Markdown corpus indexed by the plugin at build time
app/src/main/localrag-clusters.json  # precomputed answers, written into the bundle
```

## Conventions

- New code goes in `ui` / `domain` / `data`, following the existing packages.
- A Compose screen takes UI state and event lambdas as parameters; it does not reach for a
  ViewModel itself beyond the top-level route composable.
- One use case per screen query on the portfolio side, named as a verb phrase
  (`GetPortfolioSummary`, `FindHoldingBySymbol`). The chat screen has no use cases; it drives
  the SDK agent loop directly.
- `domain` stays free of Android imports — if a domain file needs `android.*`, the abstraction is
  in the wrong layer.
- Repository interfaces live in `domain/repository`; their implementations live in
  `data/repository` and are suffixed `Impl`.
- Assistant answers carry their source references through the domain model, so the UI can always
  show what an answer was grounded in.
