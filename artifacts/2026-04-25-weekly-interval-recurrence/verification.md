# Weekly Interval Recurrence Verification

## Scope

Add weekly interval recurrence, including start date, end date, interval weeks, selected weekdays, and AI default end date of one year after the start date when omitted.

## Evidence

- `./gradlew testDebugUnitTest --tests com.cory.noter.domain.alarm.NextTriggerCalculatorTest --tests com.cory.noter.data.alarm.RepeatRuleCodecTest --tests com.cory.noter.ai.AiAlarmResponseParserTest`
  - Result: passed
- `./gradlew testDebugUnitTest --tests com.cory.noter.ui.editor.AlarmEditorViewModelTest --tests com.cory.noter.ai.AiAlarmPromptBuilderTest --tests com.cory.noter.domain.alarm.NextTriggerCalculatorTest --tests com.cory.noter.data.alarm.RepeatRuleCodecTest --tests com.cory.noter.ai.AiAlarmResponseParserTest`
  - Result: passed
- `./gradlew testDebugUnitTest lintDebug assembleDebug`
  - Result: passed
- `git diff --check`
  - Result: passed

## Notes

- Room schema version is now 2 with a 1->2 migration adding `startDate`, `endDate`, and `intervalWeeks`.
- `weekly_interval` repeats are treated as repeating alarms during ringing stop and startup reconciliation.
