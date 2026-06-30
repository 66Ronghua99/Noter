# AI Create UI, Theme, And Settings Implementation Plan

## Goal Description

Implement the approved AI Create UI, Theme, And Settings design for Noter.

The app should open on one unified AI alarm creation surface with two equal modes: voice and text. Voice remains the default mode, and users can switch between voice and text in place with one visible control. Existing voice capture, text AI creation, background scheduling, exact-alarm permission, and error/status behavior must be preserved by reusing the current `VoiceHomeViewModel` and `AiCreateViewModel` behavior or a small UI-only adapter boundary.

The visual direction should follow the local static prototype under `artifacts/2026-07-01-ai-create-ui-theme-settings/`, especially:

- `index.html` for unified voice/text creation layout and mode switching.
- `settings.html` for the settings directory page.
- `appearance.html` and `theme.js` for preset/custom theme behavior.
- `ai.html`, `sound.html`, and `permissions.html` for detail-page structure.

The prototype is a visual reference only. The approved spec remains the product contract, and production Android code must keep business behavior in the existing domain, voice, AI, scheduler, and settings boundaries.

## Acceptance Criteria

- AC-1: Unified AI create entry starts in voice mode and supports in-page mode switching.
  - Positive Tests (expected to PASS):
    - Compose smoke test opens the app default route and finds the unified create surface in voice mode.
    - Compose smoke test taps the text segment and finds text prompt submission controls without navigating to a separate text-only route.
    - Compose smoke test switches from text back to voice and finds the voice record control.
  - Negative Tests (expected to FAIL before implementation):
    - Starting the app still lands on a route that only exposes the old `VoiceHomeScreen` fallback text action.
    - Opening AI create from the alarm list still lands on the old text-only `AiCreateScreen`.

- AC-2: Existing voice behavior is preserved inside the unified surface.
  - Positive Tests (expected to PASS):
    - Existing `VoiceHomeViewModelTest` behavior remains green.
    - Existing or migrated Compose tests still verify press, release, cancel, retry, permission recovery, and text fallback behavior.
    - The unified voice mode uses stable test tags for record, notice, error, cancel, retry, permission recovery, list, and settings actions.
  - Negative Tests (expected to FAIL before implementation):
    - Voice mode moves recorder lifecycle, ASR, or cleanup logic into the Compose screen instead of delegating to the existing voice boundary.
    - Voice failure states no longer expose the current retry, permission, or text fallback actions.

- AC-3: Existing text AI creation behavior is preserved inside the unified surface.
  - Positive Tests (expected to PASS):
    - Existing `AiCreateViewModelTest` remains green.
    - Compose smoke test verifies text mode prompt entry and submit state using the current loading/status/error behavior.
    - Exact-alarm permission action remains visible when `AiCreateUiState.exactAlarmPermissionRequired` is true.
    - Background creation still enqueues through `AiCreateBackgroundScheduler` when available.
  - Negative Tests (expected to FAIL before implementation):
    - Text mode bypasses `AiCreateViewModel.submit()`.
    - Missing API key, missing model, permission, network, clarification, and create failures are silently hidden or replaced by generic success UI.

- AC-4: Theme settings are persisted and applied centrally.
  - Positive Tests (expected to PASS):
    - Unit tests cover default theme settings.
    - Unit tests cover valid preset theme writes.
    - Unit tests cover valid custom seed color writes.
    - Unit tests cover unknown preset write failure.
    - Unit tests cover invalid custom seed color write failure.
    - Unit tests cover compatible default behavior when an older app install has no theme keys or an unreadable legacy theme value.
    - A theme-layer test or focused Compose test proves `NoterTheme` builds Material3 color schemes from persisted settings.
  - Negative Tests (expected to FAIL before implementation):
    - Unknown theme preset writes succeed silently.
    - Invalid custom seed color writes succeed silently.
    - Screen-level code hard-codes page colors instead of consuming `MaterialTheme` tokens.

