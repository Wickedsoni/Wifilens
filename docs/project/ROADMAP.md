# WifiLens 2.0: final Play Store release plan

## Update 2026-09-29 (session 2): do these next, in order
Sprint 0 is finished on branch `sprint/00-knowledge-base`. It isn't committed yet. The user's new instructions:
- Install the Android CLI.
- Commit, then start Sprint 1.
- Take in the Kotlin docs.
- The test device is the **Moto Edge 40**.
- The app stays **pure Android** (no KMP).

1. **Install the Android CLI.** Download it with Git Bash `curl`, since Google says PowerShell downloads aren't supported. Find the Windows link from `developer.android.com/tools/agents`. Then run `android init` and `android skills add --all`. If no Windows build can be found, stop and tell the user rather than guessing URLs. Record where the skills landed. If they install into the repo, add them to `.gitignore` or commit them deliberately.
2. **Update the docs with what I learned:**
   - **`ANDROID_GUIDELINES.md`, new §1a "Kotlin 2.4.20":**
     - Language/stdlib: `allDistinct()`, `allEqual()`, `allDistinctBy()`, `allEqualBy()` (experimental, `@OptIn(ExperimentalStdlibApi::class)`); `StackTraceRecoverable` for coroutine stack-trace recovery (experimental); lazy-message `kotlin.test` assertions (`@OptIn(ExperimentalKotlinTestApi::class)`).
     - **Rule: no experimental opt-ins in production code for a final release.** Experimental APIs are allowed in tests only.
     - Build: Kotlin 2.4.20 supports Gradle 7.6.3 to 9.7.0. The repo is on **9.7.1**, so check for warnings. If AGP 9.4.1 allows it, pin 9.7.0; otherwise record the warning.
     - The `kotlin` runner command is now `kotlinr`.
     - `invokedynamic` `when` is stable, but only for JVM 21+ targets. Android stays on JVM 17, so it doesn't apply.
     - API reference links: stdlib, kotlin.test, coroutines, serialization, datetime, immutable collections, Gradle plugin.
   - **§1b "Platform scope":** pure Android. KMP was evaluated and rejected because iOS has no Wi-Fi scan API. Pure modules still avoid needless `java.*` usage.
   - Update the device references in the guidelines, the ROADMAP and `CLAUDE.md` to the **Moto Edge 40**:
     - It's API 33+, so baseline profiles can be generated on the connected device (`useConnectedDevices = true`, no managed emulator).
     - It has a 144 Hz display, so set the frame-timing targets for that refresh rate.
   - Remove emulator mentions from the gates.
3. **Commit Sprint 0:** `docs: sprint 0 engineering guidelines, roadmap and sprint log`. Merge it to `main` with fast-forward, then create `sprint/01-foundation`.
4. **Sprint 1** (scope unchanged; see below). Order of work:
   1. Record the starting token count.
   2. Toolchain: AGP 9.4.1, Kotlin 2.4.20, the matching KSP, BOM 2026.09.00, JVM 17. Build, then commit.
   3. `:core:common` (qualifiers, `ApplicationScope`, `Async`, `WhileUiSubscribed`). Commit.
   4. The Hilt convention plugin plus the Hilt dependencies (Hilt, `hilt-navigation-compose`). Commit.
   5. Migrate from Koin to Hilt one module at a time (core, then data, then presentation, then app). Commit after each.
   6. Remove Koin.
   7. Inject dispatchers, switch to `collectAsStateWithLifecycle`, `stateIn` and the `userMessage` state. Commit.
   8. Test infrastructure (`CustomTestRunner`, `MainDispatcherRule`, `@TestInstallIn` fakes). Commit.
   9. Run the DoD gradle gate.
   10. **STOP for the Moto Edge 40 device gate.**
   11. Refactor pass, update the sprint log, retro.

