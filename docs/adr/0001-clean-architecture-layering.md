# 0001: Layering and module boundaries

Status: Accepted (being applied incrementally, see the roadmap phases 1 and 2)

## Context
The app grew as a prototype. Only `:feature:map` has a real data/domain/presentation split.
`DiagnoseViewModel` talks to Room DAOs directly, `:feature:diagnose` depends on `:feature:map` for one
type (`DevicePin`), and Wi-Fi APIs are consumed without an interface. That makes features hard to test,
hard to change independently and hard to read.

## Decision
- Dependencies point inward: `presentation -> domain <- data`. Domain is pure Kotlin (no Android, no Room).
- Each feature has three parts: `domain` (models it owns, use-cases, repository interfaces), `data`
  (repository implementations, mappers, Koin module) and `presentation` (ViewModel, state, screens).
- Shared types live in `:core:model` (pure JVM). Features never depend on each other; only on `core:*`.
- ViewModels call use-cases or repository interfaces, never DAOs or platform APIs.
- One MVI shape everywhere: immutable `State`, sealed `Action` in, one-shot `Event` out through a Channel.
- Behaviour-preserving moves and logic changes never share a commit; the existing tests are the safety net.

## Consequences
More modules and files, but each is small and replaceable. A dependency-rule check in the build fails on
illegal module edges. Migration is staged one feature per pull request.
