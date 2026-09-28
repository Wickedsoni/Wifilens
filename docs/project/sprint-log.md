# Sprint Log

Scrum-lite log for WifiLens 2.0 (plan: [ROADMAP.md](ROADMAP.md), rules: [ANDROID_GUIDELINES.md](../engineering/ANDROID_GUIDELINES.md)).
Token figures are the remaining-context counter the agent sees (the only measure available to it).

## Definition of Done (every sprint)
- `./gradlew spotlessCheck detekt testDebugUnitTest lintDebug assembleRelease` green
- detekt baseline did not grow; `graphify update .` run
- dead files removed, docs/ADRs updated
- device gate passed on the physical phone (user-approved), merged to `main`

## Backlog status

| Sprint | Goal | Status |
|---|---|---|
| 0 | Knowledge base + project setup | Done |
| 1 | Toolchain, Hilt, `:core:common`, lifecycle-aware state | Done |
| 2 | Pure M3 Expressive design system (+ B-30 hint) | In review (device gate) |
| 3 | Logo, splash, motion | To do |
| 4 | Permission handling | To do |
| 5 | Strings + accessibility | To do |
| 6 | DB v2, multiple plans, JSON import/export | To do |
| 7 | Walk survey + calibration | To do |
| 8 | Signal history + charts | To do |
| 9 | PNG/PDF report | To do |
| 10 | Performance (baseline profile, benchmarks) | To do |
| 11 | Release (AAB, policy, listing) | To do |

## Sprint 0: Knowledge base
- **Tokens:** start 14,900,504, end 14,867,700 (~33k used)
- **Done:** shallow-cloned the 6 reference repos into the scratchpad; wrote `docs/engineering/ANDROID_GUIDELINES.md`; saved the roadmap; linked the guide from `CLAUDE.md`; deleted the clones.
- **Findings:** stable toolchain is AGP 9.4.1 / Kotlin 2.4.20 / BOM 2026.09.00. ai-samples is cloud/Gemini-based, so it isn't adopted (privacy-first). Physical-device baseline-profile generation needs API 33+ or root.
- **Session 2:** installed Android CLI 1.0 (`--no-metrics`) and `skills add --all --agent=claude-code`, which put 26 skills in `~/.claude/skills`. `android init` also dropped an `android-cli` skill into the .gemini, .codex and .junie folders. Studied the Kotlin 2.4.20 notes (guidelines §1a). KMP was rejected and the app stays pure Android (§1b). The test device is the Moto Edge 40.
- **Retro:** WebFetch can't render JS pages (the KMP get-started page); fall back to sibling doc pages. Kept every clone outside the repo, so there was nothing to clean up.

## Sprint 1: Build and architecture foundation
- **Tokens:** start 14,969,500 (the session-2 counter was reset, so this is measured from the post-Sprint-0 commit), at the device gate 14,894,000 (about 75k used)
- **Done:**
  - Toolchain: AGP 9.4.1, Kotlin 2.4.20, Compose BOM 2026.09.00, Java 17 (library and JVM conventions). Gradle 9.7.1 shows no KGP compatibility warning, so it's kept.
  - `:core:common` (dispatcher qualifiers, `ApplicationScope`, `Clock`, `Async`, `WhileUiSubscribed`) and `:core:testing` (`MainDispatcherRule`).
  - Koin replaced by Hilt 2.60.1 through the `wifilens.android.hilt` convention plugin. Koin is fully removed (ADR 0006).
  - Dispatchers are injected (speed test, Diagnose). `collectAsStateWithLifecycle` everywhere.
  - Map errors are now UI state (`errorMessage` + `DismissError`) instead of a `Channel`.
- **Verification:**
  - Green: `spotlessCheck detekt testDebugUnitTest lintDebug assembleRelease` and `compileDebugAndroidTestKotlin`.
  - detekt: `@Inject` constructors are exempt from `LongParameterList` (config change, not a baseline entry).
- **Deferred:**
  - The Hilt instrumented runner (`@HiltAndroidTest`) waits until a test launches `MainActivity`. Today every UI test drives screens directly.
  - `DiagnoseViewModelTest` still waits in real time. The injected dispatcher makes it deterministic, but that's a test rewrite, planned for the refactor pass.
- **Device gate (Moto Edge 40, Android 15/API 35): PASSED**
  - `connectedDebugAndroidTest`: 36/36 green.
  - Release (R8) build: installs and launches, cold start 1,170 ms (baseline profile comes in Sprint 10).
  - Scripted over adb: 4 rotations and process death (`am kill`), then relaunch. No crashes or app errors in logcat.
  - The user checked manually: tabs, rotation state, speed test, error Snackbar, settings.
  - The user found B-30 ("+ New room" doesn't start a new map). It's by design but confusing, so it's logged and scheduled for Sprints 2 and 6.
- **Retro:**
  - Hilt's compile-time graph paid off immediately.
  - The DoD gate caught a detekt issue before merge.
  - Scripting the device checks over adb saved screenshot tokens.

## Sprint 2: Material 3 Expressive design system
- **Tokens:** start about 14,994,000 (the counter reset between turns), at the device gate 14,941,800 (about 52k used this turn, plus about 85k in the previous turn).
- **Done:**
  - `MaterialExpressiveTheme` + `MotionScheme.expressive()`. material3 is 1.5.0-alpha27 (ADR 0005), so Compose core stays on stable 1.12.1.
  - Dynamic colour is the default. Brand schemes at 3 contrast levels come from Material Color Utilities.
  - Signal colours are fixed `ColorScheme` extensions, recomputed to AA after `ContrastTest` caught failures in the high-contrast schemes.
  - Nothing tokens, fonts and components removed (APK is now 2.0 MB).
  - `NavigationSuiteScaffold`, and the Expressive `LoadingIndicator` and wavy progress.
  - Map: floating toolbar of toggle buttons, M3 top bar controls, filled "Run diagnosis".
  - More: large flexible top app bar, list items with icons, and a nested nav graph (fixes the sub-screen being lost on rotation and adds predictive back).
  - B-30..B-34 fixed.
- **Verification:**
  - DoD gate green.
  - Moto Edge 40: `connectedDebugAndroidTest` 36/36.
  - Release (R8) launches with no crashes; cold start 1,026 ms.
  - Visual matrix: dynamic dark, brand light, 200% font (restored to the user's 1.15 afterwards).
- **Deferred:**
  - Sentence-case copy is Sprint 5 (one pass with the string extraction).
  - `AnalyzeScreen.kt` (677 lines) gets split during the same Sprint 5 pass.
  - `ButtonGroup` has no natural use yet.
- **Retro:**
  - `connectedDebugAndroidTest` uninstalls the app, so reinstall before manual checks.
  - Contact sheets of downscaled screenshots kept visual QA cheap.
  - The material3 alpha versus Compose alpha trap was caught by checking transitive versions before building.
- **Device gate:** _awaiting user manual check_
