# EatPlease

A Kotlin Multiplatform app for **Android** and **iOS** that helps parents see whether their
kid is eating at a steady pace. Point the phone at the table, press **Start watching**, and
the app detects eating actions from live camera video — fully on-device — logging every
detected second for review later.

- **Shared UI** via [Compose Multiplatform](https://www.jetbrains.com/compose-multiplatform/)
  with [Navigation 3](https://developer.android.com/guide/navigation/navigation-3)
- **Detection** via the [MoViNet-A0-Stream](https://www.kaggle.com/models/google/movinet)
  video action classifier (Kinetics-600) on TensorFlow Lite — the ~10 `eating …` classes
  are aggregated into one eating score with smoothing and hysteresis
- **Storage** via Room KMP (per-second `EatingEvent` rows, one row per detected second)
- **DI** via [Metro](https://zacsweers.github.io/metro/)

## How it works

1. The platform camera layer delivers RGB frames (172×172) at ~6 fps.
2. MoViNet-Stream classifies each frame, carrying its recurrent state between frames.
3. The shared `EatingScoreAggregator` sums the eating-class probabilities, smooths them,
   applies enter/exit hysteresis, and records at most one eating event per second.
4. The log screen shows each session's per-second timeline plus derived stats:
   duration, eating seconds, bites/min, longest pause, and a pace verdict
   (constant / irregular) from the variability of intervals between eating bursts.

Platform behavior differs deliberately:

| | Android | iOS |
|---|---|---|
| Background detection | ✅ camera foreground service + notification | ❌ Apple stops background camera capture; the app keeps the screen awake instead |
| Stop from outside the app | notification action | reopen the app → Stop |

## Privacy

All video is processed on the device, frame by frame. No frames are stored or uploaded —
the only persisted data are timestamps and confidence scores of detected eating seconds,
in a local database.

## Requirements

- JDK 17+
- Android Studio (or Android SDK) for the Android app
- macOS with Xcode + CocoaPods for the iOS app (Kotlin/Native only builds on macOS)
- Network access on first build: the MoViNet model (~5 MB) is downloaded by the
  `downloadMoViNetModel` Gradle task (cached, gitignored)

## Build & run

### Android

```shell
./gradlew :composeApp:assembleDebug
```

or open the project in Android Studio and run the `composeApp` configuration.

### iOS

```shell
./gradlew :composeApp:podspec
cd iosApp && pod install
open iosApp/iosApp.xcworkspace   # then run the iosApp scheme in Xcode
```

### Tests

```shell
./gradlew :composeApp:testDebugUnitTest
```

## CI

`.github/workflows/build.yml` runs on every pull request and push to `main`:

- **Android**: shared unit tests, debug + release APKs (artifacts + QR codes in the run
  summary), Firebase App Distribution when secrets are configured.
- **iOS**: `pod install` + debug and release simulator builds; signed device builds are
  distributed via Firebase when Apple signing secrets are configured.

See the workflow file and repository secrets section of the run summaries for the
frictionless tester-install links.
