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
| 2 | Pure M3 Expressive design system (+ B-30 hint) | Done |
| 3 | Logo, splash, motion | Done |
| 4 | Permission handling | Done |
| 5 | Strings + accessibility | Done |
| 6 | DB v2, multiple plans, JSON import/export (+ speed_tests table) | Done |
| 7 | Walk survey + calibration + live signal meter | Done |
| 8 | Signal history + charts + speed-test history | Done |
| 9 | Network insights (health check, mesh, security, best band, channel planner) | Done |
| 10 | Home-screen widget + Quick Settings tile | Done |
| 11 | PNG/PDF report | To do |
| 12 | Performance (baseline profile, benchmarks) | To do |
| 13 | Release (AAB, policy, listing) | To do |

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
- **Device gate (Moto Edge 40): PASSED.** The user confirmed after the B-35 root-cause fix: Navigation 2.10's predictive-back defaults (found from the user's screen recording). All fades were removed at the user's request (tabs instant, sub-screens slide).
- **Retro addendum:** four iterations on B-35 because adb key events and the app's arrow don't take the predictive-back path. Next time, ask for a device recording on the first report.

## Sprint 3: Brand, logo and motion
- **Tokens:** start about 14,999,100, at the device gate 14,936,700 (about 62k used).
- **Done:**
  - Logo concept B ("coverage grid") picked by the user from three presented concepts (private artifact).
  - `wifilens_mark` is the single vector for the adaptive foreground, the monochrome themed icon and the in-app `WifiLensLogo`. Legacy PNG mipmaps deleted.
  - Animated splash (`core-splashscreen`, tiles light up from the router, 760 ms). Window background matched to the brand surface.
  - About shows the real `versionName`, which was hard-coded "1.0" before.
  - Play icon (512 px) and feature graphic (1024×500) in `docs/release/store/`.
  - Analyze rows keyed by the raw BSSID (deduped, with a test) and animated on re-sort with no fades.
- **Build:** `core-splashscreen` caused test-classpath conflicts under AGP consistent resolution. Fixed with constraints (concurrent-futures 1.2.0, fragment 1.9.1) and by excluding `listenablefuture` from test APKs only.
- **Verification:**
  - DoD gate green, 36/36 on-device tests.
  - Splash animation verified frame by frame from a cold-start recording.
  - Release build: cold start 862 ms, 2.0 MB.