## Context
WifiLens v1 has shipped, and v2 phases 0 to 3e are done: guardrails, `:core:model`, clean-architecture domain/data/presentation splits, the M3 scheme on Nothing tokens, Navigation Compose, and M3 Switch, NavigationBar, SegmentedButton, Chip and Snackbar. This is the **final version**, and there will be no updates after launch, so it has to ship production-grade to Google Play.

User decisions:
- **Pure Material 3 Expressive.** The Nothing look is dropped, which supersedes ADR 0002.
- **Migrate Koin to Hilt.**
- **All four features:** multiple plans + JSON import/export, walk survey + calibration, signal history charts, PNG/PDF report.
- **Privacy policy hosted on GitHub Pages.** The user already has a Play account.

Gaps found in the audit:
- Permissions:
  - The location prompt fires on launch with no rationale (`MainActivity.kt:84`).
  - Permission state is held in Activity fields and isn't re-checked on resume.
  - There's no `NEARBY_WIFI_DEVICES` for API 33+.
- Coroutines and state:
  - Dispatchers are hard-coded (`DiagnoseViewModel.kt:124,148`, `SpeedTest.kt:48`).
  - `collectAsState` is used instead of `collectAsStateWithLifecycle`.
- Strings: only `app_name` is in `strings.xml`. Every other string is hard-coded.
- Launch and branding: there's no splash-screen API, the launcher icon has no themed (monochrome) layer, and there's no logo.
- Build: AGP is `9.3.0-alpha12` (alpha, so not suitable for a release), and Java targets 11.
- Performance: no baseline profile or macrobenchmark.
- Docs: `DEVELOPMENT.md` lists "multiple plans / export" as out of scope, which is now outdated.

## Working agreement
- **Scrum-lite.** Each sprint below is one increment, and each goes through the SDLC in order: Requirements → Design (an ADR if the decision is architectural) → Implement → Verify (build, unit tests, lint, detekt) → Validate (physical device) → Review/Retro (short notes in `docs/project/sprint-log.md`).
- **Definition of Done for every sprint:**
  - `./gradlew spotlessCheck detekt testDebugUnitTest lintDebug assembleRelease` is green.
  - The detekt baseline only shrinks.
  - `graphify update .` has been run.
  - Dead files are deleted.
  - Docs are updated.
  - The device gate has passed.
- **Device gates (STOP points).** Whenever a physical-device test is needed, I stop and ask you to connect the phone. I carry on only after you confirm. Then I run `connectedDebugAndroidTest` and walk you through a short manual checklist. I never skip a gate and move on to the next sprint.
- **Git.** One branch per sprint (`sprint/NN-short-name`) with Conventional Commits. I merge to `main` only after the gate passes and you approve.
- **Refactor pass** at the end of each sprint: files over 300 lines, naming, duplicated code. Structure and logic never change in the same commit.
- **Token discipline:**
  - Use `graphify query/explain` before reading source, and read only the lines I need.
  - No subagents except for the one-time repo study in Sprint 0.
  - Keep reports short.
  - Record the remaining-token counter at the start and end of each sprint in `sprint-log.md`. That counter is the only token measure I can see.
- **Naming.** Follow the Kotlin and Android style guides:
  - `PascalCase` types, `camelCase` members, `UPPER_SNAKE` constants.
  - Composables are `PascalCase` nouns, and ViewModels are `XxxViewModel` with an `XxxUiState`.
  - Use cases are verb phrases (`ObservePlansUseCase`).
  - Resources follow `feature_screen_element` (`map_toolbar_undo`).
  - Test names are backticked sentences.

## Sprint 0: Knowledge base and project setup
1. Shallow, sparse-clone the reference repos over **https** into the scratchpad, never into the project:
   - architecture-samples
   - studio-projects
   - android-test
   - codelab-android-compose
   - performance-samples
   - ai-samples (for patterns only, since the app stays offline and privacy-first)
2. Extract only the patterns that apply to us:
   - layering and UDF, the Hilt module layout, `Result`/`asResult`
   - dispatcher qualifiers, `stateIn(WhileSubscribed)`
   - test doubles, test rules, Compose test tags, macrobenchmark and baseline profiles
   - M3 Expressive and animation codelabs
