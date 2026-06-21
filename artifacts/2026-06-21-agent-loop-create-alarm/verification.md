# Agent Loop Create Alarm Verification

## Commands

- Final-review focused regression: `./gradlew --no-daemon testDebugUnitTest --tests com.cory.noter.agent.AgentLoopRunnerTest --tests com.cory.noter.ai.OpenRouterAgentClientTest --tests com.cory.noter.ai.AiCreateBackgroundSchedulerTest`
- Focused regression: `./gradlew --no-daemon testDebugUnitTest --tests com.cory.noter.agent.AgentLoopRunnerTest --tests com.cory.noter.ai.OpenRouterAgentClientTest --tests com.cory.noter.agent.tools.alarm.CreateAlarmArgumentsParserTest --tests com.cory.noter.agent.tools.alarm.CreateAlarmToolTest --tests com.cory.noter.ai.AiAlarmCreatorTest --tests com.cory.noter.ai.AiCreateBackgroundSchedulerTest --tests com.cory.noter.ui.ai.AiCreateViewModelTest --tests com.cory.noter.di.AppContainerTest`
- Legacy cleanup scan: `rg -n "submit_alarm_draft|OpenRouterGateway|OpenRouterResult|AiAlarmResponseParser" app/src/main/java app/src/test/java`
- Full unit gate: `./gradlew --no-daemon testDebugUnitTest`
- Lint gate: `./gradlew --no-daemon lintDebug`
- Assemble gate: `./gradlew --no-daemon assembleDebug`
- Device check: `adb devices` with `ANDROID_HOME=/home/ronghua/.cache/android-sdk` and `platform-tools` added to `PATH`

## Results

- Final-review focused regression suite passed after the TDD red/green loop for the new reviewer findings. See `artifacts/2026-06-21-agent-loop-create-alarm/final-review-focused-regression.log`.
- Focused regression suite passed. See `artifacts/2026-06-21-agent-loop-create-alarm/focused-regression.log`.
- Legacy cleanup scan returned test-only references to the removed names and negative assertions around `submit_alarm_draft`; it returned no production (`app/src/main/java`) matches. See `artifacts/2026-06-21-agent-loop-create-alarm/legacy-cleanup-scan.log`.
- `testDebugUnitTest` passed. See `artifacts/2026-06-21-agent-loop-create-alarm/testDebugUnitTest.log`.
- `lintDebug` passed and wrote the HTML lint report at `app/build/reports/lint-results-debug.html`. See `artifacts/2026-06-21-agent-loop-create-alarm/lintDebug.log`.
- `assembleDebug` passed. See `artifacts/2026-06-21-agent-loop-create-alarm/assembleDebug.log`.
- `adb devices` returned no attached devices. See `artifacts/2026-06-21-agent-loop-create-alarm/adb-devices.log`.
- `connectedDebugAndroidTest` was not run because `adb devices` listed no emulator or hardware device.

## Contract Checks

- Final-review fixes verified:
  - `AgentLoopRunner` forces `create_alarm` only on the first model turn, then keeps `tools` with `AgentToolChoice.Auto` for the follow-up turn after the tool result is appended.
  - `OpenRouterAgentClient` sends `parallel_tool_calls: false` and omits `tool_choice` on Auto follow-up requests while still including the `tools` array.
  - `AiCreateWorker` now maps transient provider and transport failures to WorkManager retry, permanent config/model/tool failures to failure, and committed product outcomes to success after notifier delivery.
- Model-facing tool name is `create_alarm`, verified by the focused regression suite and the request-body assertions in `OpenRouterAgentClientTest`.
- `submit_alarm_draft` is absent from production OpenRouter request construction; the legacy cleanup scan found no production matches after deleting the old single-purpose client/parser path.
- Agent loop defaults remain `maxModelTurns = 2` and `maxToolExecutions = 1` in `app/src/main/java/com/cory/noter/agent/AgentProtocol.kt`, and `AgentLoopRunnerTest` still passes under those defaults.
- Background AI creation uses WorkManager through `WorkManagerAiCreateBackgroundScheduler`, verified by `AiCreateBackgroundSchedulerTest` and `AppContainerTest`.
