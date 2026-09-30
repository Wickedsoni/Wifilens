# Testing

How WifiLens is tested: what runs where, how to run it, and the manual checks done on a real phone before a release.

## Test pyramid

| Level | Where | What it covers | Runs on |
|---|---|---|---|
| Pure unit | `core/rf`, `core/model`, `feature/*/domain` | Path loss, wall tracing, calibration fit, Best spot, insight rules, channel planner | JVM, milliseconds |
| ViewModel | `feature/*/presentation/src/test` | MVI state, actions, edge cases, with fake repositories and `MainDispatcherRule` | JVM |
| Core Android | `core/wifi/src/test`, `core/history` | Speed test against a local HTTP server (success, 403, reset, 500, unreachable), SSID decoding | JVM |
| Instrumented | `**/src/androidTest` | Room migrations and atomic saves, repository integration | Device or emulator |
| Performance | `:baselineprofile` | Startup and frame timing macrobenchmarks, Baseline Profile generation | Physical device |
| Manual | This page | Real Wi-Fi, permissions, layout, TalkBack | Moto Edge 40 |

Every bug fix comes with a test that fails without the fix; the bug id goes in the test name, for example
`` `a connection reset mid-request is reported as blocked (B-54)` ``.

## Running tests

```bash
./gradlew testDebugUnitTest                    # all Android-module unit tests
./gradlew :core:rf:test :core:model:test \
          :feature:analyze:domain:test :feature:diagnose:domain:test \
          :feature:map:domain:test :feature:more:domain:test   # pure JVM modules
./gradlew connectedDebugAndroidTest            # instrumented, needs a device
./gradlew spotlessCheck detekt checkModuleGraph lint
```

CI (`.github/workflows/ci.yml`) runs formatting, detekt, the module-graph check, all unit tests, lint, the R8 release
build and the release bundle on every pull request. `main` only accepts green PRs.

Connected runs (instrumented tests, profile generation, benchmarks) uninstall the app afterwards, which wipes its
data. Export any plan you want to keep first.

## Device checklist (before each release)

Run on a physical phone (Wi-Fi scanning doesn't work on the emulator) with the **release** build, on a network you're
connected to and one you aren't. Tick every line or log a bug in [bug-log.md](bug-log.md).

### Permissions and start-up
- [ ] Fresh install: the permission screen explains location, and each missing item (permission, Location, Wi-Fi)
      links to the right setting.
- [ ] Deny, then allow: the app recovers without a restart.
- [ ] Cold start shows content in under a second on the test phone.

### Analyze
- [ ] Networks list fills in; the connected network shows its **name**, signal, channel and band.
- [ ] Band filters and Sort work; the fifth quick refresh shows the throttle countdown.
- [ ] Spectrum opens on the connected band; switching bands works and the choice sticks.
- [ ] Health lists findings with fixes when connected, and "Connect to Wi-Fi" when not.

### Map
- [ ] Create a plan; add rooms and paint them; walls with a material; doors; router; named devices.
- [ ] Undo and Redo; remove a device by tapping it with the Device tool.
- [ ] Room names stay readable when a device sits in the middle of a room.
- [ ] 3D view orbits and zooms smoothly.
- [ ] Kill the app mid-edit and reopen: nothing is lost.
- [ ] Export a plan to JSON and import it back.

### Diagnose
- [ ] Coverage shows a heatmap, room list and weakest device; Share report makes a PDF and an image.
- [ ] Best spot runs with progress; the result is centred; Move router here updates the plan; a router already on the
      best tile says so.
- [ ] Speed test finishes on a normal network; on a blocking network it says the network blocked it.
- [ ] Signal meter updates live.

### System surfaces
- [ ] Widget shows the current signal and refreshes on app open; resizes cleanly.
- [ ] Quick Settings tile shows the signal.

### Look and accessibility
- [ ] Light and dark theme, with and without dynamic colour.
- [ ] 200 % font size: no clipped or overlapping labels.
- [ ] TalkBack: every control has a label and a role.
- [ ] Rotate and return: state survives.

## Store screenshots

Taken from the release build with Android's demo status bar (12:00, full battery, no notifications) and Do Not
Disturb on, then exported as 1080 × 1920 PNGs without transparency. They live in `docs/release/store/phone/` and are
also used by the README.
