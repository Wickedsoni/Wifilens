# WiFiLens bug log

Living tracker for the testing phase. Each bug has an ID, severity, status and how it was found.
Statuses: **Fixed** (has a regression test or verified fix), **Open** (found, not yet fixed), **Verify** (suspected, needs proof).
Severity: High = data loss / crash / unusable feature, Medium = feature broken in a common situation, Low = polish or edge case.

Test environment: Pixel_8a emulator (Android 16 / API 36, x86_64), debug build, Gradle unit tests, instrumented tests.

## Cycle 1: found and already fixed

| ID | Sev | Bug | Found by | Guard |
|----|-----|-----|----------|-------|
| B-01 | High | Map reverted to Living Room after adding a room: save wrote cells and rooms separately, observers saw new cells with old rooms; dirty flag cleared even when edited mid-save | User report, then integration test (fails when the transaction is disabled) | `MapRepositoryIntegrationTest.saveIsAtomicForObservers`, `MapViewModelTest.stale database emission...` |
| B-02 | High | Cleared plan reappeared ~1.5 s later (queued debounced autosave wrote it back) | Unit test | `MapViewModelTest.clearing the plan resets everything` |
| B-03 | High | Non-domain exception during save (e.g. SQLite disk full) crashed the app and killed autosave | Unit test | `MapViewModelTest.unexpected save exception...` |
| B-04 | High | Privacy claims ("no INTERNET permission") false after adding the speed test | Screenshot of permission gate | Copy updated in README, DEVELOPMENT.md, About, permission gate (decision: keep test) |
| B-05 | Med | Wall-material labels used `::class.simpleName` (R8 renames in release) | Code review | Fixed with explicit `displayName()`; release build not yet verified on device |
| B-06 | Med | Analyze showed `<unknown ssid>` / quoted SSID as connected network | Emulator | Fixed with `toDisplaySsid()`; needs re-check on the emulator |
| B-07 | Med | Lint error MissingPermission in core:wifi | Lint | Permission declared in core:wifi manifest |
| B-08 | Low | Speed tab empty value rendered as squares; optimizer "x / total tiles" wrong denominator | Emulator, code review | Fixed |

## Cycle 2: found by black-box / exploratory testing (all Open)

