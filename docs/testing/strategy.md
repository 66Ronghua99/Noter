# Testing Strategy

## Goal

Protect both behavior and architecture.

## Required Layers

1. Structural checks for architecture and imports
2. Behavior tests written test-first
3. Integration tests for providers and boundaries
4. End-to-end tests for critical user journeys

## Evidence Rule

Before claiming completion, record the commands run and the evidence produced.

## Android MVP Gate

Required for Android MVP delivery:

- `./gradlew testDebugUnitTest`
- `./gradlew lintDebug`
- `./gradlew assembleDebug`
- `./gradlew connectedDebugAndroidTest` when a device or emulator is available
