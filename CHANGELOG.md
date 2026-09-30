# Changelog

All notable changes to WifiLens. Versions follow `MAJOR.MINOR.PATCH`; the Play `versionCode` is shown in brackets
and only ever grows. Bug ids (B-nn) refer to [docs/bug-log.md](docs/bug-log.md).

## [2.0.2] (202) - 2026-09-30

### Fixed
- **Network name:** Analyze shows the connected network's name on Android 12 and later instead of "Connected
  network" (B-55).
- **Spectrum:** opens on the band you're connected to, or the first band that has networks, instead of always on
  2.4 GHz (B-56).
- **Speed test:** a network that blocks the test (common on school and office Wi-Fi) now says so, instead of
  telling you to check your internet; the cause is logged (B-54).
- **Diagnose layout:** the Best spot result is centred under the map, and the four-way tab control no longer
  squeezes its labels (B-57).

## [2.0.1] (201) - 2026-09-30

### Fixed
- **Best spot:** a router already on a tile as good as the best one is reported as optimal instead of being offered a
  0 dB move (B-29).
- **Map:** a room name moves above a device pin placed on it, so both stay readable (B-51).

### Changed
- The privacy policy moved to <https://wifilens.garvitmaheshwari.in/privacy/>; the old address redirects.

## [2.0.0] (200) - 2026-09-29

The 2.0 rebuild: Material 3 Expressive design, Hilt, a modular architecture and four new features.

### Added
- **Multiple floor plans**, with JSON import and export.
- **Walk survey:** record the real signal where you stand and calibrate the prediction to your home.
- **History:** 24-hour signal history per network, busy hours, speed-test history.
- **Health check** with plain-language fixes and a channel planner.
- **Coverage report** shared as a PDF or image.
- **Home-screen widget** and **Quick Settings tile**.
- 3D view with orbit and pinch zoom; undo and redo in the 2D editor.

### Changed
- New design system on Material 3 Expressive ([ADR 0005](docs/adr/0005-m3-expressive.md)).
- Dependency injection with Hilt ([ADR 0006](docs/adr/0006-hilt.md)); database v2 ([ADR 0007](docs/adr/0007-db-v2-multiple-plans.md)).
- Baseline Profile, R8 full mode, shipped as an Android App Bundle signed with Play App Signing.

## [1.0.0] - 2026-09-22

First version: nearby networks, a single floor plan with coverage prediction, and the Best spot optimizer.

[2.0.2]: https://github.com/Wickedsoni/Wifilens/compare/v2.0.1...v2.0.2
[2.0.1]: https://github.com/Wickedsoni/Wifilens/compare/v2.0.0...v2.0.1
[2.0.0]: https://github.com/Wickedsoni/Wifilens/compare/v1.0.0...v2.0.0
[1.0.0]: https://github.com/Wickedsoni/Wifilens/releases/tag/v1.0.0
