# Memory

## Stable Notes

- AI alarm UI work should preserve the existing agent loop, WorkManager/background creation path, explicit tool contracts, and voice capture ownership.
- Alarm management is now part of the AI alarm agent surface: `list_alarms`, `pause_alarm`, and `resume_alarm` must stay routed through `AlarmManagementUseCase`, direct list-only requests must be user-visible successes, and `create_alarm`, `reject_unclear_request`, `end_task`, and required-tool policy remain intact.
- Settings persistence belongs in `SettingsRepository` and `DataStoreSettingsRepository`; UI screens should not own persistence details directly.
- Theme work should be centralized in the UI theme layer and consumed through Material3 theme tokens.
- App-root theming should consume the theme-only settings flow so launch is not coupled to unrelated AI/model setting validation; keep full settings validation explicit for feature flows.
- Custom generated `onPrimaryContainer`/`onSecondaryContainer` colors must stay dark enough for readable text on light containers; verify contrast after palette changes.
