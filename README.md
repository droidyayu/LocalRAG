# LocalRAG

An Android app that pairs a **fintech app UI** with an **in-app assistant**. The assistant answers
FAQ-style and account-specific questions so users can understand the app, their account status, and
what to do next — without leaving the screen they're on or filing a support ticket.

## What we're building

Two halves that work together:

**1. The fintech app UI**
A Compose-based fintech client — accounts, balances, transactions, transfers, and profile. This is
the surface the user actually uses, and it's also the source of truth the assistant answers from.

**2. The assistant**
A conversational helper embedded in the app. It handles the kinds of questions users ask support:

- **FAQ / product questions** — "What are the transfer limits?", "How long do withdrawals take?",
  "What fees apply to this account type?"
- **App status and account questions** — "Why is my transfer still pending?", "Is my KYC
  verification complete?", "What's the status of my card?"
- **Navigational help** — "Where do I change my payout account?", "How do I enable 2FA?"

Answers are grounded in retrieved context — product docs, FAQ content, policy text, and the user's
own in-app state — rather than generated free-hand. That grounding is the *RAG* in LocalRAG, and
keeping retrieval and user data **on-device** is the *Local*.

## Why on-device

Fintech data is sensitive. Account balances, transaction history, and KYC status should not be
shipped to a third party to answer "why is my transfer pending?". Keeping the index and the
user-context lookup local means the assistant can be specific about the user's situation without
that data leaving the device.

## Current state

Early. The repository is an Android Compose scaffold:

- `MainActivity.kt` — `NavigationSuiteScaffold` with three placeholder destinations (Home,
  Favorites, Profile) rendering a `Greeting` placeholder.
- `ui/theme/` — Material 3 theme, color, and type definitions.
- No fintech screens, no assistant, no retrieval pipeline yet.

The navigation destinations are scaffold defaults and will be replaced by real fintech
destinations. The scaffold does **not** yet follow the layer structure below — the existing
`ui/theme` package moves under `presentation/`, and the `domain` and `data` layers are still to be
created.

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

- **domain** — pure Kotlin. Entities, repository *interfaces*, and use cases (one public
  `operator fun invoke` each). No Android or framework types, no Compose, no Room/Retrofit.
- **data** — implements the domain repository interfaces. Owns data sources (local index, local
  store, any remote API), DTOs, and mappers between DTO ↔ domain entity. Domain types never leak
  DTOs outward.
- **presentation** — Compose screens are stateless and driven by an immutable UI state exposed as
  `StateFlow` from a ViewModel. ViewModels call use cases, never repositories or data sources
  directly. User actions go in as UI events.

**Package layout** (target — not yet created):

```
com.ayushig.localrag/
├── di/                          # dependency injection modules
├── core/                        # shared utilities, Result/error types, common UI
├── domain/
│   ├── model/                   # Account, Transaction, KycStatus, Answer, SourceRef …
│   ├── repository/              # AccountRepository, AssistantRepository … (interfaces)
│   └── usecase/                 # GetAccountSummary, AskAssistant, RetrieveContext …
├── data/
│   ├── local/                   # on-device store + knowledge index
│   ├── remote/                  # API clients, if any
│   ├── dto/
│   ├── mapper/
│   └── repository/              # *RepositoryImpl
└── presentation/
    ├── theme/                   # (currently ui/theme — to be moved)
    ├── navigation/
    ├── accounts/                # AccountsScreen + AccountsViewModel + AccountsUiState
    ├── transactions/
    ├── profile/
    └── assistant/               # chat surface, AssistantViewModel
```

Feature packages under `presentation/` group screen + ViewModel + UI state together; `domain` and
`data` are grouped by layer-then-type as above.

**The assistant path through the layers:** a question enters `AssistantViewModel` → `AskAssistant`
use case → `AssistantRepository` → retrieve from the local knowledge index and the user's app
state → assemble context → generate a grounded answer → back out as UI state with the source
references the answer was built from.

**Open decisions:**

- DI framework (Hilt is the default assumption for `di/`) — not yet wired up
- Local persistence choice (Room vs. alternatives) for app state
- Embedding model and on-device inference runtime
- Vector store / similarity search implementation
- Whether generation runs fully on-device or the retrieved context is sent to a hosted model
  (which would change the privacy story — see *Why on-device*)
- Where the knowledge corpus lives and how it is updated

## Tech stack

| | |
|---|---|
| Language | Kotlin 2.2.10 |
| Architecture | MVVM + Clean Architecture (`presentation` / `domain` / `data`) |
| UI | Jetpack Compose, Material 3 (`NavigationSuiteScaffold` for adaptive layout) |
| Build | Gradle (Kotlin DSL), AGP 9.3.2, version catalog in `gradle/libs.versions.toml` |
| SDK | `minSdk` 24, `targetSdk` / `compileSdk` 37 |
| Package | `com.ayushig.localrag` |

## Building

```bash
./gradlew assembleDebug      # build the debug APK
./gradlew installDebug       # build and install on a connected device/emulator
./gradlew test               # unit tests
./gradlew connectedAndroidTest   # instrumented tests (device required)
```

Or open the project in Android Studio and run the `app` configuration.

## Current layout

What exists on disk today. See *Architecture* above for the package structure this is moving to.

```
app/src/main/java/com/ayushig/localrag/
├── MainActivity.kt          # entry point, navigation scaffold
└── ui/theme/                # Material 3 theme, color, typography
```

## Conventions

- New code goes in `presentation` / `domain` / `data` — nothing new under `ui/`.
- A Compose screen takes UI state and event lambdas as parameters; it does not reach for a
  ViewModel itself beyond the top-level route composable.
- One use case per user-meaningful operation, named as a verb phrase (`GetAccountSummary`,
  `AskAssistant`).
- `domain` stays free of Android imports — if a domain file needs `android.*`, the abstraction is
  in the wrong layer.
- Repository interfaces live in `domain/repository`; their implementations live in
  `data/repository` and are suffixed `Impl`.
- Assistant answers carry their source references through the domain model, so the UI can always
  show what an answer was grounded in.