- AC-5: Settings becomes a directory plus route-specific detail pages.
  - Positive Tests (expected to PASS):
    - Settings ViewModel tests cover directory summaries for Appearance, AI and voice, Sound, and Permissions.
    - Compose smoke tests navigate from settings home to Appearance, AI and voice, Sound, and Permissions detail pages.
    - Appearance page exposes preset choices and custom seed color controls.
    - AI and voice page preserves current OpenRouter API key, LLM model, and ASR model actions.
    - Sound page preserves current default ringtone action.
    - Permissions page preserves current notification, exact alarm, and battery optimization guidance/actions.
  - Negative Tests (expected to FAIL before implementation):
    - Settings remains one long flat form.
    - Existing settings actions are removed or only represented as inert display rows.

- AC-6: Visual implementation follows the approved design direction without expanding scope.
  - Positive Tests (expected to PASS):
    - Unified create surface uses restrained Material3 components, stable layout, and lightweight 180-250 ms mode transition.
    - Main actions use filled or tonal button treatments according to importance.
    - Repeated settings rows use simple row/card treatments with modest radius and spacing.
    - User-visible strings are present in English and Chinese resources.
  - Negative Tests (expected to FAIL before implementation):
    - UI introduces heavy gradients, decorative backgrounds, waveform rendering, 3D flips, particles, or continuous expensive animation.
    - UI adds unrelated dashboards, analytics, new model providers, or custom OpenRouter model entry.

- AC-7: Final verification evidence is fresh and recorded.
  - Positive Tests (expected to PASS):
    - Evidence file under `artifacts/2026-07-01-ai-create-ui-theme-settings/` records all final verification commands and outcomes.
    - `./gradlew testDebugUnitTest` passes.
    - `./gradlew lintDebug` passes.
    - `./gradlew assembleDebug` passes.
    - `./gradlew assembleDebugAndroidTest` passes when Compose smoke tests are changed or added.
  - Negative Tests (expected to FAIL before implementation):
    - Completion is claimed without a fresh verification evidence file.
    - Any required local gate fails without a documented blocker and next action.

## Path Boundaries

### Upper Bound (Maximum Scope)

- Introduce a unified create route/screen that composes voice and text modes in one surface.
- Add a small UI adapter only if needed to pass existing `VoiceHomeUiState` and `AiCreateUiState` into the unified screen without moving business logic.
- Add a dedicated UI theme package such as `app/src/main/java/com/cory/noter/ui/theme/`.
- Extend `AppSettings`, `SettingsRepository`, and `DataStoreSettingsRepository` with theme preset/custom seed settings.
- Split settings UI into home and detail screens with route-specific composables and navigation routes.
- Update tests and localized strings for the new UI, settings summaries, theme settings, and navigation.
- Refresh Material3 styling across touched create/settings surfaces, and opportunistically let existing alarm list/editor/ringing surfaces inherit the central theme.

### Lower Bound (Minimum Scope)

- One unified AI create screen with voice default, text mode switch, and preserved existing voice/text ViewModel behavior.
- Central persisted theme setting with preset and custom seed support.
- Settings home plus Appearance, AI and voice, Sound, and Permissions detail pages.
- Focused unit and Compose smoke tests that cover the acceptance criteria above.
- Fresh local verification evidence.

### Allowed Choices

- Can use:
  - Jetpack Compose Material3 components and tokens.
  - Existing `VoiceHomeViewModel`, `AiCreateViewModel`, and `SettingsViewModel`.
  - Existing `SettingsRepository` / `DataStoreSettingsRepository`.
  - A small `UnifiedAiCreateScreen` / `AiCreateHomeScreen` composable and UI state adapter.
  - A dedicated `NoterTheme` wrapper around Material3 `MaterialTheme`.
  - AndroidX navigation routes for settings detail pages.
  - The HTML prototype as visual reference only.
