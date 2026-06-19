---
doc_type: draft
status: draft
created: 2026-06-19
source_request: manual alarm editor picker UX improvement
related:
  - docs/superpowers/specs/2026-04-23-ai-alarm-android-design.md
  - app/src/main/java/com/cory/noter/ui/editor/AlarmEditorScreen.kt
  - app/src/main/java/com/cory/noter/ui/editor/AlarmEditorViewModel.kt
---

# Manual Alarm Picker UX Draft

## Problem

Manual alarm editing currently depends on typed text fields for time, date, and interval count values. This makes common edits more effortful than they need to be:

- Hour and minute require keyboard input.
- One-time alarm date requires typing an ISO date.
- Weekly interval start and end dates require typing dates.
- Weekly interval count requires typing a number.

These fields are valid for storage and validation, but the user-facing manual editor should reduce typing and make valid choices easier to select.

## Goal

Improve the manual alarm editor so common time, repeat-count, and date adjustments can be selected directly:

- Use wheel-style controls for hour, minute, and repeat interval count.
- Use a calendar dialog for date selection.
- Preserve the existing alarm domain model, repeat-rule behavior, scheduling pipeline, validation rules, and AI creation behavior.

## Proposed UX

### Time Selection

Replace the visible `Hour` and `Minute` text fields with wheel-style selectors:

- Hour wheel: values `0` through `23`.
- Minute wheel: values `00` through `59`.
- The current value is highlighted and can be changed by scrolling.
- The selected values continue to populate the same editor state fields used by save validation.

Recommended default: keep 24-hour time first, because the current data model stores `hour` as `0..23` and the existing UI labels already expose hour/minute directly.

We could add support for 12-hour time as well. By default use 24-hour time.

### Repeat Count Selection

For `Interval` repeat rules, replace `Every N weeks` text input with a wheel-style selector:

- Minimum value: `1`.
- Initial/default value: current editor default, currently `1`.
- Practical upper bound should be explicit in the implementation plan; suggested draft bound is `52` weeks.
- The selected value continues to save as `intervalWeeksText` or an equivalent UI state field before domain validation.

### Date Selection

Replace typed date entry with a calendar picker dialog for:

- Once alarm date.
- Interval start date.
- Interval end date.

The editor should show the selected date as readable text or a compact date field, but the user should open a calendar dialog to change it. The stored/validated value remains an ISO local date behind the UI boundary.

Dialog behavior:

- Opening a date field launches a modal calendar picker.
- Confirm writes the selected date to the corresponding editor state.
- Cancel leaves the previous date unchanged.
- Interval end date must still be on or after start date; invalid combinations should fail explicitly through the existing validation/error path.

## Scope

### In Scope

- Manual create and edit alarm screen UX changes.
- Compose UI controls for wheel-style time/count selection.
- Calendar dialog for date fields used by manual repeat rules.
- Focused UI/state tests proving picker interactions update the same saved alarm values.
- Existing validation messages may be refined if needed, but validation semantics should not become looser.

### Out Of Scope

- AI alarm creation prompt, parser, OpenRouter tool schema, or model catalog changes.
- Alarm scheduling behavior changes.
- Room schema or domain model changes.
- New recurrence types.
- Snooze, vibration, notification, or ringtone redesign.
- Calendar integration with device calendars.

## Approach Options

### Option A: Compose-Native Picker Components

Build wheel-like Compose components inside `AlarmEditorScreen.kt` or a small editor UI helper file. Use existing state callbacks to write selected values back to the ViewModel.

Pros:

- Fully controlled by the app.
- Easy to test as normal Compose UI.
- Keeps UI style consistent with the current simple Compose implementation.

Cons:

- Requires implementing the wheel interaction carefully enough to feel polished.
- Accessibility and snap behavior need attention.

### Option B: Platform Picker Dialogs Where Available
[Comment: I want to select this option. Make the wheel look cool and smooth. I trust your design taste.]
Use Material date picker/dialog patterns for dates and simpler native-style controls for numeric values if suitable dependencies already exist.

Pros:

- More familiar date selection behavior.
- Less custom calendar logic.

Cons:

- May require new UI dependencies or API-specific handling.
- Wheel time/count still needs a custom or Compose-specific solution.

### Option C: Hybrid Minimal UX

Use a calendar dialog for dates, but replace time/count text inputs with compact stepper controls instead of wheels.

Pros:

- Lower implementation risk.
- Easier accessibility and testing.

Cons:

- Does not satisfy the requested wheel-style adjustment for time and repeat count.

Recommended direction: Option A for wheel controls plus a Material-style calendar dialog for dates. This best matches the requested interaction while keeping domain and scheduling behavior untouched.

## Acceptance Draft

- Manual create flow allows setting hour and minute without typing into text fields.
- Manual edit flow loads an existing alarm and shows its hour/minute as the selected wheel values.
- Once repeat mode allows choosing the date through a calendar dialog.
- Interval repeat mode allows choosing start and end dates through calendar dialogs.
- Interval repeat mode allows choosing the repeat week count through a wheel-style selector.
- Saving after picker-based edits creates or updates alarms with the same domain values currently produced by typed input.
- Invalid interval date order still blocks save and shows an explicit error.
- Existing AI alarm tests and scheduling tests remain unaffected.

## Test Ideas

- Compose smoke: create a once alarm by selecting time and date through picker UI, then save.
- Compose smoke: edit an existing alarm and confirm the wheels initialize from the alarm values.
- ViewModel or UI-state test: picker callbacks update hour, minute, once date, interval start/end date, and interval week count deterministically.
- Regression test: interval end date before start date still produces the existing explicit validation error.
- Quality gate: run `./gradlew testDebugUnitTest`, `./gradlew lintDebug`, and `./gradlew assembleDebug`; run `./gradlew connectedDebugAndroidTest` when a device or emulator is available.

## Open Questions

- Should the visible time control use 24-hour format only, or should it follow device locale and show AM/PM where appropriate?
Answer: Yes, optional if possible
- What maximum should the interval week wheel allow: `52`, `104`, or another product limit?
Answer: 104
- Should manual text entry remain available as an accessibility fallback, or should picker controls fully replace the text fields?
Answer: No, only picker controls
- Should selecting an interval start date after the current end date automatically move the end date, or should save fail until the user adjusts it manually?
Answer: automatically move the end data as well.
