# EatPlease

A Kotlin Multiplatform sample app targeting **Android** and **iOS**, with UI shared across
both platforms via [Compose Multiplatform](https://www.jetbrains.com/compose-multiplatform/).
The app currently renders an intentionally empty screen — a clean starting point for real
screens to be added later.

## Project structure

- **`composeApp/`** — the Kotlin Multiplatform module containing everything shared:
  - `commonMain` — code shared across all targets, including the root `App()` composable
    (the shared UI) and the `Platform` expect declaration.
  - `androidMain` — Android entry point (`MainActivity`) and Android-specific code.
  - `iosMain` — iOS entry point (`MainViewController`, exposed to Swift) and iOS-specific code.
  - `commonTest` — shared unit tests.
- **`iosApp/`** — the iOS application: a thin SwiftUI host that embeds the shared Compose UI.
  Open this in Xcode to run on an iPhone or simulator.

## Requirements

- JDK 17+
- Android Studio (or the Android SDK + an emulator/device) for the Android app
- macOS with Xcode for the iOS app (Kotlin/Native Apple targets only build on macOS)

## Build & run

### Android

```shell
./gradlew :composeApp:assembleDebug
```

or open the project in Android Studio and run the `composeApp` configuration.

### iOS

Open `iosApp/iosApp.xcodeproj` in Xcode and run the `iosApp` scheme. A build phase
invokes `./gradlew :composeApp:embedAndSignAppleFrameworkForXcode` to compile the shared
Kotlin framework automatically.

### Tests

```shell
./gradlew :composeApp:testDebugUnitTest
```
