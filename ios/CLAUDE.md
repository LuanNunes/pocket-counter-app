# CLAUDE.md — iOS app

This file governs `ios/**`. The Android app lives in `../android/` with its own
`CLAUDE.md`; never apply its conventions here. The repo root `../CLAUDE.md` holds what is
common to both.

Built with **Xcode 27 / iOS 27 SDK**, deployment target **iOS 26.0** — Liquid Glass landed
in iOS 26, so targeting 26 gets the whole design while still covering devices that have not
moved to 27. Swift 6 with strict concurrency (`SWIFT_STRICT_CONCURRENCY = complete`).

## Commands

Everything runs from `ios/`. Three **shared** schemes, one per environment, each driven by
`Config/*.xcconfig`:

```bash
# build
xcodebuild build -scheme "PocketCounter (Dev)" \
  -destination 'platform=iOS Simulator,name=iPhone 17' -derivedDataPath .build -quiet

# test
xcodebuild test -scheme "PocketCounter (Dev)" \
  -destination 'platform=iOS Simulator,name=iPhone 17' -derivedDataPath .build

# one suite (the -only-testing: equivalent of Gradle's --tests)
xcodebuild test -scheme "PocketCounter (Dev)" \
  -destination 'platform=iOS Simulator,name=iPhone 17' -derivedDataPath .build \
  -only-testing:PocketCounterTests/Smoke

# install and launch on the booted simulator
xcrun simctl install booted .build/Build/Products/Debug-Dev-iphonesimulator/PocketCounter.app
xcrun simctl launch --console booted com.resolveprogramming.pocketcounter.dev
```

| scheme | configuration (run) | backend | bundle id |
|---|---|---|---|
| `PocketCounter (Local)` | `Debug-Local` | `http://localhost:8080/` | `…pocketcounter.local` |
| `PocketCounter (Dev)` | `Debug-Dev` | `https://api-dev.pocket-counter.com/` | `…pocketcounter.dev` |
| `PocketCounter (Prod)` | `Release-Prod` | `https://api.pocket-counter.com/` | `…pocketcounter` |

The `Dev` scheme archives from `Release-Dev`. For day-to-day work, ⌘R in Xcode with the
right scheme selected; the commands above are for scripting and reproducible verification.

## Project layout quirks that will bite

* `PocketCounter/` and `PocketCounterTests/` are **file-system-synchronized groups**
  (`PBXFileSystemSynchronizedRootGroup`, `objectVersion = 77`). A new file on disk is part
  of the target automatically — never hand-edit `project.pbxproj` to add sources.
* `Info.plist` sits at `ios/Info.plist`, **outside** the synchronized group on purpose: a
  plist inside it would also be copied as a bundle resource.
* `//` starts a comment in xcconfig, so a literal `https://` truncates to `https:`. URLs go
  through `$(SLASH)` — see `Config/Dev.xcconfig`. `AppConfiguration` fails fast with that
  exact hint if it ever breaks, and restores the trailing slash `Endpoint` concatenates onto,
  so a dropped `$(SLASH)` cannot send every request to `…pocket-counter.comapi/v1/…`.
* Schemes must stay **shared** (`xcshareddata/xcschemes/`). An unshared scheme lives in
  `xcuserdata/`, which is gitignored, and vanishes for everyone else.
* A scheme with an empty `<TestPlans></TestPlans>` element makes `xcodebuild test` fail with
  "not currently configured for the test action" — the element must be absent, not empty.
* The synchronized group copies **every** file it finds as a bundle resource, dotfiles
  included. The `.gitkeep` files that keep the empty layer folders visible in a clone all
  collide on one output path, so `Shared.xcconfig` carries
  `EXCLUDED_SOURCE_FILE_NAMES = .gitkeep`.
* Unit tests always build under a **debug** configuration: `@testable` needs
  `ENABLE_TESTABILITY`, which must not ship. That is why the Prod scheme runs its tests on
  `Debug-Dev` while running and archiving on `Release-Prod`.
* Settings live in exactly one place. `Config/Shared.xcconfig` holds what both targets need
  and is included by `Base.xcconfig` (app) and `Tests.xcconfig` (test bundle); the project
  file carries only the debug/release differences. Do not re-declare a setting in
  `project.pbxproj` that an xcconfig already sets — the two resolve differently per target
  and will drift.
* `simctl boot` failing with "The iOS 27.0 simulator runtime is not available" while
  `xcodebuild test` works is stale CoreSimulator state, not a missing runtime. Fix:
  `killall -9 com.apple.CoreSimulator.CoreSimulatorService`, then boot again.
* This Xcode install ships no `Simulator.app`, so there is no simulator window. Booting
  headless and driving it with `xcrun simctl install/launch/io ... screenshot` works and is
  what the verification commands above rely on.

## Architecture