3. Try `android skills add --all`, the Google Android CLI. It isn't on PATH, so installing it needs your OK. If it can't be installed, fall back to the Android skills that are already installed.
4. Write **`docs/engineering/ANDROID_GUIDELINES.md`**. It's the permanent, WifiLens-specific reference: architecture rules, the Hilt layout, coroutines/Flow rules, the permission pattern, M3 Expressive usage, motion specs, naming, the testing pyramid, performance, and the release checklist. Link it from `CLAUDE.md` so future sessions read it instead of re-studying the repos.
5. Save this plan as `docs/project/ROADMAP.md` and create `docs/project/sprint-log.md` (backlog, DoD, retros, token log).
6. Delete the clones.

**Gate:** docs review by you. No device needed.

## Sprint 1: Build and architecture foundation
- **Toolchain:**
  - Move to the latest **stable** AGP, Kotlin and KSP.
  - Move Java/JVM target to 17.
  - Add a `wifilens.android.hilt` convention plugin in `build-logic/`.
- **Hilt migration:**
  - `@HiltAndroidApp`, `@AndroidEntryPoint` on `MainActivity`, `@HiltViewModel` on every ViewModel, `hiltViewModel()` in nav destinations.
  - One `@Module @InstallIn(SingletonComponent)` per data module. The modules replace the Koin modules one to one.
  - Remove Koin from `libs.versions.toml`.
- **New `:core:common`:**
  - `@Dispatcher(WifiLensDispatchers.IO/Default)` qualifiers plus an `ApplicationScope`, as in the samples.
  - `Result` and `asResult()`.
  - Inject the dispatchers wherever they're hard-coded today.
- **State collection:**
  - Use `collectAsStateWithLifecycle` everywhere.
  - Expose state with `stateIn(viewModelScope, WhileSubscribed(5_000), initial)`.
  - Replace one-shot `Channel` events with UI-state flags, as the Google guidance recommends.
- **Tests:** `HiltAndroidRule` plus `@TestInstallIn` fakes in androidTest, and a `MainDispatcherRule` in unit tests.

**Gate:** device regression (all four tabs, rotation, process death).

## Sprint 2: Pure M3 Expressive design system
- Rewrite `:core:designsystem`:
  - `MaterialExpressiveTheme` with `MotionScheme.expressive()`.
  - Dynamic colour **on by default** (API 31+), with a brand seed scheme (light, dark and medium/high-contrast) as the fallback.
  - Roboto Flex as the typeface, since it's OFL-licensed.
  - Expressive shapes.
  - Rename every `Nothing*` to `WifiLens*`, and delete the Nothing tokens, fonts and icons.
- Keep a fixed, accessible `SignalColors` palette so heat-map and signal meaning never follows the wallpaper.
- Adopt these components:
  - `NavigationSuiteScaffold` (adapts across phone, foldable and tablet)
  - `LargeFlexibleTopAppBar`
  - `ButtonGroup`, split buttons
  - `HorizontalFloatingToolbar` for the Map tools
  - `LoadingIndicator` and wavy progress (for the speed test)
  - M3 cards, lists and bottom sheets
- Write ADR 0005, which supersedes 0002.
- Screen-by-screen restyle order: Analyze, Map, Diagnose, More.

**Gate:** device visual pass in light, dark and dynamic colour at 200% font scale.

## Sprint 3: Brand, logo and motion
- **Logo:** I'll show 3 vector concepts in a preview artifact (a lens with Wi-Fi arcs as the theme), and you pick one. Then I build:
  - an adaptive icon with foreground, background and **monochrome** (themed) layers
  - the 512 px Play icon and the 1024×500 feature graphic
- **Launch:** `core-splashscreen` with an animated icon (AVD), with no white flash.
- **Motion:**
  - Spring motion scheme throughout.
  - `SharedTransitionLayout` from the network list to the detail view.
  - `AnimatedContent` for state changes and `animateItem()` for lists.
  - Predictive-back animations.
  - Respect the system's animator scale and remove-animations settings.

