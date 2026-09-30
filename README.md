<p align="center">
  <img src="docs/assets/banner.svg" alt="WifiLens: see where your Wi-Fi reaches" width="100%">
</p>

<p align="center">
  <a href="https://github.com/Wickedsoni/Wifilens/actions/workflows/ci.yml"><img src="https://github.com/Wickedsoni/Wifilens/actions/workflows/ci.yml/badge.svg?branch=main" alt="CI"></a>
  <img src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white" alt="Android 8.0+">
  <img src="https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin and Jetpack Compose">
  <img src="https://img.shields.io/badge/Material%203-Expressive-1B47A6" alt="Material 3 Expressive">
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-MIT-blue" alt="MIT license"></a>
  <a href="https://wifilens.garvitmaheshwari.in/privacy/"><img src="https://img.shields.io/badge/privacy-no%20tracking-success" alt="No tracking"></a>
</p>

<p align="center">
  <b>An Android Wi-Fi analyzer that maps how your signal reaches every room.</b><br>
  Draw your floor plan, drop your router and devices, and WifiLens predicts the signal on every tile,<br>
  lists the dead spots and finds the best place to move the router.
</p>

<p align="center">
  <a href="https://wifilens.garvitmaheshwari.in">Website</a> ·
  <a href="docs/USER_GUIDE.md">User guide</a> ·
  <a href="docs/ARCHITECTURE.md">Architecture</a> ·
  <a href="CHANGELOG.md">Changelog</a> ·
  <a href="CONTRIBUTING.md">Contributing</a>
</p>

---

<table>
<tr>
<td width="38%" align="center"><img src="docs/assets/demo.gif" alt="WifiLens demo: networks, spectrum, health, floor plan in 2D and 3D, coverage and best spot" width="300"></td>
<td>

### What it does

📡 **Analyze** everything on the air: every nearby network with signal, band, channel and security, a spectrum view of
crowded channels, and a one-tap **Health** check that says what to fix in plain language.

🏠 **Map** your home on a simple grid: rooms, walls of different materials and doors, then see it in 3D. A
**walk survey** records the real signal where you stand and calibrates the prediction to your home.

🎯 **Diagnose** the result: predicted coverage for every room, the weakest device, and a **Best spot** search that
finds where the router helps your weakest device most. Plus a speed test, a live signal meter and a shareable
PDF or image report.

📱 **At a glance** with a home-screen widget and a Quick Settings tile.

🔒 **Private by design.** No account, no ads, no analytics. Plans, scans and history stay on the phone; the only
network use is the optional speed test. See the [privacy policy](https://wifilens.garvitmaheshwari.in/privacy/).

</td>
</tr>
</table>

## Screenshots

| Floor plan | Coverage | Best spot | 3D view |
|:---:|:---:|:---:|:---:|
| <img src="docs/release/store/phone/01-map-2d.png" width="200" alt="2D floor plan editor"> | <img src="docs/release/store/phone/02-coverage.png" width="200" alt="Predicted coverage"> | <img src="docs/release/store/phone/03-bestspot.png" width="200" alt="Best router spot"> | <img src="docs/release/store/phone/04-map-3d.png" width="200" alt="3D view of the plan"> |
| **Health check** | **Networks** | **Spectrum** | |
| <img src="docs/release/store/phone/05-health.png" width="200" alt="Health check"> | <img src="docs/release/store/phone/06-analyze.png" width="200" alt="Nearby networks"> | <img src="docs/release/store/phone/07-spectrum.png" width="200" alt="Channel spectrum"> | |

## How the prediction works

The plan is a grid of roughly 1 m tiles. Every tile is a **floor** tile in a room, a **wall** with a material, or a
**door**, so the plan you draw, the heatmap and the optimizer's search space are the same grid.

Signal at a tile follows the log-distance path-loss model:

```
RSSI = A − 10 · n · log₁₀(d) − Σ wall losses
```

| Term | Meaning |
|---|---|
| **A** | Signal 1 m from the router (dBm) |
| **n** | How fast signal fades: ~2.0 in free space, ~3.0 in a typical home (default), up to ~4.0 with dense walls |
| **d** | Distance from the router to the tile (m) |
| **wall losses** | Every wall the straight line from the router crosses, traced cell by cell with Bresenham's algorithm |

A walk survey replaces the default **A** and **n** with a least-squares fit to your own readings. **Best spot**
scores each walkable tile by the *worst* signal among your devices and picks the tile that maximizes it, so one great
room never hides a starved one. More in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md#rf-engine).

## Built with

Kotlin · Jetpack Compose · Material 3 Expressive · MVI with `StateFlow` · Hilt · Room and DataStore · Coroutines and
Flow · WorkManager · Glance · Canvas rendering (no chart or 3D library) · Baseline Profiles · R8 · Android App Bundle

```mermaid
flowchart LR
  app[":app"] --> features
  subgraph features [Feature modules]
    analyze[":feature:analyze"]
    map[":feature:map"]
    diagnose[":feature:diagnose"]
    more[":feature:more"]
    widget[":feature:widget"]
  end
  features --> core
  subgraph core [Core modules]
    designsystem[":core:designsystem"]
    wifi[":core:wifi"]
    database[":core:database"]
    history[":core:history"]
    rf[":core:rf (pure JVM)"]
    model[":core:model (pure Kotlin)"]
  end
```

## Build and run

Needs Android Studio (or just the Gradle wrapper) and JDK 17+. minSdk 26, targetSdk 36.

```bash
git clone https://github.com/Wickedsoni/Wifilens.git
cd Wifilens
./gradlew :app:installDebug     # build and install on a connected device
./gradlew testDebugUnitTest     # unit tests
./gradlew spotlessCheck detekt lint
```

Wi-Fi scanning needs a real device; the emulator has no Wi-Fi radio to scan with.

## Documentation

| Document | For |
|---|---|
| [User guide](docs/USER_GUIDE.md) | Using the app, step by step |
| [Architecture](docs/ARCHITECTURE.md) | Modules, data flow, the RF engine, key decisions |
| [Development guide](DEVELOPMENT.md) | Module table, hard constraints, build setup |
| [Testing](docs/TESTING.md) | Test strategy, the device checklist, how to run everything |
| [Release process](docs/release/README.md) | Versioning, signing, Play Console steps |
| [Decision records](docs/adr/README.md) | Why the architecture is the way it is |
| [Contributing](CONTRIBUTING.md) | Branches, commits, pull requests |
| [Security](SECURITY.md) · [Privacy](PRIVACY.md) · [Changelog](CHANGELOG.md) | |

## License

MIT. See [LICENSE](LICENSE).
