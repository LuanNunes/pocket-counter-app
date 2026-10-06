# CLAUDE.md

PocketCounter mobile monorepo — two **independent native apps** for the same backend.

```
android/   Kotlin · Jetpack Compose · Hilt · Retrofit     → android/CLAUDE.md
ios/       Swift · SwiftUI · URLSession                   → ios/CLAUDE.md
docs/      ios26/ = iOS design spec; compliance/ = store & privacy
```

## Routing

Working under `android/**`, read `android/CLAUDE.md`. Under `ios/**`, read
`ios/CLAUDE.md`. **Never mix the two sets of conventions** — they are different stacks with
different idioms. Each app's Gradle/Xcode root is its own directory, so build commands run
from there, not from here.

## No shared code

There is no code shared between the platforms, and there must not be. The only shared
contract is the backend's REST API. Domain rules are written twice, in Kotlin and in Swift,
**on purpose** — that is the accepted cost of going native on both sides. Do not propose
Kotlin Multiplatform, a shared module, or generated clients without asking first.

The backend is the arbiter of business rules that matter. Clients replicate input
validation, not authority.

## Write scope

Edits happen **only inside this repository**. The sibling repos
`../pocket-counter` (backend) and `../pocket-counter-web` (web) are **read-only** — read
them to check the REST contract or a convention, never to change them. If something there
needs to change, say so instead of editing it.

## Backend

Kotlin/Spring, in the sibling repo `../pocket-counter` (modules `pocket-counter-api`,
`pocket-counter-core`, `pocket-counter-mcp`). The product's CI/CD lives there; **this repo
has no CI**, so verification here is local.

* Endpoints are under `api/v1/`; auth is JWT Bearer with refresh.
* The OpenAPI spec is served at `/v3/api-docs`, but `OpenApiConfig` is `@Profile("dev")` —
  it only exists when the backend runs with the `dev` profile.
* Backends per environment: Android emulator → `http://10.0.2.2:8080/`; iOS simulator →
  `http://localhost:8080/` (the simulator shares the Mac's network stack); dev →
  `https://api-dev.pocket-counter.com/`; prod → `https://api.pocket-counter.com/`.

## Common ground

Both apps share the product domain, the pt-BR user-facing copy, and these engineering
principles, expressed in each language's idiom: rich domain models with the rules inside
them; the domain free of framework dependencies; immutability by default; dependency
inversion through interfaces; early returns over `else`; fail fast and never swallow
errors; and tests that cover domain, use cases, validation and mappers before UI.