| ID | Sev | Bug | Repro | Evidence |
|----|-----|-----|-------|----------|
| B-09 | Med | **Selected tab is lost on activity recreation** (rotation, airplane-mode toggle): app jumps back to Analyze | Open Map (or Diagnose), rotate device or toggle airplane mode | After rotation the UI dump showed Analyze, not Map | **FIXED (cycle 3, compiled + unit tests green, not yet re-verified on device): rememberSaveable tab**
| B-10 | Med | **Speed tab is not scrollable**: "Run speed test" button and explanation text are clipped in landscape and at 2x font, so the test cannot be started | Diagnose > Speed, rotate to landscape or set font scale 2.0 | UI dump had no button node in either case | **FIXED (cycle 3, compiled + unit tests green, not yet re-verified on device): Speed tab scrolls**
| B-11 | Med | **Map tool dock is not scrollable**: at 2x font DEVICE is cut off (device pins cannot be placed) and ROUTER wraps to two lines | Font scale 2.0, open Map | Dock showed ROOM, ERASE, DOOR, WALL, ROUTER only | **FIXED (cycle 3, compiled + unit tests green, not yet re-verified on device): dock scrolls horizontally, no wrap**
| B-12 | Med | **No way to clear, resize or recreate the plan, or rename/delete a room** from the UI. `MapAction.ClearPlan` and `MapEvent.PlanCleared` exist but nothing dispatches or handles them | Create a plan; search the UI for any clear/reset entry | Code search: `ClearPlan` only in `MapState.kt` and `MapViewModel.kt` | **FIXED (cycle 3): Reset plan (confirm sheet), Edit room (rename/delete) chips; VM tests**
| B-13 | Med | **Performance on large plans**: 200x200 plan renders 58.5% janky frames (90th pct 150 ms, 99th pct 600 ms); worst GPU frame ~5 s in ISO view | Create plan 200x200, paint-drag, switch to ISO | `dumpsys gfxinfo` after the run | **FIXED (cycle 3): 2D: wall runs merged, patterns/arcs skipped on tiny tiles; ISO: reused Path, flat tops above 2,500 tiles, off-screen culling. Measured first draw of a 200x200 plan on the emulator: ISO ~730 ms, 2D ~200-430 ms (was ~5 s worst frame); guarded by `MapUiTest` budgets (1.5 s)**
| B-14 | Med | **"Run diagnosis" button on Map does nothing** (`onClick = { /* wired when :feature:diagnose exists */ }`) even though Diagnose exists | Map > add room, router, device, tap the button | Code: `MapScreen.kt` | **FIXED (cycle 3, compiled + unit tests green, not yet re-verified on device): button switches to Diagnose when ready**
| B-15 | Med | **Errors are invisible**: `MapEvent.ShowError` is collected and dropped, so a failed save/pin placement tells the user nothing | Code review (`MapScreen.kt` `viewModel.events.collect { }`) | Code | **FIXED (cycle 3, compiled + unit tests green, not yet re-verified on device): error banner (4 s, tap to dismiss)**
| B-16 | Low | Plan size silently clamped (999 becomes 200, 0 becomes 1) with no message; 1-wide plans are accepted | Create plan with 0 or 999 | 1x20 strip created; 999x999 created as 200x200 | **FIXED (cycle 3): sheet validates 5..200 with inline error**
| B-17 | Low | Empty room name is silently rejected: sheet stays open with no message (duplicate/whitespace names untested) | + New room, tap Create with empty name | Sheet unchanged | **FIXED (cycle 3): inline error for blank/duplicate/too-long names; VM guards too**
| B-18 | Low | Undo, Redo and 2D/ISO controls are shown when there is no plan | Fresh install, Map tab | Screenshot | **FIXED (cycle 3, compiled + unit tests green, not yet re-verified on device): undo/redo/2D-ISO hidden without plan**
| B-19 | Low | Active room name appears twice (plain label plus selected chip) | Map with a plan | Screenshot | **FIXED (cycle 3): duplicate label removed**
| B-20 | Low | Speed tab: button says "Test again" after a failed first attempt; static copy says link speed is an "upper bound" but the emulator shows 2 Mbps link vs 45 Mbps measured | Wi-Fi off, run test; compare on emulator | Screenshot | **FIXED (cycle 3, compiled + unit tests green, not yet re-verified on device): "Test again" only after success; upper-bound copy reworded**
| B-21 | Low | Painted room tile is very low contrast in dark mode | Dark mode, paint a tile | Screenshot | **FIXED (cycle 3): brighter dark-mode room palette**
| B-22 | Low | Analyze "Wi-Fi is off" copy says "Nothing leaves the phone." now that a speed test exists (still true for scan data, but should be reviewed with the privacy wording) | Turn Wi-Fi off, open Analyze | Screenshot | **FIXED (cycle 3): copy now "Scan results never leave the phone." (Analyze + permission gate)**
| B-23 | Low | Analyze screen in landscape leaves almost no room for the list (nav bar plus header take most of the height); not verified whether the list scrolls | Rotate on Analyze | Screenshot | **FIXED (cycle 3): header/status/filters scroll with the list**
| B-24 | Info | Lint: `Modifier` should be first optional parameter (Map, Diagnose, Settings screens); 7 unused colour resources; old target API; InlinedApi on API 30 constants | `./gradlew lint` | Lint reports | **FIXED (cycle 3): Modifier-first params, unused colours and redundant label removed; lint 0 errors. Remaining: OldTargetApi/dependency updates (deliberately left)**

## Verify (suspected, not yet proven)

- V-1: **Fixed** - `moveRouter` catches failures and shows a banner (unit test).
- V-2: **Fixed** - names trimmed, capped at 30, duplicates rejected (unit tests). Emoji layout not eyeballed.
- V-3: **Hardened** - non-IO exceptions become Failed, connections disconnect on cancel; ViewModel is activity-scoped so tab switch/rotation keep the run. No-internet path already gave Failed. Not driven on device.
- V-4: **Partly verified** - `assembleRelease` (R8) builds and lint passes; release APK not run on device.
- V-5: **Fixed by review** - `ACTION_WIFI_SCAN_AVAILABILITY_CHANGED` only exists from API 30 but was used from API 29 (Throttled never detected there); now API 30+, polling below. Verified on API 26 and API 29 emulators: full instrumented suite passes and the app starts and shows live scan updates via the polling path.
- V-6: **Partly done** - tool dock now 48dp tall with selectable/tab semantics (tested), map canvases have content descriptions. Diagnose canvases now have content descriptions. Contrast computed from the tokens (WCAG AA = 4.5:1): primary/secondary text pass in both themes; **fail**: textDisabled (3.7 dark / 2.6 light) used for captions, accent text 4.05 (dark) and light-mode success 3.04 / warning 2.03 as text. Error banners changed to white on deep red (~6:1). Token changes are a design decision, left as-is. TalkBack walkthrough not done (needs a device session).

## What passed

Monkey stress test (1,500 events): no crashes or ANRs. Permission flow, tab switching, plan creation, room creation, persistence across force-stop, dark mode, and the speed test (Wi-Fi on and airplane mode) behave correctly.

## Coverage so far (updated cycle 3)

