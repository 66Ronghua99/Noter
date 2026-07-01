# Memory

## Stable Notes

- AI alarm UI work should preserve the existing agent loop, WorkManager/background creation path, explicit tool contracts, and voice capture ownership.
- Alarm management is now part of the AI alarm agent surface: `list_alarms`, `pause_alarm`, and `resume_alarm` must stay routed through `AlarmManagementUseCase`; direct list-only requests must display alarm details, list-before-pause/resume cannot be accepted as success without a management or rejection tool, and `create_alarm`, `reject_unclear_request`, `end_task`, and required-tool policy remain intact.
- CJK alarm-agent list terms such as `列出`, `显示`, and `有哪些` need substring matching rather than word-boundary regex; English management verbs should remain word/phrase matched so `paused` and `disabled` are still read-only list filters.
- Paused-next repeating consumption must advance from the later of the skipped anchor and current clock time, otherwise long downtime can schedule a stale trigger and leave the alarm paused.
- Generic alarm repository updates are an editor compatibility boundary: when a generic save enables an alarm, normalize pause state to `NONE`; when it disables an alarm, normalize to `INDEFINITE`. Use `updateFromManagement` for intentional pause-next anchor preservation.
- Settings persistence belongs in `SettingsRepository` and `DataStoreSettingsRepository`; UI screens should not own persistence details directly.
- Theme work should be centralized in the UI theme layer and consumed through Material3 theme tokens.
- App-root theming should consume the theme-only settings flow so launch is not coupled to unrelated AI/model setting validation; keep full settings validation explicit for feature flows.
- Custom generated `onPrimaryContainer`/`onSecondaryContainer` colors must stay dark enough for readable text on light containers; verify contrast after palette changes.