```
Presentation   SwiftUI views + @Observable models + DesignSystem
      ↓
Service        use cases, orchestration
      ↓
Model          entities, value objects, enums, DTOs, contracts
      ↑
Repository     contract implementations
      ↓
Infrastructure APIClient, Keychain, mappers
```

The layer names mirror `pocket-counter-core` on the backend, so a concept sits in the same
place in Kotlin and in Swift: `Model/{Entity,DTO,Contract,Enum}`, `Service/`, `Repository/`,
`Infrastructure/`. `Presentation/` has no backend counterpart and keeps its name.

* `Model/` must not `import SwiftUI` and must not know `URLSession`. Every other layer may
  depend on it; it depends on nothing.
* `Model/Contract/` holds the protocols (`SessionRepository`, `TokenStoring`). `Service/`
  takes the protocol, `Repository/` implements it — **the arrow from `Repository/` still
  points up into `Model/`**. The protocol moved next to the types it speaks about; the
  inversion it exists for did not change.
* A contract whose implementation **is** a piece of infrastructure stays in
  `Infrastructure/`, not `Repository/`: `KeychainTokenStore` implements `TokenStoring` from
  `Infrastructure/Local/`, because it is the Keychain, not a repository over it. `Repository/`
  is for implementations that compose infrastructure into a domain operation —
  `APISessionRepository` is one. Both arrows point up into `Model/` either way.
* `Model/DTO/` holds wire shapes: `Codable` and nothing else, no rules and no behavior.
  `Infrastructure/Mapper/` is the only place a DTO becomes an entity, and `Service/` and
  `Presentation/` only ever see entities. Folders do not enforce this — Swift compiles the
  app as one module — so it is a review rule.
* `APIError` is infrastructure and never leaves `Infrastructure/`. The **repository
  implementation** translates it into the typed error the contract declares — translating it
  in `Service/` would force that layer to import an infrastructure type, inverting the very
  arrow this layering exists to protect. `.sessionExpired` (the refresh was refused; the
  session is already cleared) and `.authenticationUnavailable` (could not authenticate right
  now; the session is intact and the call is retryable) mean different things to the user, so
  every repository must translate both. The switch over `APIError` is **exhaustive, with no
  `default:`** — that is what makes a new case a compile error instead of a silent
  "something went wrong".
* **Mappers** are `enum`s of static throwing functions (`throws(MappingFailure)`), fed real
  captured JSON in tests. A DTO field the entity cannot do without throws; an optional one maps
  to `nil`. `MappingFailure` becomes `LoadFailure.server` in the repository.
* **Enum degradation.** An unknown enum value that affects a sign, an arithmetic result or a
  count cannot degrade: throw. One that only decorates (a payment method) degrades to `nil`.
* **Order is part of a repository's contract and must be a total order.** `sorted(by:)` is not
  stable and `displayOrder` is 0 on most rows, so end every key in `id`.
* **A repository that caches is built once**, as a stored `let` in `AppContainer`. A computed
  property builds a fresh instance, and so an empty cache, per access.
* **A failure to read is not an absence.** A `TokenStoring` read that throws
  `TokenStoreUnavailable` says "cannot tell", not "signed out" — a read before first unlock
  answers `errSecInteractionNotAllowed` with the session perfectly intact. It is never
  cached, and `SessionStatus.undetermined` is how the session gate renders it: a retry, never
  the login screen. Signing a user out because a question could not be answered is the bug
  this pair of rules exists to prevent.

## Hard rules

* **Zero external dependencies**: `URLSession`, `Codable`, `Security`, `OSLog`. Adding a
  package needs explicit approval and a sentence on what Apple's SDK cannot do.
  * **One approved exception, 2026-10-04: `GoogleSignIn-iOS`**, for Google sign-in.
    What Apple's SDK cannot do: `ASWebAuthenticationSession` can run Google's OAuth web flow,
    but it cannot mint an ID token carrying the **server's** web-client audience without us
    implementing the code exchange and PKCE ourselves, and it cannot reuse the Google account
    the device has already authorised — which is exactly what `setServerClientId` buys the
    Android app. The backend compares `aud` against one configured client id
    (`GoogleOAuthService.validateIdToken`), so matching Android's server-client-id approach is
    what keeps the backend unchanged.
    The SDK is confined to `Infrastructure/`, behind a `GoogleIdentityProviding` contract in
    `Model/Contract/`. **`Presentation/` must never `import GoogleSignIn`.**
* **DI is a hand-built `AppContainer`.** Constructor injection into models; no singletons,
  no service locator, no DI framework.
* `async/await` only. No Combine, no `DispatchQueue.main.async`.
* One `state` struct per model, `private(set)`, mutated only on the `MainActor`. Models are
  `@MainActor @Observable` and take **protocols** in `init`.
* Views take `state` + `onAction`. **No ViewModel by default** — only where there is real
  orchestration.
