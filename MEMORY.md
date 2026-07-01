# Memory

## Stable Notes

- AI alarm UI work should preserve the existing agent loop, WorkManager/background creation path, explicit tool contracts, and voice capture ownership.
- Agent loop tool budgets should distinguish read tools from write tools: list-before-manage needs read plus one write, but duplicate WRITE/DESTRUCTIVE/BATCH tools in one run must be blocked before a second write can commit.
- Alarm management is now part of the AI alarm agent surface: `list_alarms`, `pause_alarm`, and `resume_alarm` must stay routed through `AlarmManagementUseCase`; direct list-only requests must display alarm details, list-before-pause/resume cannot be accepted as success without a management or rejection tool, and `create_alarm`, `reject_unclear_request`, `end_task`, and required-tool policy remain intact.
- CJK alarm-agent list terms such as `列出`, `显示`, and `有哪些` need substring matching rather than word-boundary regex; English management verbs should remain word/phrase matched so `paused` and `disabled` are still read-only list filters.
- Paused-next repeating consumption must advance from the later of the skipped anchor and current clock time, otherwise long downtime can schedule a stale trigger and leave the alarm paused.
- Finite repeating paused-next consumption with no future trigger must clear the `NEXT_OCCURRENCE` checkpoint into a terminal disabled/`INDEFINITE` state through `updateFromManagement`, and startup reconciliation should report that as a consumed final checkpoint rather than a failure.
- CJK read-only alarm list filters such as `显示暂停的闹钟` need adjective/state phrase handling so they do not get mistaken for actual pause/resume commands; keep `暂停闹钟` protected as management.
- Direct alarm-agent list filters such as paused/disabled must affect the final user-visible `AlarmsListed` result; do not accept a filtered request as success while displaying the raw unfiltered `list_alarms` output.
- Direct read-only `listed_alarms` results are locally complete once produced; if the required finalization turn fails afterward, preserve the list result for direct list requests while keeping list-before-management safeguards intact.
- Generic alarm repository updates are an editor compatibility boundary: when a generic save enables an alarm, normalize pause state to `NONE`; when it disables an alarm, normalize to `INDEFINITE`. Use `updateFromManagement` for intentional pause-next anchor preservation.
- Settings persistence belongs in `SettingsRepository` and `DataStoreSettingsRepository`; UI screens should not own persistence details directly.
- Theme work should be centralized in the UI theme layer and consumed through Material3 theme tokens.
- App-root theming should consume the theme-only settings flow so launch is not coupled to unrelated AI/model setting validation; keep full settings validation explicit for feature flows.
- Custom generated `onPrimaryContainer`/`onSecondaryContainer` colors must stay dark enough for readable text on light containers; verify contrast after palette changes.
