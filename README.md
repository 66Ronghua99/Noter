# Noter

Noter is a voice-first Android alarm app built with Kotlin and Jetpack Compose. It supports local alarm management, text and voice-assisted creation, exact alarm scheduling, English/Chinese UI, and background status notifications.

Chinese documentation: [README-zh.md](README-zh.md)

## Current Status

Noter keeps the agent loop, alarm/calendar tools, scheduling, permissions, and all local writes on Android. AI services are reached through the repository-owned first-party API:

- Text creation sends the local agent messages, tool schemas, and tool results to `POST /api/v1/agent/completions`.
- Voice creation records temporary m4a audio and sends it to `POST /api/v1/asr/transcriptions`.
- The server selects upstream endpoints, credentials, models, limits, and timeouts. The app does not expose provider, API-key, or model settings.
- Settings retains appearance, sound, calendar, and device-permission recovery controls.
- Foreground requests and background work use bounded, owner-specific retry policies; Android local tool results remain authoritative after a committed write.

## Tech Stack

- Kotlin 2.2
- Android Gradle Plugin 8.13
- Jetpack Compose Material 3
- Room, DataStore, and WorkManager
- Kotlin serialization and OkHttp
- TypeScript, Fastify, and Node.js 20 for `server/noter-api`
- Docker Compose and Caddy for production ingress
- JUnit, Robolectric, AndroidX Test, Compose UI tests, and Vitest

## Requirements

- JDK 17
- Android SDK with API 35 installed
- Android Studio or Gradle CLI
- Node.js 20 and npm for backend work
- Docker and Docker Compose for container and smoke checks

The app targets Android SDK 35 and supports Android 8.0+ (`minSdk 26`).

## Getting Started

Build the debug APK:

```sh
./gradlew assembleDebug
```

Install it on a connected device:

```sh
./gradlew installDebug
```

Run Android checks:

```sh
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebugAndroidTest
```

Run connected UI tests when a device or emulator is available:

```sh
./gradlew connectedDebugAndroidTest
```

## First-Party API Development

Android build inputs are supplied by the build environment or an untracked `local.properties` file. They are not entered in the app:

```properties
NOTER_API_BASE_URL=https://api.example.com
NOTER_CLIENT_TOKEN=the-build-time-access-token
```

The release workflow reads `NOTER_API_BASE_URL` from a repository variable and `NOTER_CLIENT_TOKEN` from a repository secret. A release build fails early when either input is missing.

The backend is an independent package:

```sh
cd server/noter-api
npm ci
npm run format:check
npm run lint
npm run typecheck
npm test
```

Server configuration is loaded from the deployment environment. Upstream keys and model names belong in the private production `/opt/noter/.env`; they are never copied into the Android app or committed to the repository.

## Local Container Smoke Check

The packaged smoke stack uses a fake compatible upstream and does not require paid credentials:

```sh
./deploy/production/tests/fake-upstream-smoke.sh
```

The production Compose file publishes only Caddy on ports 80 and 443. The API container has no host-published port. `deploy/production/deploy.sh` accepts only an immutable GHCR image reference and restores the previous image/configuration after failed readiness.

## Permissions

For the full alarm experience, grant:

- Microphone: required for voice recording.
- Notifications: required for alarm and AI status notifications on Android 13+.
- Exact alarms: required for reliable minute-accurate alarms.
- Battery optimization exemption: recommended for background reliability.
- Calendar access: required only when calendar sync is requested.

The Settings screen exposes recovery actions for device permissions that need attention.

## Project Layout

```text
app/src/main/java/com/cory/noter/
  agent/          Android-owned tool-calling loop and protocol
  agent/tools/    Alarm, management, terminal, and clarification tools
  alarm/          Scheduling, receivers, reconciliation, and ringing service
  data/           Room and DataStore persistence
  di/             Application container wiring
  domain/         Alarm, settings, and AI domain models
  notifications/  Creation and ringing notifications
  ui/             Compose screens and ViewModels
  voice/          m4a capture, first-party ASR, and cleanup boundaries

server/noter-api/
  src/            Fastify routes, validated config, services, and upstream adapters
  test/           Contract, limits, privacy, provider, and error tests

deploy/production/
  compose.yml     Private API network and public Caddy ingress
  Caddyfile       Direct-DNS HTTPS reverse proxy
  deploy.sh       Immutable-image deployment and rollback
  tests/          Fake-upstream smoke and deployment rollback tests
```

## Verification Gate

The strongest relevant local gate is:

```sh
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
./gradlew assembleDebugAndroidTest
```

For backend and deployment changes, also run the package gates, Compose validation, deployment-script tests, and fake-upstream smoke. Fresh evidence is recorded under `artifacts/` when the implementation handoff is completed.

## Release Inputs

The Android release workflow requires:

- Repository variable `NOTER_API_BASE_URL`
- Repository secret `NOTER_CLIENT_TOKEN`

Optional signing secrets are:

- `ANDROID_SIGNING_KEYSTORE_BASE64`
- `ANDROID_SIGNING_KEY_ALIAS`
- `ANDROID_SIGNING_STORE_PASSWORD`
- `ANDROID_SIGNING_KEY_PASSWORD`

The backend workflow publishes `ghcr.io/<owner>/noter-api:<full-commit-sha>`. SSH deployment remains disabled until the production server values, known-host entry, and credentials are provisioned and `PRODUCTION_DEPLOY_ENABLED` is explicitly set to `true`.

## Development Docs

- [Repository guide](AGENTS.md)
- [Architecture overview](docs/architecture/overview.md)
- [Layer notes](docs/architecture/layers.md)
- [Testing strategy](docs/testing/strategy.md)
- [Progress log](PROGRESS.md)
- [Next step pointer](NEXT_STEP.md)
- [Memory notes](MEMORY.md)