**Gate:** device check of the launch, the themed icon and the motion.

## Sprint 4: Permission handling
- A `PermissionRepository` plus a gate ViewModel replace the Activity fields. Status is re-checked on `Lifecycle.ON_RESUME`, which covers returning from Settings.
- **Requests in context, with a rationale before each.** Every state is handled:
  - granted
  - approximate-only
  - denied
  - permanently denied (shown as a Settings deep link)
  - location services off
  - Wi-Fi off
  - scan throttled (explained in the UI)
- ~~On API 33+, add `NEARBY_WIFI_DEVICES`~~ **Disproved by the on-device spike:** Wi-Fi scanning needs precise location on every Android version. `NEARBY_WIFI_DEVICES` isn't declared (see guidelines §5).
- Move the gate into the nav graph so the app can still be used with scanning off.

**Gate:** device matrix of grant, deny, deny twice, approximate-only, revoke in Settings, then return.

## Sprint 5: Strings, accessibility, polish
- Move strings into per-module `strings.xml` (plurals, format args, content descriptions).
- Turn on lint `HardcodedText` as an error.
- Accessibility pass:
  - TalkBack order and labels
  - 48 dp touch targets
  - contrast tests on the new schemes
  - `ui-test-junit4-accessibility` checks

**Gate:** device TalkBack walkthrough.

## Sprint 6: DB v2, multiple plans, JSON import/export
- Room migration 1→2, additive only:
  - a `plans` table and `planId` columns, with the existing data backfilled into a default plan
  - `activePlanId` added to settings
  - `measurements`, `scan_samples` and `speed_tests` tables, created now for Sprints 7 and 8
  - the `LIMIT 1` query in `GridPlanDao` removed
- A plans list screen: create, rename, duplicate and delete.
- Versioned JSON export and import (kotlinx.serialization, the Storage Access Framework so no new permission, validated inside a transaction).
- `MigrationTestHelper` tests that assert the old data is preserved.

**Gate:** install v1 with data, upgrade, and confirm the data survives.

## Sprint 7: Walk survey and calibration
- Survey mode on the map: tap a tile to record the averaged RSSI for the chosen BSSID, with the throttle shown in the UI.
- A least-squares fit of A and n in `:core:rf` (pure JVM, with a minimum-sample guard and RMSE).
- A measured-vs-predicted overlay toggle.
- **Live signal meter** ("find the dead spot", approved 2026-09-29): a large, smoothly updating gauge for the connected network, with optional haptic ticks as signal rises or falls. It shares the survey's live-RSSI plumbing and is foreground only.

**Gate:** a real walk survey plus the live meter on the device.

## Sprint 8: Signal history and charts
- Record samples while the app is in the foreground (throttled to about one per network every 30 s).
- An hourly channel-congestion rollup.
- A 7-day retention prune run by WorkManager.
- Custom Compose Canvas charts with downsampling.
- **Speed-test history** (approved 2026-09-29): every speed test is stored in a `speed_tests` table (created in the Sprint 6 migration) and shown as a list plus a time-of-day chart, to reveal patterns like slower evenings.

**Gate:** device test with a 30+ minute session and several speed tests.

## Sprint 9: Network insights (approved 2026-09-29)
All built offline from the scan and connection data the app already reads:
- **One-tap Wi-Fi health check:** combines signal, channel congestion, band and an optional speed test into one plain verdict with a concrete fix (for example, "Crowded channel: switch your router to channel 11").
- **Mesh and extender insight:** which access point (BSSID) you're connected to, and a flag when a stronger AP with the same network name is nearby. `WifiConnectionInfo.Connected` gains `bssid`.
- **Security check:** flags open, WEP, WPA/TKIP and mixed WPA2/WPA3 networks, with one-line explanations.
- **Best-band hint:** tells you when a 5 or 6 GHz network of the same name is available but you're on 2.4 GHz.
- **Channel planner:** extends the existing Spectrum "Best channel" card (`ChannelRecommendation`) to recommend the least crowded channel for every band.
- Each rule is a pure, unit-tested domain function.

