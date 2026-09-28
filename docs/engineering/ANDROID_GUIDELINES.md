# WifiLens Android Engineering Guidelines

The single source of truth for how WifiLens code is written. Distilled on 2026-09-29 from Google's
reference repos (architecture-samples, studio-projects, android-test, codelab-android-compose,
performance-samples, ai-samples) and adapted to this app. **Read this before changing code; do not
re-study those repos.** When a rule here conflicts with older code, the rule wins and the old code is
migrated as part of the sprint that touches it (see `docs/project/ROADMAP.md`).

---

## 1. Toolchain baseline (2.0 target)

| Item | Version / setting | Source |
|---|---|---|
| AGP | 9.4.1 (stable, never alpha for a release) | studio-projects template |
| Kotlin | 2.4.20, Compose compiler plugin = Kotlin version | studio-projects template |
| Compose BOM | 2026.09.00 (Compose 1.12.1) | studio-projects template |
| material3 | **1.5.0-alpha27**, the only non-stable dependency; see ADR 0005 | M3 Expressive APIs are internal in stable 1.4.0 |
| JVM target | 17 | performance-samples |
| minSdk / targetSdk / compileSdk | 26 / 36 / 37 | app |
| DI | Hilt (Dagger) + KSP, `androidx.hilt:hilt-navigation-compose` | architecture-samples |
| Versions | only in `gradle/libs.versions.toml`; modules use convention plugins in `build-logic/` | NiA-style |

Bump versions only in a dedicated `chore(deps)` commit, never mixed with features.

### 1a. Kotlin 2.4.20 notes (from kotlinlang.org "What's new")

- **New stdlib APIs (experimental):**
  - `allDistinct()` / `allDistinctBy {}` / `allEqual()` / `allEqualBy {}` (`@OptIn(ExperimentalStdlibApi::class)`)
  - `StackTraceRecoverable<T>` + `copyForStackTraceRecovery()` for custom exceptions thrown across coroutines (`@OptIn(ExperimentalStdlibCoroutineSupportApi::class)`)
  - lazy-message `kotlin.test` assertions, e.g. `assertEquals(e, a) { "msg" }` (`@OptIn(ExperimentalKotlinTestApi::class)`)