* Typed identifiers (`TransactionID`, `TagID`, `CardID`), never raw `String`.
* `Decimal` for money, never `Double`. Tokens in the Keychain, never `UserDefaults`.
* `guard` and early returns; **avoid `else`**. Android enforces this with the detekt rule
  `ForbiddenElse`; here it is a review rule until a linter covers it.
* Never swallow an error: no `catch { return nil }`, no force unwraps.
* Name things after business concepts — no `Manager`, `Helper`, `Utils`, `CommonService`.
* Liquid Glass belongs on controls that float over scrolling content, never on content, and
  never glass over glass (use `GlassEffectContainer`). Never set
  `UIDesignRequiresCompatibility` — it turns Liquid Glass off.

## Adding an endpoint

1. DTO in `Model/DTO/`
2. a `static func` per path on a nested `enum Route` inside the repository, returning an
   `Endpoint`. It is internal, not private, so a test can assert the path with no HTTP fake:
   a typo is otherwise a silent 404. Paths carry no leading slash.
3. declare its `authentication:` — `.bearer`, unless the request carries its own credential
   (a password, a refresh token), which is `.credentials`. It decides whether a 401 triggers
   a refresh: refreshing after a rejected credential both loops and signs out a healthy
   session.
4. mapper in `Infrastructure/Mapper/`
5. implementation in `Repository/`
6. register it in `AppContainer`

Write the mapper test first. Protocols in `Model/Contract/` do not change without a real
need.

## Testing

Swift Testing. Priority: Model → use cases → mappers/validation → networking (refresh and
retry). Model tests touch no network, no database, no UI framework and no DI container —
if one is needed, the dependency is in the wrong place. Test behavior, not implementation
details.

## Design

`../docs/ios26/` is the source of truth: the `.jsx` files are the per-screen spec,
`glass.css` (lines 11-15) the tokens, `screens.css` the metrics. Start at its `README.md`,
which records that the prototype **does not run** and which of its own claims do not hold.
Colors there are OKLCH — converted once and stored as asset-catalog Color Sets in Display P3
(`tint` in dark mode falls outside sRGB), **not hand-tweaked in Swift**. Most tokens map to
Apple's semantic colors; only the purple tints and the hero are custom.

Mind the `-ink` pairs. `glass.css` ends with
`.inc{color:var(--green-ink)}.exp{color:var(--label)}.wrn{color:var(--orange-ink)}`: the
plain `--green`/`--orange` fill dots and badges, while their darker `-ink` twins are for
**text**, because the system color is unreadable on `--cell` in light mode (`systemGreen` is
2.2:1 there). And an expense amount is **not red** — `--red` is reserved for destructive
actions. Use `PocketAmount`, which encodes all three.

Where implementation and specification differ, follow the specification unless there is a
documented reason not to.

## Divergences from Android, on purpose

* Bundle ids are suffixed per environment (`.local`, `.dev`, none for prod) so all three can
  be installed side by side. Android uses one `applicationId` for all three flavors and
  cannot.
* The local backend is `http://localhost:8080/`, not `10.0.2.2` — the iOS simulator shares
  the Mac's network stack.
* The Início "N lançamentos para revisar" banner is **not implementable**: it is fed by
  Android's `NotificationListenerService`, which has no iOS equivalent.
* The dark `tint` is deliberately off-spec: `glass.css:16` is `oklch(0.72 0.16 285)` and we ship
  ≈L 0.64 (`#847AE8`), because white on the spec value is 2.61:1, below the 3:1 floor for icons
  and large text. App-wide in dark mode; a "re-derive the tokens" pass must not revert it.
* The destructive button's ink is deliberately off-spec. `glass.css:123` is
  `.btn.dst{background:color-mix(in srgb,var(--red) 14%,transparent);color:var(--red)}` — plain
  `--red` on its own 14% tint, which measures **2.66:1**. There is no `--red-ink` twin in the
  spec the way `--green-ink` and `--orange-ink` exist, so one was derived: `destructiveSoftInk`,
  5.12:1 light and 5.67:1 dark on that tint. `destructiveInk` was tried first and is 4.03:1
  there — it is 5.00:1 on `cell`, but the tinted fill is a different backdrop. Same rule as the
  dark `tint`: a "re-derive the tokens" pass must not revert it.
* The transaction detail sheet keeps `PocketColor.background` behind its content, where
  `glass.css:107` has a blurred material. `PocketListSection` fills with `cell`
  (`secondarySystemGroupedBackground`, white in light mode), which would not separate from a
  light sheet material. Not rendered here: if a device shows the cells separating, drop the
  `.background` in `TransactionDetailView`.
* Início orders `MonthPill` → quick-add → hero, where `home.jsx:21-22` puts quick-add first: the
  pill is shared chrome owned by `MonthScreen`.
