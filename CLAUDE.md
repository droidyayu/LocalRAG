# LocalRAG — working notes

Android app: a **fintech app UI** plus an embedded **assistant** that answers FAQ and
app/account-status questions ("why is my transfer pending?", "is my KYC complete?", "what are the
transfer limits?"). Answers are grounded in a local knowledge index plus the user's on-device app
state — hence *Local* + *RAG*.

See `README.md` for the full picture. This file is the short version for working in the repo.

## Architecture: MVVM + Clean Architecture

Dependencies point inward. `presentation` → `domain` ← `data`. `domain` depends on nothing.

```
com.ayushig.localrag/
├── di/            # DI modules
├── core/          # shared utils, Result/error types, common UI
├── domain/        # model/ · repository/ (interfaces) · usecase/     ← pure Kotlin
├── data/          # local/ · remote/ · dto/ · mapper/ · repository/  ← *RepositoryImpl
└── presentation/  # theme/ · navigation/ · <feature>/ (Screen + ViewModel + UiState)
```

## Rules

- **No Android imports in `domain`.** If a domain file needs `android.*`, the abstraction belongs
  in `data` or `presentation`.
- **ViewModels call use cases**, never repositories or data sources directly.
- **One use case per operation**, verb-phrase named, single public `operator fun invoke`.
- **Screens are stateless**: they take an immutable UI state + event lambdas. Only the top-level
  route composable touches the ViewModel. UI state is exposed as `StateFlow`.
- **DTOs stay in `data`.** Map DTO ↔ domain entity in `data/mapper`; domain types never expose DTOs.
- Repository interface in `domain/repository`, implementation in `data/repository` suffixed `Impl`.
- Assistant answers carry source references in the domain model so the UI can show what grounded
  them.

## Current state

Fresh Compose scaffold only — `MainActivity.kt` with a `NavigationSuiteScaffold` (placeholder
Home/Favorites/Profile destinations, `Greeting` placeholder) and `ui/theme/`. No fintech screens,
no assistant, no retrieval pipeline.

The layer packages above **do not exist yet**. When adding code: create the layer package it
belongs in, and put new UI under `presentation/` — not under the existing `ui/`, which is slated to
move to `presentation/theme/`.

## Not yet decided

DI framework (assume Hilt unless told otherwise), local persistence, embedding model, on-device
inference runtime, vector store, whether generation is on-device or hosted. Ask rather than pick
one of these unilaterally.

## Build

```bash
./gradlew assembleDebug
./gradlew installDebug
./gradlew test
./gradlew connectedAndroidTest   # device required
```

Kotlin 2.2.10 · AGP 9.3.2 · Compose BOM 2025.12.00 · minSdk 24 · target/compileSdk 37.
Dependencies go through the version catalog at `gradle/libs.versions.toml` — add the version and
library entries there, then reference via `libs.*` in `app/build.gradle.kts`.