- **Rule for this final release:** no experimental opt-ins in production source sets. They're allowed only in tests. **The one exception** is `@ExperimentalMaterial3ExpressiveApi`, allowed only inside `:core:designsystem` (ADR 0005). Use stable equivalents in production (`list.distinct().size == list.size`).
- Gradle compatibility is 7.6.3 to 9.7.0. The repo is on Gradle 9.7.1, so watch for KGP compatibility warnings. Pin 9.7.0 if AGP 9.4.1 accepts it.
- The standalone `kotlin` runner is now `kotlinr`. `invokedynamic` `when` is stable only for JVM 21+ targets, so it doesn't apply to our JVM 17 Android target.
- Native, Wasm and JS changes (Swift export, Wasm compilation modes, JS test DSL) don't apply to this app.
- API references:
  - [stdlib](https://kotlinlang.org/api/core/kotlin-stdlib/)
  - [kotlin.test](https://kotlinlang.org/api/core/kotlin-test/)
  - [coroutines](https://kotlinlang.org/api/kotlinx.coroutines/)
  - [serialization](https://kotlinlang.org/api/kotlinx.serialization/)
  - [datetime](https://kotlinlang.org/api/kotlinx-datetime/)
  - [immutable collections](https://kotlinlang.org/api/kotlinx.collections.immutable/)
  - [Kotlin Gradle plugin](https://kotlinlang.org/api/kotlin-gradle-plugin/)

### 1b. Platform scope

WifiLens is **pure Android**. Kotlin Multiplatform was evaluated and rejected: iOS exposes no Wi-Fi scan API, so the core features couldn't exist there. `:core:model` and `:core:rf` stay plain-Kotlin JVM modules. Avoid `java.*` in them where a Kotlin stdlib equivalent exists.

### 1c. Tooling

- **Android CLI** (`%USERPROFILE%\AppData\AndroidCLI\android.exe`, v1.0). Always pass `--no-metrics`. Useful commands: `android docs search|fetch`, `android run`, `android screen capture`, `android sdk ...`.
- **Official Android skills** (installed in `~/.claude/skills`). Load a skill only in the sprint that needs it:

  | Sprint | Skills |
  |---|---|
  | 1 | `agp-9-upgrade`, `testing-setup` |
  | 2 | `adaptive`, `navigation-3`, `edge-to-edge`, `styles` |
  | 4 | `android-permissions-security`, `android-intent-security` |
  | 10 | `android-profiler`, `r8-analyzer` |
  | 11 | `play-policy-insights` |

- **Test device:** Motorola **Moto Edge 40** (API 33+, 144 Hz). Every instrumented test, benchmark and manual validation runs on it via adb (`C:/Users/Avik/AppData/Local/Android/Sdk/platform-tools/adb.exe`). No emulator gates.

## 2. Architecture

Layered, unidirectional data flow (UDF), as in architecture-samples:

```
UI (Composable) ──events──▶ ViewModel ──calls──▶ UseCase (domain) ──▶ Repository (interface, domain)
      ▲                         │                                          │
      └──── StateFlow<UiState> ◀┘                           RepositoryImpl (data) ─▶ Room / DataStore / WifiManager
```

Module map (existing, keep):

- `:app`: the shell only: `MainActivity`, `WifiLensApplication` (`@HiltAndroidApp`), the nav graph, the app-level Hilt modules.
- `:core:model`: pure Kotlin domain models. No Android.
- `:core:rf`: pure Kotlin RF math. **No Android imports, ever** (the `kotlin.jvm` plugin enforces this).
- `:core:common` (new in Sprint 1): dispatcher qualifiers, `ApplicationScope`, `Result`/`asResult()`, `WhileUiSubscribed`.
- `:core:database`: Room + DataStore. `:core:wifi`: platform Wi-Fi flows. `:core:designsystem`: theme + components.
- `:feature:<name>:domain | data | presentation`. `presentation` never depends on `data`; `:app` wires them together.

Rules:

1. **The ViewModel exposes exactly one `StateFlow<XxxUiState>`**, built by `combine(...)` + `stateIn(viewModelScope, WhileUiSubscribed, XxxUiState(isLoading = true))`.
   ```kotlin
   private const val STOP_TIMEOUT_MILLIS = 5_000L
   val WhileUiSubscribed = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS)
   ```
2. **One-off messages are state, not a Channel.** Put `userMessage: Int?` (a string res) in the UI state, then call `viewModel.userMessageShown()` after the Snackbar shows. This is Google's current guidance. Navigation after an action is also a state flag (`isSaved = true`) that the screen reacts to in a `LaunchedEffect`.
3. **Loading/error/success**: map repository flows to `Async.Loading | Async.Success(data) | Async.Error(@StringRes msg)` and `.catch { emit(Async.Error(...)) }` before `stateIn`. A flow must never crash the ViewModel.
4. **Repositories own threading.** They inject `@IoDispatcher`/`@DefaultDispatcher` and use `withContext`. ViewModels and use-cases never name `Dispatchers.*`.
5. **Work that must outlive the screen** (saving a plan, pruning history) runs in `@ApplicationScope` inside the repository, or in WorkManager if it must survive process death.
6. **`SavedStateHandle`** holds small UI selections (tool, selected room, filter). Big data (the grid) lives in Room.
7. **Use-cases are single-purpose classes with `operator fun invoke`**, named with a verb (`ObservePlansUseCase`, `ComputeCoverageUseCase`). Add one only when it holds logic or combines repositories. Don't write pass-through wrappers.
8. **Mappers** (`Entity.asExternalModel()`, `Model.asEntity()`) sit next to the entity in the data layer (`ModelMappingExt.kt` pattern).
9. Screens are split into a stateful `XxxRoute(viewModel = hiltViewModel())` and a stateless `XxxScreen(uiState, onAction...)`. Previews and UI tests target `XxxScreen`.

## 3. Hilt layout

```kotlin
// :core:common
@Qualifier @Retention(AnnotationRetention.RUNTIME) annotation class IoDispatcher
@Qualifier @Retention(AnnotationRetention.RUNTIME) annotation class DefaultDispatcher
@Qualifier @Retention(AnnotationRetention.RUNTIME) annotation class ApplicationScope

@Module @InstallIn(SingletonComponent::class)
object CoroutinesModule {
    @Provides @IoDispatcher fun io(): CoroutineDispatcher = Dispatchers.IO
    @Provides @DefaultDispatcher fun default(): CoroutineDispatcher = Dispatchers.Default
    @Provides @Singleton @ApplicationScope
    fun appScope(@DefaultDispatcher d: CoroutineDispatcher) = CoroutineScope(SupervisorJob() + d)
}
```

- Each `:data` module has `di/XxxDataModule.kt`, an `abstract class` with `@Binds` from the interface to the impl (`@Singleton`).
- Room: `DatabaseModule` `@Provides @Singleton` the database, plus one `@Provides` per DAO.
- ViewModels: `@HiltViewModel class XxxViewModel @Inject constructor(...)`.
- Tests: `CustomTestRunner` swaps in `HiltTestApplication`. `@TestInstallIn(replaces = [XxxDataModule::class])` provides fakes. Use `@HiltAndroidTest` + `HiltAndroidRule(this)` ordered before the compose rule.
- The convention plugin `wifilens.android.hilt` applies `com.google.dagger.hilt.android` + KSP and adds the dependencies. Modules never repeat that setup.

## 4. Coroutines and Flow

- Platform listeners use `callbackFlow { ...; awaitClose { unregister } }` and **emit the current value first** (existing pattern in `:core:wifi`, keep it).
- Collect in Compose with **`collectAsStateWithLifecycle()`** only. Never use `collectAsState()`, which keeps scanning while the app is in the background.
- CPU-heavy work (coverage grid, optimizer, least-squares fit) runs in `withContext(defaultDispatcher)` and must be cancellable: check `ensureActive()` inside long loops.
- No `GlobalScope`. No `runBlocking` outside tests. No `Thread`/`Handler`.
- Wi-Fi scan throttling (4 scans / 2 min in the foreground) is a platform fact. Surface it in UI state and never busy-loop `startScan()`.
- `Flow` operators: prefer `combine`, `map`, `distinctUntilChanged`, `debounce` (autosave), `conflate` (high-rate sensors).
- Tests: `runTest` + a `MainDispatcherRule(UnconfinedTestDispatcher())`. Inject `StandardTestDispatcher(testScheduler)` into repositories. Use Turbine for flow assertions.

## 5. Permissions (Wi-Fi app specifics)

| Need | Permission | Notes |
|---|---|---|
| Scan results, API 33+ | `NEARBY_WIFI_DEVICES` with `android:usesPermissionFlags="neverForLocation"` | runtime |
| Scan results, API ≤ 32 / connected SSID/BSSID | `ACCESS_FINE_LOCATION` (+ `COARSE` in the same request) | runtime; FINE may get `maxSdkVersion` after the Sprint 4 spike |
| Wi-Fi state / trigger scan | `ACCESS_WIFI_STATE`, `CHANGE_WIFI_STATE` | install-time |
| Speed test only | `INTERNET`, `ACCESS_NETWORK_STATE` | install-time |

Pattern:

1. Permission status lives in a `PermissionRepository` that exposes a `StateFlow<ScanAccess>` (sealed: `Granted`, `NeedsRationale`, `Denied`, `PermanentlyDenied`, `ApproximateOnly`, `LocationServicesOff`, `WifiOff`). It's re-evaluated on `Lifecycle.Event.ON_RESUME` (`LifecycleEventEffect`), so returning from Settings updates it.
2. **Never request on launch.** Show an in-context rationale screen first, then `rememberLauncherForActivityResult(RequestMultiplePermissions())`.
3. Treat "permanently denied" as a denied result where `shouldShowRequestPermissionRationale == false` after a request. Offer a button to `Settings.ACTION_APPLICATION_DETAILS_SETTINGS`.
4. The app stays usable without scanning (Map editor, Glossary, Settings). Gate features, not the whole app.
5. Keep the permission rationale text, the About screen, the Play Data safety form and `PRIVACY.md` saying the same thing.

## 6. Design system: Material 3 Expressive

- Root: `MaterialExpressiveTheme(colorScheme, motionScheme = MotionScheme.expressive(), typography, shapes)` in `WifiLensTheme`.
- Colour:
  - Dynamic colour on by default on API 31+ (`dynamicLightColorScheme`/`dynamicDarkColorScheme`), with a user toggle in Settings.
  - The fallback is a brand scheme generated from one seed (light, dark, medium- and high-contrast variants).
  - **Signal/heat-map colours never follow dynamic colour.** They are fixed per-mode `ColorScheme` extensions: `colorScheme.success`, `.warning`, `.danger`, `.isDark` (WCAG-checked by `ContrastTest`, and usable in `DrawScope`).
- Always read colours from `MaterialTheme.colorScheme.*`. Never hard-code a `Color(...)` in a feature module.
- Type: the M3 type scale on the platform font (no bundled fonts). Use `MaterialTheme.typography.*` roles, never raw `sp`. In `DrawScope`, read `WifiLensTypography`.
- Shape: `MaterialTheme.shapes.*`. Use expressive shape morphing (`MaterialShapes`, `toShape()`) for the selected and pressed states of hero elements.
- Components to use:

  | Component | Where |
  |---|---|
  | `NavigationSuiteScaffold` | Adaptive nav: bar, rail or drawer, chosen by window size |
  | `LargeFlexibleTopAppBar` / `TopAppBar` + `exitUntilCollapsedScrollBehavior` | Screen app bars |
  | `HorizontalFloatingToolbar` | Map tools |
  | `ButtonGroup`, `SplitButtonLayout` | Grouped and split actions |
  | `LoadingIndicator` / `ContainedLoadingIndicator` | Scanning |
  | `LinearWavyProgressIndicator` | Speed test |
  | `SnackbarHost` | Messages |
  | `ModalBottomSheet` | Sheets |
  | `ListItem`, `ElevatedCard`/`OutlinedCard` | Lists and cards |
  | `FilterChip`, `SingleChoiceSegmentedButtonRow` | Selection controls |

- Canvas stays mandatory for the floor plan and coverage map (a composable per cell is forbidden).
- Components in `:core:designsystem` are prefixed `WifiLens` (`WifiLensTopAppBar`). Use them only when they add app-wide defaults. Otherwise call M3 directly.

## 7. Motion

- Default springs come from `MaterialTheme.motionScheme` (`defaultSpatialSpec()`, `fastEffectsSpec()`, …). Don't hand-pick durations.
- Toolbox, from the AnimationCodelab plus Expressive:

  | Use | API |
  |---|---|
  | Show/hide | `AnimatedVisibility` |
  | Content swap on state | `AnimatedContent` (with `contentKey`) |
  | Size changes | `animateContentSize()` |
  | Single values | `animate*AsState` |
  | Multi-property state | `updateTransition` |
  | Gestures and fling | `Animatable` + `pointerInput` |
  | Scanning pulse | `rememberInfiniteTransition` |
  | List changes | `Modifier.animateItem()` |
  | List ↔ detail continuity | `SharedTransitionLayout` + `sharedElement` |
  | Back gesture | Navigation Compose's predictive-back transitions |

- Respect accessibility: if the system animator scale is 0 or "Remove animations" is on, skip infinite and decorative animations.
- Splash: `androidx.core:core-splashscreen` with the animated vector icon (keep it under 1000 ms). The window background matches the theme, so there's no white flash.

## 8. Naming conventions

| Kind | Convention | Example |
|---|---|---|
| Package | lowercase, `com.wickedcoder.wifilens.<layer>.<feature>` | `...feature.map.presentation` |
| Class / object / interface | PascalCase, no `I` prefix; impl = `Default`/`Room`/`Platform` + name | `PlanRepository`, `RoomPlanRepository` |
| ViewModel / state / action | `XxxViewModel`, `XxxUiState`, `XxxAction` | `MapViewModel` |
| Composable | PascalCase noun; stateful `XxxRoute`, stateless `XxxScreen` | `CoverageScreen` |
| Use-case | verb + `UseCase` | `FindBestSpotUseCase` |
| Function / property | camelCase; booleans `is/has/can/should` | `isScanning` |
| Constant | `UPPER_SNAKE_CASE` in a companion object or at top level | `STOP_TIMEOUT_MILLIS` |
| Flow backing field | `_uiState` private, `uiState` public | |
| Resource | `feature_screen_element[_state]`, snake_case | `map_toolbar_undo`, `ic_signal_strong` |
| Test tag | `feature:element` constants in the screen file | `"map:canvas"` |
| Test function | backticked sentence | `` `scan result below threshold is hidden` `` |
| Branch / commit | `sprint/NN-name`; Conventional Commits `feat:` `fix:` `refactor:` `test:` `docs:` `chore:` | |

Formatting: ktlint via Spotless (`.editorconfig`) and detekt (`config/detekt`). The baseline only shrinks.

## 9. Testing pyramid

| Level | Tool | What |
|---|---|---|
| Unit (JVM, most tests) | JUnit + `kotlinx-coroutines-test` + Turbine + fakes | ViewModels, use-cases, repositories with fake data sources, `:core:rf` math (parameterized) |
| Room | `MigrationTestHelper`, in-memory DB (androidTest) | Every migration asserts preserved data |
| UI | `createAndroidComposeRule<HiltTestActivity>()`, `onNodeWithTag`, `useUnmergedTree` for merged semantics, `waitUntil` over sleeps | Critical flows per screen |
| Accessibility | `ui-test-junit4-accessibility` checks, TalkBack pass on device | Labels, contrast, 48 dp targets |
| Performance | Macrobenchmark (`StartupTimingMetric`, `FrameTimingMetric`) | Cold/warm start, Analyze list scroll, Map pan |

- Prefer **fakes over mocks** (`FakePlanRepository` in a `shared-test` source set or module). Fakes implement the real interface.
- Stable `testTag`s are the selectors. Never select by display text (it changes with strings and locales).
- **Physical-device rule:** instrumented tests, benchmarks and manual validation run on the user's phone. The agent stops and asks before any device step.

## 10. Performance

- Baseline profile:
  - Add a `:baselineprofile` module (`androidx.baselineprofile` plugin) with a generator that walks startup → Analyze → Map → Diagnose.
  - Add `profileinstaller` to `:app`.
  - Generate on the connected Moto Edge 40 (API 33+, so `useConnectedDevices = true` and no managed devices).
- Frame targets are for the Edge 40's 144 Hz panel (about a 6.9 ms frame budget). Aim for P90 frame time under 10 ms and under 5% janky frames.
- `:benchmark` module, a `com.android.test` project with `targetProjectPath = ":app"`, self-instrumenting. It measures startup with `CompilationMode.None`, `Partial(BaselineProfileMode.Require)` and `Full`.
- Compose:
  - Keep models `@Immutable`/`@Stable`, since the domain models are data classes.
  - Hoist lambdas, give `LazyColumn` stable `key`s, and defer state reads (use `drawBehind` and lambda modifiers for animated values).
  - Check the compiler stability reports.
- R8: `isMinifyEnabled` + `isShrinkResources`. Add keep rules only for kotlinx.serialization models and anything reflective. Verify the release build on the device.
- Debug builds only: StrictMode (thread + VM policies) and LeakCanary.

## 11. AI samples (evaluated, not adopted)

ai-samples uses the Firebase AI Logic (Gemini cloud) and ML Kit GenAI (Gemini Nano on-device) SDKs. WifiLens is offline and privacy-first, and the 2.0 scope has no AI feature, so none is added. If one is ever wanted, only the on-device ML Kit GenAI path would fit the privacy promise.

## 12. Release checklist (Play Store)

- [ ] `versionCode` incremented and `versionName` = semver. Stable AGP, no `-alpha` dependencies.
- [ ] Signed with the upload key from the git-ignored `keystore.properties`, enrolled in Play App Signing. Upload an **AAB**.
- [ ] targetSdk meets Play's current requirement (36).
- [ ] Privacy policy URL (GitHub Pages) linked in the Play listing and the About screen.
- [ ] Data safety form matches the code: no data collected or shared. The speed test contacts a download server.
- [ ] Permission declarations: location is used for Wi-Fi scanning only, and none is background.
- [ ] Adaptive icon with a monochrome layer, 512×512 Play icon, 1024×500 feature graphic, phone + 7"/10" tablet screenshots.
- [ ] Content rating questionnaire, target audience, ads = none.
- [ ] Personal developer account: a closed test with 12+ testers for 14 days before production access.
- [ ] Pre-launch report clean. Release build smoke-tested on the physical device.
