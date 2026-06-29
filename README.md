# Noter

Noter is a voice-first Android alarm app built with Kotlin and Jetpack Compose. It supports manual alarm management, AI-assisted alarm creation through OpenRouter, exact alarm scheduling, localized English/Chinese UI, and background status notifications.

Chinese documentation: [README-zh.md](README-zh.md)

## Current Status

Noter is an Android MVP under active development. The main implemented flows are:

- Hold-to-speak voice alarm creation, with Android speech recognition first and OpenRouter ASR fallback.
- Text AI alarm creation through a tool-based OpenRouter agent loop.
- Manual create, edit, delete, and list flows for alarms.
- One-time, daily, weekday, custom weekday, and weekly-interval recurrence rules.
- Exact alarm scheduling, boot/startup reconciliation, ringing service, and stop notification.
- Settings for OpenRouter API key, LLM model, ASR model, default ringtone, and Android permissions.
- English and Simplified Chinese string resources.

## Tech Stack

- Kotlin 2.2
- Android Gradle Plugin 8.13
- Jetpack Compose Material 3
- Room
- DataStore
- WorkManager
- Kotlin serialization
- OkHttp
- JUnit, Robolectric, AndroidX Test, Compose UI tests

## Requirements

- JDK 17
- Android SDK with API 35 installed
- Android Studio or Gradle CLI
- An OpenRouter API key for AI and ASR features

The app targets Android SDK 35 and supports Android 8.0+ (`minSdk 26`).

## Getting Started

Clone the repository and build the debug APK:

```sh
./gradlew assembleDebug
```

Install on a connected device:

```sh
./gradlew installDebug
```

Run unit tests:

```sh
./gradlew testDebugUnitTest
```

Run lint:

```sh
./gradlew lintDebug
```

Run connected UI tests when a device or emulator is available:

```sh
./gradlew connectedDebugAndroidTest
```

## Using AI Features

AI creation and OpenRouter ASR require an OpenRouter API key.

1. Open Noter.
2. Go to Settings.
3. Enter and save the OpenRouter API key.
4. Select the LLM model and speech recognition model.
5. Use the voice home screen or the text AI create screen.

The default voice path tries Android system speech recognition first. If that is unavailable or fails, Noter records temporary audio and sends it to the configured OpenRouter ASR model.

## Permissions

For the full alarm experience, grant:

- Microphone: required for voice alarm recording.
- Notifications: required for alarm and AI creation status notifications on Android 13+.
- Exact alarms: required for reliable minute-accurate alarms.
- Battery optimization exemption: recommended for background reliability.

The Settings screen exposes recovery actions for permissions that need attention.

## Project Layout

```text
app/src/main/java/com/cory/noter/
  agent/          Provider-neutral tool-calling agent runtime
  agent/tools/    Alarm, terminal, and clarification tools
  alarm/          Android alarm scheduling, receivers, and ringing service
  data/           Room and DataStore persistence
  di/             Application container wiring
  domain/         Alarm, settings, and AI domain models
  notifications/  AI creation and ringing notifications
  ui/             Compose screens and ViewModels
  voice/          Voice capture, STT, ASR fallback, and cleanup boundaries

docs/
  architecture/   Architecture notes
  project/        Project context
  testing/        Test strategy
  superpowers/    Specs, plans, and templates

artifacts/        Verification logs and manual test evidence
```

## Verification Gate

The repository test strategy currently uses this local gate:

```sh
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
```

When a device or emulator is available, also run:

```sh
./gradlew connectedDebugAndroidTest
```

Fresh verification evidence is recorded under `artifacts/`.

## Release Notes

The GitHub release workflow can build unsigned release APKs by default. To produce signed release APKs, configure these repository secrets:

- `ANDROID_SIGNING_KEYSTORE_BASE64`
- `ANDROID_SIGNING_KEY_ALIAS`
- `ANDROID_SIGNING_STORE_PASSWORD`
- `ANDROID_SIGNING_KEY_PASSWORD`

## Development Docs

- [Repository guide](AGENTS.md)
- [Architecture overview](docs/architecture/overview.md)
- [Layer notes](docs/architecture/layers.md)
- [Testing strategy](docs/testing/strategy.md)
- [Progress log](PROGRESS.md)
- [Next step pointer](NEXT_STEP.md)
- [Memory notes](MEMORY.md)
