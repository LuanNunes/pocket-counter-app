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
  through `$(SLASH)` — see `Config/Dev.xcconfig`. `AppEnvironment.baseURL` fails fast with
  that exact hint if it ever breaks.
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
Application    use cases, orchestration
      ↓
Domain         entities, value objects, repository protocols
      ↑
Infrastructure APIClient, Keychain, DTOs, mappers, repository impls
```

* `Domain/` must not `import SwiftUI`, must not know `URLSession`, must not reference a DTO.
* DTOs never leave `Infrastructure/`; mappers are the only conversion point.
* `APIError` is infrastructure — the Application layer translates it into a typed
  per-use-case error before it reaches a view.

## Hard rules

* **Zero external dependencies**: `URLSession`, `Codable`, `Security`, `OSLog`. Adding a
  package needs explicit approval and a sentence on what Apple's SDK cannot do.
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

1. DTO in `Infrastructure/DTO/`
2. `Endpoint` in `Infrastructure/Remote/Endpoints/`
3. mapper in `Infrastructure/Mapper/`
4. implementation in `Infrastructure/Repository/`
5. register it in `AppContainer`

Write the mapper test first. Protocols in `Domain/Repository/` do not change without a real
need.

## Testing

Swift Testing. Priority: Domain → use cases → mappers/validation → networking (refresh and
retry). Domain tests touch no network, no database, no UI framework and no DI container —
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
