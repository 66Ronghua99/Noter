# AI Alarm Tool-Call And Weekly-Interval Merge Draft

## Context

The local branch `master` is at `92f3099` and is behind `origin/master` by one known local tracking commit, `ce0a22f` (`Use OpenRouter tool calls for AI alarms`, 2026-06-18 17:34:25 +0800). A fresh `git fetch origin --prune` could not run in the current sandbox because `.git/FETCH_HEAD` is not writable, so this analysis is based on the existing local `origin/master` reference.

The local working tree has uncommitted weekly-interval recurrence changes. The user goal is to merge the remote OpenRouter tool-call stability work with the local recurrence work so AI alarm creation becomes more stable without losing weekly interval support.

## Remote Change Summary

Remote commit `ce0a22f` changes AI creation from prompt-only JSON output to a forced OpenRouter tool call:

- `OpenRouterClient` sends a `submit_alarm_draft` function tool schema and `tool_choice`.
- Successful responses now parse `tool_calls[].function.arguments`.
- Missing tool calls return `InvalidResponse`.
- `AiAlarmPromptBuilder` removes the strict "Return only JSON" contract and asks the model to call `submit_alarm_draft`.
- `OpenRouterModel` defaults to `deepseek/deepseek-v4-flash`, keeps `deepseek/deepseek-v3.2`, and removes free model options.
- AI, OpenRouter client, and settings tests are updated.
- Spec, plan, progress, memory, and verification artifacts document the tool-call change.

## Local Change Summary

The uncommitted local changes add weekly interval recurrence:

- `RepeatRule.WeeklyInterval(startDate, endDate, intervalWeeks, days)`.
- Room schema version 2 columns for `startDate`, `endDate`, and `intervalWeeks`, plus migration wiring.
- `RepeatRuleCodec`, repository mapping, validation, next trigger calculation, alarm list labels, editor UI/ViewModel state, ringing, and startup reconciliation understand weekly intervals.
- `AiAlarmResponseParser` accepts `repeatRule.type == "weekly_interval"` with `startDate`, optional/defaulted `endDate`, `intervalWeeks`, and non-empty weekdays.
- `AiAlarmPromptBuilder` currently documents weekly interval fields inside prompt-only JSON instructions.
- Unit and smoke tests cover parser, codec, calculator, editor ViewModel, and prompt additions.

## Overlap And Risk

The direct overlap is small but important:

- `AiAlarmPromptBuilder.kt` conflicts semantically: remote removes JSON-only instructions, while local adds weekly interval fields to those JSON instructions.
- `AiAlarmPromptBuilderTest.kt` conflicts semantically: remote expects tool-call instruction and paid model catalog; local expects weekly interval wording and JSON-only instruction.
- `MEMORY.md` and `PROGRESS.md` both append different stable notes/progress bullets.

The indirect integration risk is higher:

- Remote `OpenRouterClient.alarmDraftParameters()` only allows `once`, `daily`, `weekdays`, and `custom_weekdays`. If merged as-is, the model cannot legally call the tool with `weekly_interval`.
- The tool schema also omits `repeatRule.startDate`, `repeatRule.endDate`, and `repeatRule.intervalWeeks`, while the local parser requires them for weekly intervals.
- Parser strictness requires only known keys; tool schema and parser must stay in lockstep.
- Settings/model changes must be preserved so the app uses tool-capable paid models, not free models.

## Merge Goal

Preserve the remote stability improvement as the AI output boundary: OpenRouter must force `submit_alarm_draft`, parse tool arguments, and reject missing tool calls. Preserve the local recurrence capability by extending the tool schema, prompt, tests, docs, and evidence so weekly interval alarms can be produced through the tool-call path.

## Suggested Plan Path

`docs/superpowers/plans/2026-06-19-ai-alarm-tool-call-weekly-interval-merge.md`
