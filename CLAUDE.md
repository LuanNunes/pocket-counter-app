# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

Gradle wrapper (`./gradlew`) is the entry point for everything. Java 17 toolchain is required.

- Build debug APK: `./gradlew :app:assembleDebug`
- Install on running emulator/device: `./gradlew :app:installDebug`
- All unit tests: `./gradlew test` (or `./gradlew :app:testDebugUnitTest`)
- Single test class: `./gradlew :app:testDebugUnitTest --tests "com.resolveprogramming.pocketcounter.domain.WizardDraftTest"`
- Single test method: append `.methodName` to the `--tests` filter
- Android lint: `./gradlew :app:lintDebug`
- Clean: `./gradlew clean`

No instrumented tests exist yet — `androidTest` source set is not populated.

`API_BASE_URL` is hardcoded to `http://10.0.2.2:8080/` (Android emulator loopback to host) in `app/build.gradle.kts` via `buildConfigField`. The backend project lives at `/home/nunes/projects/resolveprogramming/pocket-counter`; start it on host port 8080 before exercising network paths.

## Architecture

Single-module Android app (`:app`), package `com.resolveprogramming.pocketcounter`. Stack: Jetpack Compose + Material 3, MVVM + UDF (StateFlow → UI), Hilt DI, Navigation Compose, Room + DataStore, Retrofit + kotlinx.serialization, KSP.

### Layering

- `domain/model/` — pure Kotlin data classes (`WizardDraft`, `NotificationItem`, `Source`, `Tag`, `TagContext`, `Token`, etc.). `WizardDraft` mirrors the backend `TransactionDto` 1:1 and owns step-validation logic.
- `data/repository/` — interface + `InMemory*` implementation pairs (`NotificationRepository`, `TransactionRepository`, `SourceRepository`, `PaymentSourceRepository`, `TagRepository`). Interfaces are the contract; `InMemory*` use `SampleData` as a stand-in until Room/Retrofit lands.
- `data/sample/SampleData.kt` — seed data for the in-memory repos.
- `data/local/TokenStore.kt` — auth tokens persisted via DataStore Preferences.
- `data/remote/` — `AuthInterceptor` attaches `Bearer` headers (skipping `/auth/` paths); `TokenAuthenticator` does refresh-on-401 with infinite-loop guard; `api/AuthApi` + `dto/AuthDto`.
- `di/DataModule.kt` binds repo interfaces to `InMemory*` impls — **this is the swap point** when wiring real Room/Retrofit backings. `di/NetworkModule.kt` provides `Json`, `OkHttpClient`, `Retrofit`, and Retrofit APIs.
- `ui/{home,auth,wizard}/` — Compose screens + Hilt ViewModels exposing `StateFlow<UiState>`.
- `ui/theme/` — `PocketTheme` + `PocketColors`/`PocketTypography`/`PocketShapes`. Tokens are derived from `handoff/README.md`; do not redesign — match those values. Fonts (DM Sans + Geist Mono) ship in `res/font/`.
- `navigation/PocketNavHost.kt` — three routes (`AUTH`, `HOME`, `WIZARD/{notificationId}`). Start destination is gated by `TokenStore.isLoggedIn`; transitions are horizontal slide + fade.

### Wizard (the central feature)

5 steps mapping 1:1 to a `TransactionDto`: Type → Amount/Date → PaymentSource → Source → Tags, then Success. `WizardViewModel` owns a single `WizardUiState` containing the `WizardDraft`, filtered Source list, available tags, parsed `Token`s from the source text, and current `WizardStep`.

Two invariants worth knowing before touching wizard code:

1. **Payment-method change invalidates the selected Source.** `WizardDraft.withPaymentSourceReset` nulls `idSource` when `idPaymentSource` changes — Sources are scoped to one payment method. Don't bypass this.
2. **Source-text token roles are mutually exclusive per role.** `assignTokenRole` strips the role from any other token before assigning. Tokens drive the draft (e.g., relabeling a token as `MERCHANT` updates `draft.merchant`; `AMOUNT` re-parses the BR-formatted number).

`WizardDraft.fromNotification` is the bridge from a parsed/classified `NotificationItem` to an initial draft. Step validation lives on `WizardDraft` (`isStep1Valid`…`isStep4Valid`); step 5 (tags) is always advanceable.

### Design source of truth

`handoff/README.md` is the authoritative spec for the UI (colors as OKLCH, typography sizes, spacing scale, wizard copy, backend mappings including endpoints like `POST /api/v1/transactions/{incomes,expenses}` and the `POST /api/v1/notifications/classify` contract). `handoff/prototype/` contains the HTML reference implementation — it is design reference, **not** code to port literally. When the spec and the Compose code disagree, the spec wins unless there's a deliberate reason recorded.

### Backend integration (not yet wired)

Repository interfaces and `NetworkModule` are scaffolded but only `AuthApi` is real. To plug a feature into the backend: add a Retrofit API in `data/remote/api/`, add DTOs in `data/remote/dto/`, write a `Retrofit*Repository` impl, and swap the `@Binds` in `DataModule` from the `InMemory*` impl to the new one. Keep the existing repository interface unchanged so ViewModels don't need edits.