**Gate:** device check against the real home network (verdicts make sense, and fixes are correct).

## Sprint 10: Home-screen widget and Quick Settings tile (approved 2026-09-29)
- **Jetpack Glance widget:** current signal (dBm + status colour), band, channel and link speed, "updated X min ago", and tap to open WifiLens and scan. Refreshed every 15 min (WorkManager, Android's minimum) and on tap. Reuses Sprint 8 history for a small trend line.
- **Quick Settings tile** (`TileService`): signal and band in the subtitle; tap opens WifiLens and scans.
- **Deliberately no background location.** Scan results and the network name count as location data in the background, and background location needs a Play policy declaration and review. The widget and tile show only non-location data (RSSI, frequency, link speed), with "Connected" instead of the name.

**Gate:** device check of the widget (sizes, light/dark, themed), the tile, refresh and tap-through.

## Sprint 11: PNG/PDF report
- Render the plan and coverage off-screen, build the report with `PdfDocument`, and share it through `FileProvider` (no storage permission).

**Gate:** share the report to Drive/Files on the device.

## Sprint 12: Performance and quality
- A `:baselineprofile` module (Baseline Profile Gradle plugin) and a `:benchmark` macrobenchmark module (startup, map scroll, and frame timing on the Analyze list).
- Compose stability report fixes, and StrictMode in debug builds.
- LeakCanary (debug builds only).
- R8 full mode with keep-rules verified.
- An APK/AAB size check.

**Gate:** benchmarks run on the device. Targets are cold start under 500 ms and no jank above 5%.

## Sprint 13: Release
- Version `2.0.0`, with the versionCode set explicitly.
- Signed AAB from `keystore.properties`, with Play App Signing enrolled.
- CI builds the release AAB.
- `PRIVACY.md` plus a GitHub Pages policy page, linked from About.
- Data-safety answers (no data is collected; the speed test contacts a server).
- Store listing text, screenshots for phone and tablet, and the content-rating questionnaire answers, all under `docs/release/`.
- Update the README, `DEVELOPMENT.md` (scope section) and the ADRs. Delete stale docs and screenshots.
- **Note:** a personal Play account created after November 2023 must run a closed test with **12 testers for 14 days** before production. I'll prepare the tester instructions.

**Gate:** full regression on the device using the release build, then tag `v2.0.0`.

## Files to create
- `docs/engineering/ANDROID_GUIDELINES.md`
- `docs/project/ROADMAP.md`, `docs/project/sprint-log.md`
- `docs/adr/0005-m3-expressive.md`, `docs/adr/0006-hilt.md`, `docs/adr/0007-db-v2.md`
- `core/common/`, `baselineprofile/`, `benchmark/`
- `build-logic/.../HiltConventionPlugin.kt`
- `PRIVACY.md`, `docs/release/*`

Existing code to reuse: `callbackFlow` scanners in `:core:wifi`, `GridPlan`/rf math in `:core:rf`/`:core:model`, the clean-architecture use-cases from Phase 2, the migration test harness in `app/src/androidTest`, and CI in `.github/workflows/ci.yml`.

## Verification
- **Roadmap v2 (2026-09-29):** 13 sprints after the user approved the feature ideas (widget/tile, network insights, live meter, speed-test history).
- **Every sprint:** the DoD gradle gate, then the device gate (the user connects the phone), then the manual checklist, recorded in `sprint-log.md`.
- **End to end before release:**
  - On the Moto Edge 40: install the v1 APK with data, upgrade to the 2.0 release AAB (via bundletool), and confirm the data survives.
  - Test all permission paths and all four tabs plus the new features.
  - Check rotation, process death, dark mode, dynamic colour and font scale.
  - Run the benchmarks.
  - Run the Play pre-launch report on the internal track.
