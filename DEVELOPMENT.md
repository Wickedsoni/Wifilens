# WifiLens: Development Guide

Free Android Wi-Fi analyzer that works offline. The `INTERNET` permission is declared in
`:core:wifi` and used by exactly one feature: the Diagnose > Speed download test
(`downloadSpeedFlow`, only when the user taps *Run speed test*). Every other feature, including the
RF coverage prediction, runs entirely on-device. Keep it that way, and update the About screen,
permission-gate footer and README if that ever changes. minSdk 26 / targetSdk 36. Kotlin + Compose + Hilt + Room.

This doc is for anyone working on the codebase. See the root [README](README.md) for the overview,
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for how the pieces fit, and [CONTRIBUTING.md](CONTRIBUTING.md) for
branches and pull requests.

## Module structure

| Module | What it is |
|---|---|
| `:app` | App shell: `MainActivity`, bottom nav, Hilt component root (`@HiltAndroidApp`), app-level Hilt modules (`di/`), the permission gate, StrictMode and LeakCanary in debug. |
| `:build-logic` | Gradle convention plugins (`wifilens.android.library`, `wifilens.android.feature`, `wifilens.android.hilt`, `wifilens.jvm.library`) so every module's build config stays one line. |
| `:core:common` | Pure Kotlin: dispatcher qualifiers, `ApplicationScope`, `Clock`, `Async`, `WhileUiSubscribed`. |
| `:core:model` | Pure Kotlin domain types shared by features: `GridPlan`/`CellType`/`Material`, pins, rooms, settings, repository interfaces, signal thresholds. Immutable; marked stable for Compose in `compose_stability.conf`. |
| `:core:rf` | Pure JVM, zero Android imports (plain `kotlin.jvm` plugin): path loss, wall loss, the Bresenham tracer, calibration fit. |
| `:core:database` | Room v2 (plans, rooms, pins, survey readings, scan history, speed tests) and DataStore settings. |
| `:core:history` | Signal/scan history recording (foreground only), pruning worker, congestion roll-ups. |
| `:core:designsystem` | Material 3 Expressive theme, tokens, components, icons, charts, signal colours (ADR 0005). |
| `:core:wifi` | Wi-Fi scan, connection and speed-test flows, all `callbackFlow`-based. |
| `:core:testing` | Unit-test helpers (`MainDispatcherRule`); `testImplementation` only. |
| `:feature:analyze:{domain,presentation}` | Networks, Spectrum and Health tabs; insight rules and channel planner. |
| `:feature:map:{domain,data,presentation}` | Plans, 2D editor, 3D view, walk survey, JSON import/export. |
| `:feature:diagnose:{domain,data,presentation}` | Coverage, Best spot, Speed, Signal tabs; the shareable PDF/PNG report. |
| `:feature:more:{domain,data,presentation}` | Glossary, Settings, About (with the privacy policy link). |
| `:feature:widget` | Glance home-screen widget and Quick Settings tile. |
| `:baselineprofile` | Baseline Profile generator and macrobenchmarks (startup, frame timing) against `:app`. |

A feature gets `domain`/`data` modules only when it has its own business logic or persistence; see ADR 0001.

## Hard constraints

These aren't arbitrary — each one is load-bearing for how the app is built:

- **`:core:rf` has zero Android imports.** Enforced at the compiler level by using the
  `kotlin.jvm` plugin there instead of an Android library plugin — if an Android
  import sneaks in, the module simply won't compile. This is what makes the RF math
  independently unit-testable, in milliseconds, without an emulator.
- **Canvas only for the map editor and coverage map** — never a `LazyVerticalGrid` of
  cell composables. A floor plan can be tens of thousands of cells; a composable per
  cell would recompose and lay out at a scale Compose isn't built for. Canvas draws
  the whole grid as a handful of batched `Path`s.
- **Walls are unpainted tiles carrying a `Material`, never lines.** The wall *is* a
  grid cell — a `CellType.Empty(material)` — not a separate geometric overlay. That's
  what lets the same grid double as the RF simulation's obstacle map with no
  translation step.
- **One router pin and one floor per plan.** Any number of saved plans (v2, ADR 0007); the active
  plan is the most recently opened one. Plans move between devices as versioned JSON files through the
  Storage Access Framework (no storage permission).

## Out of scope

AR capture, photo/image floor-plan import, wall thickness, multiple floors per plan, cloud sync,
login/account/profile, an AI assistant, router login/control, notifications, onboarding carousels,
vendor lookup, background location. If a change would require one of these, it's a different app (see
`docs/project/ROADMAP.md`).

## Core data model

