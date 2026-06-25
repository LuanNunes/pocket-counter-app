# CLAUDE.md

This file provides guidance to Claude Code when working in this repository.

# Commands

Use the Gradle wrapper for all tasks.

* Build: `./gradlew :app:assembleDebug`
* Install: `./gradlew :app:installDebug`
* Test: `./gradlew test`
* Single test: `./gradlew :app:testDebugUnitTest --tests "com.package.ClassName"`
* Lint: `./gradlew :app:lintDebug`
* Clean: `./gradlew clean`

Java 17 is required.

The backend runs at `http://10.0.2.2:8080/` (Android emulator → host).

# Architecture

Stack:

* Kotlin
* Jetpack Compose
* MVVM + UDF
* Hilt
* Room
* DataStore
* Retrofit
* KSP

Dependencies must always point inward:

```
UI
 ↓
ViewModel
 ↓
Domain
 ↑
Data
```

* Domain contains business rules.
* UI renders state only.
* ViewModels orchestrate the flow.
* Data implements repositories.
* DTOs never leave the data layer.

# Engineering Principles

## DDD

* Rich domain models.
* Business rules belong inside the domain.
* Avoid anemic models.
* Domain must not depend on Android, Retrofit or Room.

## Immutability

* Prefer `val` over `var`.
* State changes should return new instances.
* Prefer immutable collections.

## SOLID & DI

* Follow SOLID principles.
* Depend on interfaces, not implementations.
* Never instantiate repositories manually.
* Use Hilt for dependency injection.

## Keep It Simple

* DRY: avoid duplicated logic.
* KISS: prefer simple solutions.
* YAGNI: don't build for hypothetical requirements.
* Favor composition over inheritance.

## Code Style

* Prefer early returns over `else`.
* Keep functions small and focused.
* Use meaningful names.
* Minimize nesting.
* Prefer expressions over mutable code.
* Use Kotlin idioms (`let`, `run`, `apply`, `map`, `fold`, etc.) when they improve readability.

## Error Handling

* Fail fast.
* Never swallow exceptions.
* Validate inside the domain.
* Surface meaningful errors.

## Testing

Test business rules first.

Prioritize:

* Domain
* Use cases
* Validation
* Mappers

UI tests should verify rendering and interactions only.

# Wizard

The wizard is the application's main workflow.

Flow:

Type → Amount/Date → Payment Source → Source → Tags → Success

Rules:

* Changing the payment source invalidates the selected source.
* Token roles are mutually exclusive.
* Validation belongs inside `WizardDraft`.

# Backend

When integrating new endpoints:

1. Create Retrofit API.
2. Create DTOs.
3. Implement the repository.
4. Replace the in-memory binding in `DataModule`.

Do not change repository interfaces unless absolutely necessary.

# Design

`handoff/README.md` is the source of truth for the UI.

When implementation and specification differ, follow the specification unless there is a documented reason not to.
