# Manual Picker Fixed Frame Snap Verification

## Scope

Implement fixed center selection frames, snap-to-center wheel settlement, and optional haptic feedback for the manual alarm editor wheels.

## Evidence

- RED: `./gradlew testDebugUnitTest --tests com.cory.noter.ui.editor.NumberWheelPickerTagTest` failed before implementation because `wheelSelectionFrameTag` did not exist.
- GREEN: `./gradlew testDebugUnitTest --tests com.cory.noter.ui.editor.NumberWheelPickerTagTest` passed after adding the shared selection-frame tag helper.
- Android test compile: `./gradlew assembleDebugAndroidTest` passed after adding fixed-frame smoke assertions.
- Emulator smoke: `ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.cory.noter.AlarmEditorSmokeTest` initially failed because `performScrollToNode` was unstable with dynamic selected row tags after snap behavior.
- Emulator smoke fix: `AlarmEditorSmokeTest` now uses `performScrollToIndex` for wheel positioning and still asserts selected tags plus saved values.
- Final emulator smoke: `ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.cory.noter.AlarmEditorSmokeTest` passed on `Medium_Phone_API_36.0(AVD) - 16`, finishing 3 tests.
- Local gate: `./gradlew testDebugUnitTest lintDebug assembleDebug` passed.
- Whitespace gate: `git diff --check` passed.
- Refactor review: pass. The diff stays inside the editor wheel composable, focused smoke tests, and repository evidence/docs; no picker state moved into ViewModel/domain and no new dependency was added.

## Connected Phone

User completed connected-phone manual visual inspection after the emulator pass and reported no selector issues.