62 JVM unit tests from earlier cycles plus 9 added this cycle (7 Map ViewModel, 2 Diagnose ViewModel; total not re-counted) and 25 instrumented tests (10 gestures, 8 Room integration, 7 new Compose component/perf tests in `MapUiTest`). CI workflow added at `.github/workflows/ci.yml` (unit tests, lint, R8 release build; not yet run on GitHub). Older figure below.

### Earlier figure

62 JVM unit tests (rf 20, analyze 9, Map VM 17, Diagnose VM 11, speed test 5), 18 instrumented tests (10 existing gestures, 8 Room integration). Not yet built: Compose component tests, CI.


## Cycle 3 addendum: found while fixing

| ID | Sev | Bug | Fix |
|----|-----|-----|-----|
| B-25 | High | Residual B-01: plan and rooms were two flows; after a save the plan re-emitted before rooms, the ViewModel briefly saw new cells with the old room list and reset the active room to the first one for good. Surfaced as a flaky `saveIsAtomicForObservers` (failed ~2 of 4 runs) | Single `observePlan()` snapshot query (`GridPlanSnapshot`); instrumented suite 18/18 green on 5 consecutive runs |

Still open: V-5 (minSdk 26-29 behaviour) and V-6 (TalkBack / touch targets) need a device or an accessibility pass and were not done. The RESET, Edit-room and dock changes have not been visually checked on the emulator.

| B-26 | Med | Best-spot result never went stale: after "Move router here" (or any plan/router/pin change) Diagnose kept showing the old gain and offering the same move | `DiagnoseViewModel` drops optimizer results when plan, router or pins change; unit tests (also enabled `isReturnDefaultValues`, the optimizer's `Log.d` had made it untestable on the JVM) |

Diagnose plan/rooms read (suspected mismatch like B-25): traced, not a bug. Rooms and pins are combined inside one `flatMapLatest` that restarts with the plan, so each emission is consistent, and rooms are only used for labels.

## Real-device run (Motorola Edge 40, Android 15 / API 35)

- Instrumented suite: **25/25 pass** on the physical device (gestures, Room integration, Compose component tests, 200x200 render budgets).
- Debug build cold start about 1.2 s; release (R8) build installs and runs: live scan list renders, Map empty state shows without Undo/Redo/2D-ISO (B-18 confirmed on hardware), no crash from the app.
- Not done on the device: creating a plan and driving the editor, speed test on real Wi-Fi (phone was on mobile data), TalkBack. Still unverified: API 26-29 behaviour (V-5).

## Cycle 4: remaining open items

| ID | Sev | Item | Result |
|----|-----|------|--------|
| B-27 | Med | Automated accessibility audit (Accessibility Test Framework, `AccessibilityTest`) found `NothingChip` touch target 26dp (Analyze band filters, Map room chips) | Fixed: 48dp hit area with compact pill, tab/selected semantics; `NothingGhostButton` also 48dp with button role; Map context strip height relaxed to 48dp |
| B-28 | Med | Text colours below WCAG AA: disabled text (3.7 dark / 2.6 light), dark accent text 4.05, light success 3.0 / warning 2.0 | Fixed in tokens (dark disabled 8A8A8A, dark accent FF4A52, light disabled 666666, light secondary 4D4D4D, light success 29753A, light warning 8A5F00); guarded by `ContrastTest` (JVM) and on-pixel audit tests |
| V-5 | - | minSdk 26-29 | API 26 and 29 emulators: 34 instrumented tests, 0 failures (2 render-budget tests skipped there: software-rendered old emulators exceed Compose's idle timeout regardless of code). App launches, scan list updates by polling |
| Editor E2E | - | Map flows never driven through the UI on hardware | `MapEndToEndTest` (real screen + ViewModel + Room): create plan, add / rename / delete room, reset with confirm, persistence across a new ViewModel. Passes on the Motorola Edge 40 and API 26/29/36 emulators |
| B-13 | - | Render times on hardware | Motorola Edge 40: 2D 200x200 first draw ~63 ms |
| CI | - | Workflow could not run locally | Added `chmod +x gradlew` (the wrapper is committed non-executable, which would fail on Linux). Still not run on GitHub |
| targetSdk | - | Lint OldTargetApi (target 36, compile 37.1) | Deliberately unchanged: no API 37 device or image to verify behaviour changes on |
| Speed test on real Wi-Fi | - | Phone was on mobile data (the test refuses off Wi-Fi) | Not verified on hardware; verified on the emulator's Wi-Fi earlier. Needs the phone on Wi-Fi |
| TalkBack | - | Hands-on screen-reader pass | Not done; the automated audit covers labels, target size and contrast only |

Coverage now: 34 instrumented tests (gestures, Room integration, Compose components, accessibility audit, Map end-to-end, render budgets) plus the JVM suites, run on a physical phone and API 26/29/36 emulators.
