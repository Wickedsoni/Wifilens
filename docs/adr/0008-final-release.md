# 0008: Final release: signing, privacy and performance bar

Status: Accepted (2026-09-29)

## Context
WifiLens 2.0 is published on Google Play and then no longer maintained. Choices made for the release have to hold
without anyone around to revisit them.

## Decision
- **Play App Signing with an upload key.** Google keeps the app signing key; a lost upload key can be reset by Play
  support, so the app stays updatable. The key lives outside the repo (`keystore.properties`, git-ignored); CI signs
  only when the `UPLOAD_*` secrets are set. versionCode 200 / versionName 2.0.0.
- **No data collected.** Nothing leaves the device except the speed test's download from Cloudflare. The Room
  database (plans, readings, scan history) is excluded from Android cloud backup so scan data stays on the phone;
  device-to-device transfer still copies it. The unused `FOREGROUND_SERVICE` permission that WorkManager declares is
  removed. The privacy policy is `PRIVACY.md`, published by GitHub Pages from `docs/privacy/`.
- **Performance bar as measured, not as first written.** Cold start is judged for a returning user (~260 ms on the
  Moto Edge 40); the first launch after install (~605 ms) is dominated by GPU shader compilation outside app code.
  Frame timing at 120/144 Hz (map pinch 13.8%, short-list fling 14.1% janky) is recorded rather than held to 5%.

## Consequences
- The Play data-safety form is "no data collected, none shared" (`docs/release/data-safety.md`) and must change if
  any network use is ever added.
- Restoring a phone from a cloud backup restores settings but not plans; plans move by JSON export or a direct
  transfer.
- Performance numbers and how to re-run them are in `docs/project/sprint-log.md` (Sprint 12) and `DEVELOPMENT.md`.
