# Noise Timer — Copilot instructions

Open-source Android app: real-time white/pink/brown noise generator with a
sleep timer. Kotlin + Jetpack Compose, no third-party network/analytics
dependencies. See `README.md` for user-facing feature docs.

## Build, test, lint

Always use the wrapper (`./gradlew`), never a system-wide `gradle` —
the project is pinned to Gradle 9.6.1 via `gradle/wrapper/`.

```bash
./gradlew assembleDebug              # unsigned debug APK -> app/build/outputs/apk/debug/
./gradlew assembleRelease            # minified+shrunk release APK (unsigned unless keystore.properties exists)
./gradlew testDebugUnitTest          # JVM unit tests (no emulator needed)
./gradlew testDebugUnitTest --tests "*NoiseSynthesisTest*pink filter is deterministic*"  # single test
./gradlew lintDebug                  # Android Lint; must pass with 0 errors (warnings OK)
```

There is no instrumented/emulator test suite in CI — `NoiseEngine`/`NoiseService`
behavior (foreground service lifecycle, notifications, rotation, process-death)
has historically been verified manually via a booted AVD (`adb`/`uiautomator`),
not via automated instrumentation tests. If you need to validate that kind of
change, boot an emulator and drive it with `adb shell input`/`uiautomator dump`
rather than assuming JVM unit tests cover it.

CI (`.github/workflows/build.yml`) runs `testDebugUnitTest`, `lintDebug`, and
`assembleDebug` on every push/PR to `main`.

Toolchain: JDK 17, AGP 9.3.0 (built-in Kotlin support — **do not** add the
`org.jetbrains.kotlin.android` plugin, only `org.jetbrains.kotlin.plugin.compose`
is applied), compileSdk/targetSdk 37, minSdk 26.

## Architecture

Three-layer split, each independent of the others' lifecycle:

- **`NoiseEngine`** (singleton `object`) — owns all playback state as a
  `StateFlow<NoisePlayerState>`, persisted to `SharedPreferences`. Runs a
  dedicated background `Thread` that synthesizes PCM samples directly into
  an `AudioTrack` (`MODE_STREAM`) — there's no pre-baked/looped audio.
  Exposes direct methods (`play`, `pause`, `stop`, `setNoiseType`, `setVolume`,
  `setTimerMinutes`, `setFadeOutEnabled`). Has no Android Context dependency
  beyond `init(context)` for SharedPreferences.
- **`NoiseSynthesis.kt`** — pure, JVM-testable math extracted out of
  `NoiseEngine`: `PinkNoiseFilter` (stateful class), `nextBrownSample()`
  (pure function), `fadeGainFor()` (pure function). Add new
  noise types or fade curves here, not inline in `NoiseEngine`'s generator
  loop, so they stay unit-testable without Robolectric/emulator.
- **`NoiseService`** (`LifecycleService`) — exists *only* to keep the process
  alive in the foreground (with a notification) while `NoiseEngine.state.isPlaying`
  is true. It contains no playback logic itself; it reacts to `NoiseEngine.state`
  and calls into `NoiseEngine` methods.
- **`MainActivity`** (Compose UI) — calls `NoiseEngine` methods **directly**
  for all settings (noise type, volume, timer, fade-out). It only routes
  through `NoiseService.start()`/`sendPlaybackAction()` for Play/Pause, since
  starting/stopping the foreground service is the service's exclusive job.

### The foreground-service contract (read before touching `NoiseService`)

Android requires `startForegroundService()` to be followed by a synchronous
`Service.startForeground()` call almost immediately, or the system kills the
process with `ForegroundServiceDidNotStartInTimeException`. This codebase has
hit three distinct ways to violate that contract; any future change to
`NoiseService`/`NoiseEngine` startup must avoid re-introducing them:

1. **Never block the main thread** before `onStartCommand`'s `ACTION_PLAY`
   branch reaches `ServiceCompat.startForeground(...)`. This is why all
   `AudioTrack` construction happens inside `NoiseEngine.startAudioThread()`'s
   background `Thread`, not synchronously in `play()`.
2. **`NoiseEngine.state` is a `StateFlow`**, which replays its current value
   to new subscribers immediately. `NoiseService.onCreate()`'s collector uses
   `.drop(1)` for this reason — without it, the replayed `isPlaying=false`
   value would trigger the `else` branch's `stopSelf()` before `onStartCommand`
   gets a chance to call `startForeground()`.
3. **Only actions that actually call `startForeground()` may be sent via
   `ContextCompat.startForegroundService()`.** In practice, that means only
   `ACTION_PLAY`/`ACTION_PAUSE` go through `NoiseService.start()`; every other
   setting (`SET_NOISE`, `SET_VOLUME`, `SET_TIMER`, `SET_FADE`) must call
   `NoiseEngine` directly from the UI/caller, never through the service.

Also: `NoiseEngine.state` ticks `remainingMillis` every 200ms while a timer
runs. The notification collector uses
`.distinctUntilChangedBy { it.isPlaying to it.noiseType }` to avoid re-posting
an identical notification 5x/second — preserve this if you touch that collector.

## Conventions

- All user-facing strings go in `res/values/strings.xml` via `stringResource(...)`
  in Compose — no hardcoded string literals in UI code.
- New Compose interactive controls should carry accessibility semantics
  (`contentDescription`, or `Modifier.toggleable(role = Role.Checkbox, ...)`
  for checkbox-like rows so the whole row is one tap target and one
  screen-reader announcement) — see the volume `Slider` and fade-out `Row`
  in `MainActivity.kt` for the pattern.
- Calls to notification/permission-gated APIs (e.g. `NotificationManagerCompat.notify`)
  must explicitly check `ContextCompat.checkSelfPermission` for revocable
  runtime permissions (POST_NOTIFICATIONS on API 33+) rather than assuming
  they're granted — see `NoiseService.postNotification()`.
- `keystore.properties` (release signing config) is gitignored and optional;
  code must keep working (unsigned release build) when it's absent.
