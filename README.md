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

## CI

`.github/workflows/build.yml` runs on every pull request and push to `main`:

- **Android**: shared unit tests, then debug + release APKs (uploaded as artifacts,
  with download buttons and QR codes in the run summary).
- **iOS**: debug + release simulator builds via `xcodebuild` (uploaded as artifacts).

## Firebase App Distribution (frictionless tester installs)

CI can push every debug and release build to
[Firebase App Distribution](https://firebase.google.com/docs/app-distribution), giving
testers an install link / QR code that works directly on their phones — no GitHub
account needed. The distribution steps activate automatically once the repository
secrets below are configured, and are skipped otherwise.

### One-time setup

1. Create a Firebase project, then add an **Android app** with package name
   `com.eatplease.app` and an **iOS app** with bundle ID `com.eatplease.app.EatPlease`.
2. Enable App Distribution for both apps and create a tester group (default expected
   name: `testers`; override with the `FIREBASE_TESTER_GROUP` secret).
3. Create a service account (IAM → Service Accounts) with the
   **Firebase App Distribution Admin** role and download its JSON key.
4. Add the GitHub repository secrets:

| Secret | Value |
|---|---|
| `FIREBASE_SERVICE_ACCOUNT` | Full JSON of the service-account key |
| `FIREBASE_ANDROID_APP_ID` | Android app ID (`1:…:android:…` from Firebase project settings) |
| `FIREBASE_IOS_APP_ID` | iOS app ID (`1:…:ios:…`) |
| `FIREBASE_TESTER_GROUP` | *(optional)* tester group alias, defaults to `testers` |

Android needs nothing further — release builds are signed with the debug key so
testers can install them (swap in a real keystore before shipping to stores).

### Additional secrets for iOS device builds

iOS installs require Apple code signing. Export an **Apple Distribution** certificate
(.p12) and an **ad-hoc provisioning profile** for `com.eatplease.app.EatPlease`
(including your testers' device UDIDs), then add:

| Secret | Value |
|---|---|
| `APPLE_TEAM_ID` | Apple Developer team ID |
| `APPLE_CERTIFICATE_BASE64` | Distribution certificate .p12, base64-encoded |
| `APPLE_CERTIFICATE_PASSWORD` | Password of the .p12 |
| `APPLE_PROVISIONING_PROFILE_BASE64` | Ad-hoc .mobileprovision, base64-encoded |

Tester links and QR codes for every distributed build appear in the workflow run
summary.
