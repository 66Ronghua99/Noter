# Noter Collaboration Guide

## Repository Entry

Before non-trivial code work, read:

- `NEXT_STEP.md`
- `MEMORY.md`

Read `PROGRESS.md` and the active spec or plan when status, scope, or completion state is unclear.

## Project Shape

Noter is an Android alarm app with voice-first and text AI alarm creation. Keep UI work inside Compose/view-model boundaries, AI alarm behavior inside the existing agent/background scheduler boundary, voice capture inside the voice boundary, and settings persistence in the settings repository layer.

## Verification

Use the strongest relevant Android gate before claiming completion:

- `./gradlew testDebugUnitTest`
- `./gradlew lintDebug`
- `./gradlew assembleDebug`
- `./gradlew assembleDebugAndroidTest` when Compose smoke tests are changed or added
- `./gradlew connectedDebugAndroidTest` when a device or emulator is available and end-to-end behavior needs confirmation

Record verification evidence under `artifacts/`.