Every cell in the floor plan is one of three `CellType`s: `Floor(roomId)` (belongs to
a room), `Empty(material: Material)` (a wall, tagged with what it's made of), or
`Door`. Walls aren't a separate concept — they're just `Empty` cells sitting between
two `Floor` regions. The payoff of this being *one* data structure is that the floor
plan you draw, the coverage heatmap you see, and the router-placement optimizer's
search space are all the same `GridPlan` — there's no separate representation to keep
in sync, no export/import step between "what I drew" and "what gets simulated."

## RF prediction

```
RSSI(cell) = A − 10·n·log10(d) − Σ wallLoss(walls crossed)
```

- `A` — reference signal strength at 1 meter from the router (dBm).
- `n` — the path-loss exponent: how fast signal fades with distance. Free space ≈ 2.0;
  a typical home with walls and furniture ≈ 3.0 (the app's default); dense
  walls/many obstructions ≈ 4.0+.
- `d` — distance from the router to the cell, in meters, clamped to a 1m minimum
  before the `log10` (the formula is undefined, and physically meaningless, under 1m).
- `Σ wallLoss(...)` — the sum of the per-material attenuation of every wall the
  straight line from router to cell crosses. That line is traced cell-by-cell with
  Bresenham's line algorithm — the same integer algorithm used for line rendering,
  repurposed here to enumerate which grid cells a ray passes through.

The router-placement optimizer evaluates every walkable tile as a candidate router
position, scores each by the *worst* predicted signal across all placed device pins
(not the average — a great signal in one room doesn't help if another device is
starved), and returns the tile that maximizes that worst case.

## What's built (2.0.x)

- **Analyze:** Networks (sortable, band filters, 24 h signal history per network), Spectrum (congestion, busy hours),
  Health (one-tap check: signal, channel, band, access point, mesh, security, speed; channel planner).
- **Map:** multiple plans with JSON import/export, 2D editor with undo/redo, 3D view (orbit, pinch zoom), walk
  survey with per-plan calibration.
- **Diagnose:** Coverage (predicted or calibrated), Best spot optimizer, Speed test with history, live Signal
  meter, shareable PDF/PNG coverage report.
- **More:** Glossary, Settings, About. Home-screen widget and Quick Settings tile.
- Baseline Profile shipped; macrobenchmarks in `:baselineprofile`. Release numbers are in `docs/project/sprint-log.md`
  (Sprint 12).

## Known gaps

- A "measured vs predicted" difference view for walk-survey readings was left in the backlog (Sprint 7).
- Frame timing at 120/144 Hz: map pinch-out and short-list overscroll exceed the 5% jank target (Sprint 12, accepted).

## Key patterns

- `callbackFlow` + `awaitClose` for any listener/receiver-backed flow (Wi-Fi scan
  results, connection state). Seed the flow with the current value on subscribe —
  several of Android's `NetworkCallback`/broadcast APIs simply never fire when there's
  nothing to report (e.g. no active network), so waiting for the first callback before
  emitting anything leaves the UI stuck on a loading state indefinitely.
- Sealed interfaces (not enums) when variants carry data.
- MVI: `StateFlow` for state, `Channel` for one-shot events.
- `SavedStateHandle` for anything cheap that must survive process death (active tool,
  selected room, wall material) — not the floor-plan grid itself, which can be tens of
  thousands of cells and belongs in Room instead, autosaved on a debounce.
- Pure functions in `:core:rf`, tested with JUnit5 + parameterized cases.

## Building and running

Open in Android Studio (or run headless with the Gradle wrapper below). Requires JDK 17+.

```
./gradlew :app:assembleDebug            # debug APK
./gradlew :core:rf:test                 # RF engine unit tests
./gradlew :app:bundleRelease            # signed AAB (needs keystore.properties, see docs/release/signing.md)
./gradlew :app:generateBaselineProfile -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=BaselineProfile
./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=Macrobenchmark
```

Connected runs (instrumented tests, profile generation, benchmarks) uninstall the app afterwards, wiping its data.

## Code quality gates

- `./gradlew spotlessCheck` / `spotlessApply`: ktlint formatting (rules in `.editorconfig`). It only checks files changed since `origin/main` (a ratchet), so old code is not reformatted wholesale; anything you touch must be clean.
- `./gradlew detekt`: static analysis (rules in `config/detekt/detekt.yml`). Existing findings are frozen in `config/detekt/baseline.xml`; the baseline may only shrink. Do not add to it to silence new code.
- Room schemas are exported to `core/database/schemas/` and committed; see `docs/adr/0003-room-migrations.md` before changing entities.
- Architecture decisions live in `docs/adr/`.
- CI (`.github/workflows/ci.yml`) runs format + detekt, unit tests, lint, the R8 release build and the release AAB (signed when the upload-key secrets are set).
- Release paperwork (listing, data safety, content rating, signing, closed test) lives in `docs/release/`.
