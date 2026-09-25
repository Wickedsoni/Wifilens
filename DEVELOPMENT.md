# WiFiLens — Development Guide

Free Android Wi-Fi analyzer that works offline. The `INTERNET` permission is declared in
`:core:wifi` and used by exactly one feature: the Diagnose > Speed download test
(`downloadSpeedFlow`, only when the user taps *Run speed test*). Every other feature, including the
RF coverage prediction, runs entirely on-device. Keep it that way, and update the About screen,
permission-gate footer and README if that ever changes. minSdk 26 / targetSdk 36. Kotlin + Compose + Koin + Room.

This doc is for anyone (including future-me) working on the codebase. See the root
[README](README.md) for the user-facing pitch and screenshots.

## Module structure

| Module | What it is |
|---|---|
| `:app` | App shell: `MainActivity`, bottom nav, Koin startup, the permission gate. |
| `:build-logic` | Gradle convention plugins (`wifilens.android.library`, `wifilens.android.feature`, `wifilens.jvm.library`) so every module's build config stays one line. |
| `:core:rf` | Pure JVM — zero Android imports, enforced by using the plain `kotlin.jvm` plugin. Path loss, wall loss, the Bresenham tracer, `GridPlan`/`CellType`/`Material`. Near-total unit test coverage. |
| `:core:database` | Room entities + DAOs (plan, rooms, pins), DataStore-backed settings. |
| `:core:designsystem` | The "Nothing"-inspired design system: tokens, typography, components, icons. |
| `:core:wifi` | Wi-Fi scan flow, connection flow, one-shot manual scan, location-services check — all `callbackFlow`-based. |
| `:feature:analyze:presentation` | Networks + Spectrum tabs. |
| `:feature:map` | 2D canvas floor-plan editor, isometric render, undo/redo, pin placement. |
| `:feature:diagnose` | Coverage scoring, router-placement optimizer, Coverage + Best Spot screens. |
| `:feature:more` | Glossary, Settings, About. |

Feature modules are presentation-only for now — no separate `:domain`/`:data`
submodule per feature. Split one out only when a feature actually needs its own
persistence or business-logic layer distinct from `:core`; don't pre-create the split
ahead of that need (an empty `:core:domain` placeholder module existed for a while and
was removed for exactly this reason — see git history if you're curious).

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
- **One router pin. One floor. One saved plan.** Deliberately not a general-purpose
  floor-plan tool — see "Out of scope" below.

## Out of scope

Walk-around surveys, measured heatmaps, before/after verification, AR capture,
photo/image floor-plan import, wall thickness, multiple floors, multiple saved plans,
cloud/sync/share/export, login/account/profile, an AI assistant, router login/control,
speed tests, notifications, onboarding carousels, vendor lookup. If a change would
require one of these, it's a different app.

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

## What's built

All four tabs are functionally complete:

- `:core:rf` — path loss, wall loss, Bresenham tracer, `GridPlan`/`CellType`/`Material`, unit tests.
- `:core:database` — Room entities + DAOs (plan, rooms, pins), DataStore-backed settings.
- `:core:designsystem` — tokens, components, icons.
- `:core:wifi` — scan flow, connection flow, one-shot manual scan, location-services check.
- `:feature:analyze:presentation` — Networks + Spectrum tabs, manual refresh.
- `:feature:map` — 2D canvas editor, ISO render with swipe-to-orbit and pinch-to-zoom, undo/redo, pins.
- `:feature:diagnose` — coverage scoring, optimizer, Coverage + Best Spot screens.
- `:feature:more` — Glossary, Settings, About.
- Permission gate screen; bottom nav with all four tabs live.

## Roadmap / known gaps

- Legacy PNG mipmaps still need to go through the Android Studio Image Asset tool.
- Launcher icon hasn't been verified rendering correctly on-device across launchers.
- Room deletion has no UI yet — rooms can be created and painted, not removed.

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

Open in Android Studio (or run headless with the Gradle wrapper below). Requires
JDK 17+; the Gradle wrapper handles the rest.

```
./gradlew :app:assembleDebug   # build the debug APK
./gradlew :core:rf:test        # run the RF physics engine's unit test suite
```