- Cannot use:
  - New alarm-agent behavior, new agent tools, or changes to tool-call requirements.
  - New ASR provider or model-provider functionality.
  - Business logic inside Compose screens for recorder, ASR, AI creation, scheduling, or DataStore persistence.
  - Silent fallback for invalid writes.
  - Heavy animation or decorative visual systems outside the spec.
  - Production dependency on the HTML/CSS/JS prototype.

## Dependencies and Sequence

### Milestones

1. Milestone 1: Theme domain and persistence
   - Phase A: Add theme model types, preset ids, custom seed validation, and default values.
   - Phase B: Extend `AppSettings`, `SettingsRepository`, `DataStoreSettingsRepository`, and fakes.
   - Phase C: Add failing tests first for valid preset/custom writes, invalid writes, and old-value compatibility; then implement.

2. Milestone 2: Central Material3 theme layer
   - Phase A: Add `ui/theme/NoterTheme` and palette construction from settings.
   - Phase B: Wrap `MainActivity` content with `NoterTheme` fed by settings flow.
   - Phase C: Replace touched hard-coded colors with `MaterialTheme.colorScheme` and typography tokens.

3. Milestone 3: Unified AI create UI
   - Phase A: Add unified create state/mode tags and composable shell based on the prototype.
   - Phase B: Embed or adapt existing voice mode behavior without moving voice capture logic.
   - Phase C: Embed or adapt existing text mode behavior without moving AI creation logic.
   - Phase D: Update navigation so app start and alarm-list AI create entry both land on the unified surface.

4. Milestone 4: Settings hierarchy
   - Phase A: Refactor settings routes into home, appearance, AI and voice, sound, and permissions destinations.
   - Phase B: Preserve current API key, model, ASR, ringtone, and permission actions in detail screens.
   - Phase C: Add summary state for each settings home entry.
   - Phase D: Add appearance preset/custom color controls.

5. Milestone 5: Localization and visual polish
   - Phase A: Add English and Chinese strings for new routes, controls, summaries, and status labels.
   - Phase B: Tune spacing, touch targets, shape, and stable loading/error layouts against the prototype.
   - Phase C: Keep mode transition lightweight and layout-stable.

6. Milestone 6: Verification and evidence
   - Phase A: Run focused unit tests after each behavioral milestone.
   - Phase B: Run final local gates.
   - Phase C: Record commands and results in `artifacts/2026-07-01-ai-create-ui-theme-settings/verification.md`.
   - Phase D: Update `PROGRESS.md`, `MEMORY.md` if a stable lesson was learned, and `NEXT_STEP.md`.

## Implementation Notes

- Code should not contain plan terminology such as `AC-1` or milestone names.
- Prefer explicit failures for invalid theme writes.
- The only compatible fallback in this scope is read-side handling for older persisted theme values; it must be visible in code and covered by tests.
- Do not change agent-loop, ASR, scheduling, or alarm validation behavior unless a test proves the existing behavior was already broken by the UI integration.
- Keep test tags stable and named for user-observable controls rather than implementation classes.
- If route naming changes, keep it internal and update tests in the same milestone.

## Checklist

- [x] Theme model, validation, repository contract, DataStore implementation, and fakes are updated test-first.
- [x] `NoterTheme` centralizes theme construction and wraps app content.
- [x] Unified AI create screen starts in voice mode and switches between voice/text in-page.
- [x] Existing voice capture behavior is preserved through the current ViewModel boundary.
- [x] Existing text AI creation behavior is preserved through the current ViewModel boundary.
- [x] Alarm list AI create action and default start destination land on the unified create surface.
- [x] Settings home and Appearance, AI and voice, Sound, Permissions detail routes are implemented.
- [x] Appearance supports preset and custom seed color settings.
- [x] User-visible strings are localized in English and Chinese resources.
- [x] Unit tests and Compose smoke tests cover the acceptance criteria.
- [x] Final verification evidence is recorded under `artifacts/2026-07-01-ai-create-ui-theme-settings/verification.md`.
- [x] `PROGRESS.md`, `MEMORY.md`, and `NEXT_STEP.md` are synchronized after implementation.
