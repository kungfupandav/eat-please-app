# Plan: "Eat Please" — on-device eating-action detection for kids

## Context

The merged scaffold (PR #1) is a Compose Multiplatform app (Android + iOS, shared UI, empty screen) with CI and Firebase distribution. Now the real product: **detect whether a kid is eating at a constant pace from live camera video**, fully on-device, using the **MoViNet** video-action-recognition model in **TensorFlow Lite**. Parents press *Start watching*, prop the phone facing the child, and later review a per-second log of detected eating actions.

User decisions:
- **iOS is foreground-only** (Apple forbids background camera without a special entitlement); the iOS app keeps the screen awake during a session. **Android gets true background capture** via a camera foreground service.
- **Camera toggle in app** — front camera by default, switchable on the home screen.
- **MoViNet-A0-Stream** (int8, ~5 MB, 172×172 input, Kinetics-600) — the streaming variant that classifies frame-by-frame with carried internal state, designed exactly for live video.

Since PR #1 merged, work restarts on the same branch name from latest `main` (`git checkout -B claude/kotlin-multiplatform-sample-ygj6jk origin/main`), and ships as a new PR.

## How detection works (shared logic, `commonMain`)

1. Platform camera layer delivers RGB frames (172×172) at ~6 fps to the shared pipeline.
2. `MoViNetClassifier` (expect/actual) runs the A0-Stream TFLite model per frame, feeding the model's recurrent state tensors back in on each call; returns 600 class probabilities.
3. `EatingScoreAggregator` (pure Kotlin, unit-tested): sums the probabilities of the ~10 Kinetics-600 `eating *` classes (+ `tasting food`), applies exponential smoothing and enter/exit hysteresis thresholds (~0.35/0.20, tunable constants), and emits **one `EatingSecond` event per wall-clock second** while eating is detected — this is the "close to the second" log resolution.
4. `PaceAnalyzer` (pure Kotlin, unit-tested): computes per-session stats — eating seconds, bites/min over rolling windows, average interval between eating bursts, longest pause, and a "constant pace" verdict (std-dev of inter-burst intervals under a threshold).
5. `WatchSessionManager` orchestrates session start/stop and persists events.

**Storage**: **Room KMP** (androidx.room 2.7.x with the Room Gradle plugin + KSP, `androidx.sqlite:sqlite-bundled` driver for a single code path on both platforms). Entities `WatchSession(id, startedAt, endedAt)` and `EatingEvent(sessionId, atEpochSecond, confidence)`; `@Dao` with Flow-returning queries for the log screens; `@Database` + `RoomDatabase` builder via expect/actual (context on Android, NSDocumentDirectory path on iOS).

**Model & labels assets**: `kinetics_600_labels.txt` is committed (fetchable now from raw.githubusercontent.com). The `.tflite` model is fetched by a Gradle `downloadMoViNetModel` task into `composeResources/files/` (gitignored, cached) — model hosts are blocked in this sandbox but reachable from CI and dev machines; loaded at runtime via `Res.readBytes` → interpreter-from-memory on both platforms.

## UI (`commonMain`, shared Compose)

- **HomeScreen**: status card (idle / watching since HH:mm), **Start watching / Stop watching** button, front/back camera toggle, live "eating now / last seen Xs ago" indicator while active, link to log. On app reopen, state reflects the still-running session so the user can stop it (requirement).
- **LogScreen**: list of sessions → session detail with stats header (duration, eating seconds, avg bites/min, longest pause, pace verdict) and the per-second timeline (HH:mm:ss + confidence rows).
- Navigation: simple sealed-class screen state in `App.kt` (no nav library).

```
┌─────────────────────────────┐   ┌─────────────────────────────┐
│  Eat Please                 │   │  ←  Log                     │
│                             │   │                             │
│  ┌───────────────────────┐  │   │  Today, 12:05 – 12:33  ▸    │
│  │  ● WATCHING           │  │   │  ┌───────────────────────┐  │
│  │  since 12:05          │  │   │  │ 28 min · eating 214 s │  │
│  │  eating now ✓         │  │   │  │ 7.6 bites/min avg     │  │
│  │  (last seen 2 s ago)  │  │   │  │ longest pause 3m 12s  │  │
│  └───────────────────────┘  │   │  │ pace: ~constant ✓     │  │
│                             │   │  └───────────────────────┘  │
│  Camera:  [Front ⇄ Back]    │   │  12:05:14  eating  0.61     │
│                             │   │  12:05:15  eating  0.66     │
│  ┌───────────────────────┐  │   │  12:05:16  eating  0.58     │
│  │      STOP WATCHING    │  │   │  12:05:31  eating  0.44     │
│  └───────────────────────┘  │   │  12:05:32  eating  0.52     │
│                             │   │  …                          │
│  View log ▸                 │   │  (one row per detected      │
│                             │   │   second, HH:mm:ss + conf)  │
└─────────────────────────────┘   └─────────────────────────────┘
   Home (active session)             Log (session detail)
```

Idle home shows the same layout with "START WATCHING", no live indicator; the log's top level is a plain list of past sessions (date, duration, eating seconds) that opens the detail above.

## Platform layers

**Android** (`androidMain` + manifest):
- `WatchForegroundService` with `foregroundServiceType="camera"` — keeps the camera running when the app backgrounds; persistent notification with a Stop action. Permissions: `CAMERA`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CAMERA`, `POST_NOTIFICATIONS` (runtime camera+notification requests in `MainActivity`).
- CameraX `ImageAnalysis` (172×172 target, ~6 fps throttle, YUV→RGB conversion) feeding the shared pipeline.
- `MoViNetClassifier` actual: `org.tensorflow:tensorflow-lite` Interpreter using the model's signature runner (init-states + per-frame invoke).

**iOS** (`iosMain` + `iosApp`):
- Camera in Kotlin/Native via `platform.AVFoundation`: `AVCaptureSession` + video data output (BGRA), crop/scale to 172×172, front/back device switching. `UIApplication.idleTimerDisabled = true` during a session (foreground-only decision). `NSCameraUsageDescription` added to Info.plist.
- `MoViNetClassifier` actual: TensorFlowLite **C API** via the Kotlin **CocoaPods plugin** (`pod("TensorFlowLiteC")` cinterop). This switches iOS integration from the embedAndSign run-script to the standard CocoaPods flow: `iosApp/Podfile` consumes `composeApp` as a local pod, Xcode builds via `iosApp.xcworkspace`. (Fallback if C-API signature-runner symbols are missing in the pod version: invoke the stream model through plain indexed multi-input/output tensors — functionally identical.)

## Files (representative)

- `composeApp/src/commonMain/kotlin/com/eatplease/app/detection/` — `MoViNetClassifier.kt` (expect), `EatingScoreAggregator.kt`, `PaceAnalyzer.kt`, `WatchSessionManager.kt`, `EatingClasses.kt`
- `composeApp/src/commonMain/kotlin/…/data/` — `WatchSession.kt`, `EatingEvent.kt` (entities), `WatchDao.kt`, `EatPleaseDatabase.kt`, `DatabaseBuilder.kt` (expect/actual)
- `composeApp/src/commonMain/kotlin/…/ui/HomeScreen.kt`, `LogScreen.kt`; rewrite `App.kt`
- `composeApp/src/androidMain/kotlin/…/WatchForegroundService.kt`, `CameraFrameSource.android.kt`, `MoViNetClassifier.android.kt`; `AndroidManifest.xml`
- `composeApp/src/iosMain/kotlin/…/CameraFrameSource.ios.kt`, `MoViNetClassifier.ios.kt`
- `composeApp/src/commonTest/kotlin/…/` — aggregator, pace-analyzer, repository tests (JVM-runnable)
- `composeApp/build.gradle.kts` (cocoapods plugin, Room + KSP plugins, camerax/tflite deps, model download task), `gradle/libs.versions.toml`, `iosApp/Podfile`, `iosApp/iosApp/Info.plist`, `.github/workflows/build.yml` (ios job: `pod install`, build workspace), `README.md`

## Delivery: stacked PRs (small, reviewable, each CI-green)

Each PR's branch is based on the previous one; each PR targets the branch below it so reviews show only that layer's diff. Merge top of stack last (GitHub retargets the next PR to `main` automatically as each base merges). Every PR passes the existing CI (tests + both platform builds + Firebase artifacts).

| # | Branch | Contents |
|---|---|---|
| 1 | `claude/eat-detect-1-data` (from `main`) | **Data scaffold**: Room KMP wiring (Room Gradle plugin, KSP, bundled SQLite driver), entities + DAO + database builders (expect/actual), domain models (`EatingSecond`, session types), `DetectionRepository`; DAO unit tests. No UI change. |
| 2 | `claude/eat-detect-2-core` (stacked on 1) | **Detection core**: `EatingScoreAggregator`, `PaceAnalyzer`, `WatchSessionManager`, `MoViNetClassifier` expect declaration + `FakeClassifier` for tests; committed `kinetics_600_labels.txt` + eating-class index parsing; thorough unit tests. Pure Kotlin, no platform code. |
| 3 | `claude/eat-detect-3-ui` (stacked on 2) | **Screens**: Home + Log screens per the mocks, sealed-class navigation, camera-toggle UI state, wired to `WatchSessionManager` with the fake classifier so the whole flow is demoable from Firebase builds before ML lands. |
| 4 | `claude/eat-detect-4-android` (stacked on 3) | **Android detection**: Gradle model-download task, TFLite `MoViNetClassifier` actual (signature runner), CameraX frame source (YUV→RGB, 172×172, ~6 fps), `WatchForegroundService` (background capture + notification Stop action), permissions + manifest. |
| 5 | `claude/eat-detect-5-ios` (stacked on 4) | **iOS detection**: CocoaPods migration (Podfile, workspace, CI ios job update), `pod("TensorFlowLiteC")` cinterop classifier actual, AVFoundation camera source, idle-timer disable, `NSCameraUsageDescription`; README/privacy docs wrap-up. |

Scaffold-vs-implementation split: PRs 1–2 are pure scaffolding/logic (fast reviews, fully unit-tested), PR 3 makes the app demoable end-to-end with a fake detector, PRs 4–5 swap the fake for the real model per platform. If any single PR grows past ~small-review size during implementation (likely candidates: 4 and 5), split it further (e.g. camera source and foreground service as separate stacked PRs) rather than piling on.

Note on branches: the previously designated branch was merged with PR #1; this stack uses fresh sequential branch names as explicitly requested.

## Verification

- **Locally**: aggregator/pace-analyzer unit tests on JVM (scratch flat-classpath method used for the scaffold). Room KMP lives on Google Maven (blocked in this sandbox), so DB-layer tests run in CI only — `testDebugUnitTest` exercises the DAO against the bundled SQLite driver.
- **CI (the real gate)**: Android job runs `testDebugUnitTest` + assembles both APKs; iOS job runs `pod install` + workspace build for simulator. Both already distribute via Firebase.
- **End-to-end**: install the Firebase-distributed debug APK on a phone, start watching, eat on camera — confirm per-second events appear in the log and survive app restart; background the Android app and confirm detection continues (notification present); on iOS confirm the screen stays awake and detection runs while foregrounded; reopen and stop the session from the home screen.
- Confidence values are logged per event so thresholds can be calibrated from real meals.

## Known risks

- MoViNet is trained on Kinetics-600, not specifically on children at a table — detection quality needs the calibration loop above; thresholds are constants on purpose.
- The Android release APK stays debug-signed (already noted in repo) — fine for testing.
- TFLite runtimes come from Google Maven / CocoaPods — CI-only builds, as established for this repo.
