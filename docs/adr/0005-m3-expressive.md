# 0005: Material 3 Expressive design system

- **Status:** Accepted (2026-09-29, Sprint 2). Supersedes [0002](0002-material3-on-nothing-tokens.md).
- **Context:** For the final 2.0 release the user chose pure Material 3 Expressive over the Nothing-inspired look. In the latest stable `material3` (1.4.0) the Expressive APIs (`MaterialExpressiveTheme`, `MotionScheme.expressive()`, `LoadingIndicator`, wavy progress, floating toolbars, `ButtonGroup`) are `internal`. They're public only in the 1.5.0 alphas.

## Decision

- **Dependency:**
  - Pin `material3` to **1.5.0-alpha27**, chosen by the user over a hand-built imitation.
  - It's the newest alpha built against Compose 1.12. The BOM's stable Compose **1.12.1** still wins resolution, so `ui`, `foundation`, `animation` and `runtime` stay stable. Alpha28+ would pull Compose 1.13 alphas.
  - It's the only non-stable dependency in the app.
- **Opt-ins:**
  - `@ExperimentalMaterial3ExpressiveApi` is used **only inside `:core:designsystem`**.
  - Feature modules use the `WifiLens*` wrappers (`WifiLensLoadingIndicator`, `WifiLensWavyProgress`, …).
  - The app is never upgraded after release, so source-compatibility churn doesn't matter. Runtime behaviour is verified on the device.
- **Theme:** `MaterialExpressiveTheme` with `MotionScheme.expressive()`.
- **Colour:**
  - Dynamic colour is on by default (API 31+), and users can turn it off.
  - The brand fallback is generated with Material Color Utilities (`SchemeFidelity`, seed `#2F6FDE`) at standard, medium and high contrast. The variant follows the system contrast setting (API 34+).
  - Code reads `MaterialTheme.colorScheme` directly. There's no parallel token object.
- **Signal colours:**
  - `success`, `warning` and `danger` are fixed per-mode extensions on `ColorScheme` (HCT tone 34 in light, 74 in dark). They never follow the wallpaper, and as pure functions they also work in `DrawScope`.
  - `ContrastTest` checks them for AA on every surface of all six brand schemes.
- **Type:**
  - The M3 type scale on the platform font. Doto, Space Grotesk and Space Mono are removed, which shrinks the APK and matches the system.
  - The planned Roboto Flex was dropped for the same reason.
- **Navigation:** `NavigationSuiteScaffold` gives a bottom bar on phones and a rail on medium and expanded windows.
- **Icons:** `ImageVector`s built from Material path data in `Icons.kt`. No icons-extended dependency.
- **Copy:** M3 uses sentence case. The old ALL-CAPS copy is converted during the Sprint 5 string extraction, so each string is touched once.

## Consequences

- One alpha library ships in production. It's mitigated by stable Compose core, device validation and the fact that there are no future upgrades.
- Canvas-drawn features (map, heat map) keep their fixed room and heat palettes.
- ADR 0002 is kept for history only.