- **Not applicable:** `SharedTransitionLayout` (there's no list-to-detail screen) and predictive-back motion (the user chose no fades, so tabs are instant and sub-screens slide).
- **Device gate (Moto Edge 40): PASSED.** The user confirmed the launcher icon, themed icon, splash animation and About screen.

## Sprint 4: Permission handling
- **Tokens:** start about 14,999,500, end about 14,999,000 in the current counter (the counter resets between turns; this sprint used roughly 35k in total).
- **Done:**
  - In-context gate: no dialog on launch, explanation first.
  - Every outcome handled: approximate-only, "don't ask again" leading to Settings (with steps), Wi-Fi off leading to the in-app panel.
  - Re-checked on every resume.
  - `GateViewModel` (Hilt) + pure `resolveGate()` with 8 unit tests. `MainActivity` is thin.
  - B-36 fixed: real status rows.
- **Spike result (important):** `NEARBY_WIFI_DEVICES`/`neverForLocation` does **not** allow Wi-Fi scanning on Android 13+. The Android 15 platform log said "startScan not allowed ... UID has no location permission". The permission was removed, precise location is used on every version, and guidelines §5 was corrected.
- **Device gate (Moto Edge 40): PASSED.** The user tapped through: Allow, Don't allow, deny twice leading to Settings, approximate-only leading to precise, and revoke in Settings then return.
- **Retro:** the planned spike paid off. Ship-blocking platform assumptions get verified on hardware before building on them.

## Sprint 5: Strings, accessibility, polish
- **Done:**
  - All UI text is in per-module `strings.xml` (sentence case, format args, plurals, locale decimals), and `NoHardcodedUiTextTest` guards it.
  - The domain returns data instead of prose (`WifiSecurity`, typed `Finding`, `RoomNameProblem`, null hidden SSID). ViewModels use `UiText`, and errors log their cause instead of showing raw exception text.
  - `AnalyzeScreen` split into 4 files. Duplicated room-name validation removed. `isReturnDefaultValues` moved to the convention plugin.
  - Accessibility: headings, merged rows, labelled 48 dp stepper, severity not conveyed by colour alone, extended ATF audits.
  - Bugs fixed: B-37 (dead buttons), B-38 (sort unreachable), B-39 (stale licences), B-40 (stepper accessibility).
- **Verification:**
  - DoD gate green.
  - Moto Edge 40: 38/38 instrumented tests.
  - Release build launches with no crashes; cold start 642 ms.
- **Device gate (Moto Edge 40): PASSED.** Automated checks plus the user's TalkBack walkthrough (rows, sort toggle, headings, switch rows, stepper labels).

## Sprint 6: DB v2, multiple plans, JSON import/export
- **Done:**
  - Schema v2 with a hand-written additive `MIGRATION_1_2` (ADR 0007): plan timestamps, active plan = most recently opened, calibration columns, and tables for measurements, scan history, channel roll-ups and speed tests.
  - `PlanRepository`/`PlanDao` split from the Map content APIs. Saves are addressed by plan id.
  - Versioned JSON import/export through SAF (validated before writing; imports always create a new plan).
  - Floor plans sheet (switch, rename, duplicate, export, delete, new, import). Create sheet has a name field.
- **Found and fixed during design:** `savePlan` wrote to whichever plan was active *at save time* and reset the name to "Home". With multiple plans, a late autosave would have overwritten the wrong plan. Now id-addressed; switches flush first and drop queued saves (VM tests).
- **Quality:**
  - detekt's size/complexity findings led to real splits (`PlanRepository`, `PlanDao`, small rule checks) rather than suppressions.
  - A stray `+` in `build-logic/build.gradle.kts` (uncommitted, not from this session) broke the build and was restored to the committed version.
- **Verification:**
  - DoD gate green.
  - Unit tests: 8 codec, 3 plan-switching, domain rules.
  - Moto Edge 40: 3/3 migration tests. Repository integration tests (incl. 4 multi-plan) written; they run in the device gate.
- **Device gate (Moto Edge 40): PASSED.**
  - 44/44 instrumented tests.
  - Real upgrade: the v1 build (from `main`, in a temporary worktree, since removed) got a user-made plan, then Sprint 6 was installed over it. The data survived, with no crash.
  - The user checked the plans sheet (new, switch, rename, duplicate, export/import, delete).
  - B-41 (room vs map confusion, second report) was fixed in-sprint by clearer wording, per the user's choice to keep rooms as areas.

## Sprint 7: Walk survey, calibration, live signal meter
- **Done:**
  - `:core:rf` least-squares fit of the log-distance model (`rssi + wallLoss = A − n·10·log10 d`) with a 5-reading minimum, a spread guard (≥ 3 dB of log-distance), n clamped to 1.5–6, and RMSE.
  - Data: `MeasurementDao` (running average per plan/tile/BSSID), calibration stored on the plan row, `SurveyRepository`, calibration carried in `PlanSnapshot` and Diagnose's `PlanContext`, `observeLive()` polling of the connection (no scan quota), BSSID when Android doesn't redact it.
  - Map **Measure** tool: tap the tile you stand on; ~3 s of samples are averaged and saved (confirm haptic); readings drawn in the shared signal colours with a pulse on the tile being measured; strip with live signal, count, Calibrate and Clear readings (confirmed). Its own `SurveyViewModel`, so `MapViewModel` stays about editing.
  - Diagnose uses the calibrated model when the plan has one (Calibrated badge, stale optimizer results dropped), plus a **Signal** tab: spring-animated gauge, one-minute trace with good/fair guides, best/worst/band/link, and a haptic tick on quality-tier changes. Polling only while the tab is on screen.
  - Signal colour scale moved to the design system so Map and Diagnose always agree.
- **Verification:**
  - DoD gate green (spotless, detekt, unit tests, lint, release build).
  - New unit tests: 9 calibration fit, 3 per-tile merge, 8 survey ViewModel/sampler, 3 signal meter, 2 calibration override.
  - Moto Edge 40: 44/44 instrumented tests (the Map end-to-end test now builds its `SurveyViewModel` by hand).
- **Device gate (Moto Edge 40), run unattended overnight at the user's request:**
  - Checked by script: plan creation, room creation, painting, Measure tool strip and pulse, offline error path, Signal tab offline state, 4-tab Diagnose control.
  - Found and fixed: B-42 (sheet button under the navigation bar), B-43 (missing space in the caption), B-44 (slow offline failure). Re-verified on the device.
  - **Pending for the user (needs a person and a Wi-Fi network):** the phone wasn't connected to Wi-Fi overnight, so a real walk survey (measure 5+ spots near and far, Calibrate, check the Calibrated badge in Diagnose) and the connected Signal meter (gauge, trace, tier tick while walking) still need a hands-on pass.
  - **Not done from the plan:** the measured-vs-predicted *comparison* toggle. Readings are drawn as measured values in the Measure tool; a "difference from prediction" view is carried to the backlog.

## Sprint 8: Signal history, charts, speed-test history
- **Done:**
  - `:core:history`: `HistoryRepository` on the Sprint 6 tables. One sample per access point per 30 s (the throttle survives restarts), an hourly per-channel congestion roll-up that keeps the busiest reading, and speed tests stored with the connection they ran on.
  - `ScanHistoryRecorder` listens passively to fresh scans only while the process is started: no extra scans, no background location.
  - `HistoryPruneWorker` (WorkManager + Hilt, daily): samples 7 days, roll-ups 30 days, newest 500 speed tests. The Application supplies WorkManager's configuration.
  - Charts in the design system (`WifiLensLineChart`, `WifiLensBarChart`) with min/max downsampling that keeps dips and spikes.
  - Analyze: tap a network for its 24 h signal history (best/average/worst); Spectrum gets a "Busy hours" card (band congestion per hour, gaps shown as gaps).
  - Diagnose → Speed: speed history by hour of day with the slow hour called out ("usually slower around 21:00"), plus the latest tests.
  - B-45: Analyze's list now uses the same signal thresholds as the rest of the app.
- **Verification:**
  - DoD gate green (after two real detekt fixes: `HistoryDao` split into scan history and `SpeedTestDao`; chart data in its own file).
  - New unit tests: 4 history repository, 2 downsampling, 3 speed-by-hour, 2 congestion-by-hour, 1 speed test stored with its connection.
  - Moto Edge 40: 44/44 instrumented tests.
- **Device gate (Moto Edge 40):**
  - Checked by script: a scan was recorded and the network's history sheet showed it (trace point and best/average/worst); Busy hours showed the current hour with earlier hours as gaps; Speed history showed its empty state.
  - Found and fixed: B-46 (sort button wrapped to three lines). Re-verified; B-45's shared thresholds visible in the list.
  - **Pending for the user:** the phone wasn't on a Wi-Fi network, so the 30+ minute session and several speed tests (history chart, slow-hour callout) still need a hands-on pass.

## Sprint 9: Network insights
- **Done:**
  - Pure, unit-tested rules in the Analyze domain: signal grade (app-wide thresholds, now in `:core:model`), crowded channel with a concrete switch (DFS caveat), better band (same network on 5/6 GHz with at least fair signal), stronger access point of your network on the same band (a device's own radios excluded), mesh detected, security issues, speed. Worst first; empty when not connected.
  - Security check from the capability strings Android really writes (read from the device with `cmd wifi list-scan-results`): open, WEP, WPA1, TKIP, WPA2/WPA3 transition. This exposed **B-47** (WPA3 is written as SAE; a WPA3-only network showed as Open), fixed.
  - Channel planner: the quietest channel on every band in the scan, including bands you're not on; shown even before joining a network.
  - Analyze **Health** tab: verdict ("2 things to fix" / "looks healthy"), findings with fixes, planner. "Run health check" adds a speed test, stored in speed-test history.
- **Verification:**
  - DoD gate green. New unit tests: 9 insight/security/planner rules (real device strings), 3 insights ViewModel.
  - Moto Edge 40: 44/44 instrumented tests.
- **Device gate (Moto Edge 40):**
  - Checked on screen: Health tab disconnected state with the planner (2.4 GHz → ch 6, 5 GHz → ch 36), and B-47 fixed in the list (a transition-mode network now reads WPA3).
  - Changed after the check: the planner used to be hidden while disconnected; it needs only the scan, so it now shows.
  - **Pending for the user (needs a Wi-Fi connection):** the connected verdict against the real home network (do the findings and fixes make sense?) and a health check with its speed test.

## Sprint 10: Home-screen widget and Quick Settings tile
- **Done:**
  - `:feature:widget`: Glance widget (responsive small/medium) with dBm in the app's fixed signal colours (`SignalPalette`, shared from the design system), band, channel, link speed, a three-hour trend (drawn into a bitmap, since Glance has no Canvas) and the refresh time; tap opens WifiLens. Dynamic colour through `GlanceTheme`.
  - `WidgetRefreshWorker` (Hilt + WorkManager): every 15 minutes while a widget exists, plus once when a widget is added and when the app opens. State lives per widget in Glance's preferences; the snapshot logic is pure and unit-tested.
  - Quick Settings tile (`TileService`, Hilt): signal and band in the subtitle (API 29+), active while on Wi-Fi, tap opens the app (the `PendingIntent` overload on API 34+).
  - No network name or BSSID in either: RSSI, frequency and link speed aren't location data, so no background location is needed.
- **Environment issues found and handled:**
  - Windows Smart App Control started blocking the `aapt2.exe` Gradle unpacks into its cache (`CreateProcess error=4551`). The security setting was left alone; with the user's approval, `~/.gradle/gradle.properties` (machine-level, not in the repo) points `android.aapt2FromMavenOverride` at the Google-signed aapt2 in the SDK's `build-tools/36.1.0`.
  - The widget module's (empty) instrumented-test APK ran the 3 GB Gradle daemon out of heap while dexing Glance; `org.gradle.jvmargs` is now `-Xmx4096m`.
- **Verification:**
  - DoD gate green. New unit tests: 4 widget snapshot.
  - Moto Edge 40: 44/44 instrumented tests.
- **Device gate (Moto Edge 40):**
  - The system registered both the widget provider and the tile. The tile was added to Quick Settings (`cmd statusbar add-tile`), showed inactive (the phone wasn't on Wi-Fi), and a tap opened WifiLens.
  - Placed on the home screen by the user. Found and fixed: **B-48** (the widget read "Not on Wi-Fi" while connected: the worker took the flow's first value from `activeNetwork`; it now waits up to 3 s for a connected reading, and the app refreshes it on every return to the foreground, not only on a cold start) and **B-49** (a tall widget floated a small block; a third 180×200 dp size with a bigger reading and trend). Re-verified: a warm reopen refreshed the widget, and it showed "Not on Wi-Fi" correctly after the phone dropped to 5G.
  - A lint run crashed inside lint's Kotlin analysis (`AccessibilityTest.kt`, untouched); re-run on its own it passed. Treated as a lint flake.
  - Connected re-check: the tall size showed -73 dBm (fair colour), Fair signal, Ch 13 · 78 Mbps, a trend and the refresh time; seen in both light (8:07) and dark (7:38) system themes, following the wallpaper colours. Gate passed.
