# Voice-First AI Alarm Verification

## Task 1: Agent Loop RequiredAnyTool And Tool Risk Metadata

- BitLesson selection: `.humanize/bitlesson.md` has no actual lessons. The selector returned its placeholder format, so Task 1 proceeded with `LESSON_IDS: NONE`.
- Environment note: Gradle must run with `JAVA_HOME=/home/ronghua/.cache/codex-jdks/jdk-17` and `ANDROID_HOME=/home/ronghua/.cache/android-sdk`. A first unscoped attempt with the system Java 11 failed before compiling tests and was replaced by the red run below.
- Red evidence: `artifacts/2026-06-24-voice-first-ai-alarm/task1-red-required-any-tool.log`
  - Command: `JAVA_HOME=/home/ronghua/.cache/codex-jdks/jdk-17 ANDROID_HOME=/home/ronghua/.cache/android-sdk ./gradlew --no-daemon testDebugUnitTest --tests com.cory.noter.agent.AgentLoopRunnerTest --tests com.cory.noter.ai.OpenRouterAgentClientTest`
  - Result: expected compile failure because `AgentToolChoice.RequiredAnyTool`, `AgentToolRisk`, and `AgentToolSpec.risk` were not implemented yet.
- Green evidence: `artifacts/2026-06-24-voice-first-ai-alarm/task1-green-focused-tests.log`
  - Command: `JAVA_HOME=/home/ronghua/.cache/codex-jdks/jdk-17 ANDROID_HOME=/home/ronghua/.cache/android-sdk ./gradlew --no-daemon testDebugUnitTest --tests com.cory.noter.agent.AgentLoopRunnerTest --tests com.cory.noter.ai.OpenRouterAgentClientTest`
  - Result: passed.
- Focused command evidence:
  - `artifacts/2026-06-24-voice-first-ai-alarm/task1-green-agent-loop-runner.log`
  - `artifacts/2026-06-24-voice-first-ai-alarm/task1-green-openrouter-agent-client.log`
  - Both listed Task 1 focused commands passed.
- Diff check:
  - Command: `git diff --check`
  - Result: passed with no output.
- Bounded architecture/refactor review:
  - Result: pass; no boundary drift found.
  - Notes: `AgentToolChoice.RequiredAnyTool` and `AgentToolRisk` live in provider-neutral `AgentProtocol`; `AgentLoopRunner` only validates policy and registry state; OpenRouter-specific `tool_choice` JSON remains inside `OpenRouterAgentClient`; `CreateAlarmTool` only labels its existing committing behavior as `AgentToolRisk.WRITE`.

## Current Gate Status

- Task 1 focused unit tests: passed.
- Task 2 focused unit tests: passed.
- Task 3 focused unit tests: passed.
- Task 4 focused unit tests: passed.
- Debug androidTest APK compile: passed after updating stale androidTest fakes to the current agent gateway boundary.
- Full local gate (`testDebugUnitTest`, `lintDebug`, `assembleDebug`): pending later integration task.
- Connected Android test: pending later integration task.

## Task 2: Permanent `reject_unclear_request` Tool

- Humanize loop note: the current active loop started at base commit `61bc10d`. The tracked plan file is intentionally not modified during this loop because the Humanize stop gate enforces tracked plan immutability.
- BitLesson selection: `.humanize/bitlesson.md` has no actual lessons. The selector returned its placeholder format again, so Task 2 proceeded with `LESSON_IDS: NONE`.
- Red evidence: `artifacts/2026-06-24-voice-first-ai-alarm/task2-red-reject-tool.log`
  - Command: `JAVA_HOME=/home/ronghua/.cache/codex-jdks/jdk-17 ANDROID_HOME=/home/ronghua/.cache/android-sdk ./gradlew --no-daemon testDebugUnitTest --tests com.cory.noter.agent.tools.RejectUnclearRequestToolTest --tests com.cory.noter.ai.AiAlarmCreatorTest`
  - Result: expected compile failure because `RejectUnclearRequestTool` did not exist and `AiAlarmCreator` had not registered or mapped it yet.
- Green evidence: `artifacts/2026-06-24-voice-first-ai-alarm/task2-green-focused-tests.log`
  - Command: `JAVA_HOME=/home/ronghua/.cache/codex-jdks/jdk-17 ANDROID_HOME=/home/ronghua/.cache/android-sdk ./gradlew --no-daemon testDebugUnitTest --tests com.cory.noter.agent.tools.RejectUnclearRequestToolTest --tests com.cory.noter.ai.AiAlarmCreatorTest`
  - Result: passed after adding `RejectUnclearRequestTool`, registering both tools, changing text AI creation to `RequiredAnyTool`, and mapping reject tool results to `AiCreateResult.ClarificationRequired`.
- Focused command evidence:
  - `artifacts/2026-06-24-voice-first-ai-alarm/task2-green-reject-tool.log`
  - `artifacts/2026-06-24-voice-first-ai-alarm/task2-green-ai-alarm-creator.log`
  - Both listed Task 2 focused commands passed.
- Existing agent/tool regression evidence:
  - `artifacts/2026-06-24-voice-first-ai-alarm/task2-green-agent-tool-regression.log`
  - Command covered `AgentLoopRunnerTest`, `OpenRouterAgentClientTest`, `CreateAlarmToolTest`, and `CreateAlarmArgumentsParserTest`.
  - Result: passed.
- Diff check:
  - Command: `git diff --check`
  - Result: passed with no output.
- Bounded architecture/refactor review:
  - Result: pass; no boundary drift found.
  - Notes: `RejectUnclearRequestTool` is provider-neutral and has no repository or scheduler dependency; `AiAlarmCreator` remains the UI-facing adapter that registers tools and maps tool results; prompt wording remains in `AiAlarmPromptBuilder`; alarm writes and scheduling still flow only through `CreateAlarmTool`.

## Round 1: Preserve Reject Result Through Finalization Failure

- Review source: `.humanize/rlcr/2026-06-24_23-32-34/round-0-review-result.md`
- Red evidence: `artifacts/2026-06-24-voice-first-ai-alarm/round1-red-reject-finalization.log`
  - Command: `GRADLE_USER_HOME=/tmp/noter-gradle-home JAVA_HOME=/home/ronghua/.cache/codex-jdks/jdk-17 ANDROID_HOME=/home/ronghua/.cache/android-sdk ./gradlew --no-daemon testDebugUnitTest --tests com.cory.noter.agent.AgentLoopRunnerTest --tests com.cory.noter.ai.AiAlarmCreatorTest`
  - Result: expected compile failure because the provider-neutral preserved-tool-result path did not exist yet.
- Green evidence: `artifacts/2026-06-24-voice-first-ai-alarm/round1-green-reject-finalization-focused.log`
  - Command: `GRADLE_USER_HOME=/tmp/noter-gradle-home JAVA_HOME=/home/ronghua/.cache/codex-jdks/jdk-17 ANDROID_HOME=/home/ronghua/.cache/android-sdk ./gradlew --no-daemon testDebugUnitTest --tests com.cory.noter.agent.AgentLoopRunnerTest --tests com.cory.noter.ai.AiAlarmCreatorTest --tests com.cory.noter.agent.tools.RejectUnclearRequestToolTest`
  - Result: passed after adding `AgentRunResult.FailedAfterToolResults`, preserving non-committing tool results in `AgentLoopRunner`, and mapping preserved rejected results to `AiCreateResult.ClarificationRequired`.
- Required focused command evidence:
  - `artifacts/2026-06-24-voice-first-ai-alarm/round1-green-agent-loop-runner.log`
  - `artifacts/2026-06-24-voice-first-ai-alarm/round1-green-ai-alarm-creator.log`
  - `artifacts/2026-06-24-voice-first-ai-alarm/round1-green-reject-tool.log`
  - All listed Round 1 focused commands passed.
- Diff check:
  - Command: `git diff --check`
  - Result: passed with no output.
- Bounded architecture/refactor review:
  - Result: pass; no boundary drift found.
  - Notes: `AgentRunResult.FailedAfterToolResults` is provider-neutral agent runtime plumbing for non-committing tool results; committed create-alarm results still use `CompletedWithFinalizationFailure`; `AiAlarmCreator` only maps preserved rejected results to the existing `AiCreateResult.ClarificationRequired` UI-facing result.

## Round 2: ASR Model Settings

- Round contract: `.humanize/rlcr/2026-06-24_23-32-34/round-2-contract.md`
- BitLesson selection: `.humanize/bitlesson.md` has no actual lessons. The selector returned its placeholder format for Task 3 and the small androidTest compatibility subtask, so Round 2 proceeded with `LESSON_IDS: NONE`.
- Red evidence: `artifacts/2026-06-24-voice-first-ai-alarm/round2-red-asr-settings.log`
  - Command: `JAVA_TOOL_OPTIONS=-Duser.home=/tmp/noter-home HOME=/tmp/noter-home GRADLE_USER_HOME=/tmp/noter-gradle-home JAVA_HOME=/home/ronghua/.cache/codex-jdks/jdk-17 ANDROID_HOME=/home/ronghua/.cache/android-sdk ./gradlew --no-daemon testDebugUnitTest --tests com.cory.noter.data.settings.DataStoreSettingsRepositoryTest --tests com.cory.noter.ui.settings.SettingsViewModelTest`
  - Result: expected compile failure because `AsrModel`, `selectedAsrModelId`, `setSelectedAsrModel`, and settings ASR ViewModel state/actions were not implemented yet.
- Green combined evidence: `artifacts/2026-06-24-voice-first-ai-alarm/round2-green-asr-settings-focused.log`
  - Command: `JAVA_TOOL_OPTIONS=-Duser.home=/tmp/noter-home HOME=/tmp/noter-home GRADLE_USER_HOME=/tmp/noter-gradle-home JAVA_HOME=/home/ronghua/.cache/codex-jdks/jdk-17 ANDROID_HOME=/home/ronghua/.cache/android-sdk ./gradlew --no-daemon testDebugUnitTest --tests com.cory.noter.data.settings.DataStoreSettingsRepositoryTest --tests com.cory.noter.ui.settings.SettingsViewModelTest`
  - Result: passed after adding the ASR model catalog, settings persistence, ViewModel state/actions, settings radio group, and localized strings.
- Required focused command evidence:
  - `artifacts/2026-06-24-voice-first-ai-alarm/round2-green-datastore-settings.log`
  - `artifacts/2026-06-24-voice-first-ai-alarm/round2-green-settings-viewmodel.log`
  - Both listed Task 3 focused commands passed.
- Settings smoke compile evidence:
  - Initial log: `artifacts/2026-06-24-voice-first-ai-alarm/round2-assemble-debug-android-test.log`
  - Initial result: failed on stale androidTest fakes still referencing the deleted pre-agent `OpenRouterGateway` / `AiAlarmResponseParser` path.
  - Fixed log: `artifacts/2026-06-24-voice-first-ai-alarm/round2-assemble-debug-android-test-fixed.log`
  - Fixed result: `assembleDebugAndroidTest` passed after moving the androidTest fake to `AgentLlmGateway` and wiring the smoke test through `AgentLoopRunner`.
- Diff check:
  - Log: `artifacts/2026-06-24-voice-first-ai-alarm/round2-git-diff-check.log`
  - Command: `git diff --check`
  - Result: passed with no output.
- Bounded architecture/refactor review:
  - Result: pass after cleanup.
  - Finding fixed: the first implementation made `domain/settings/AppSettings` import the higher-level `ai` package for `AsrModel.DefaultId`. That boundary drift was removed; `AppSettings` now remains a plain string settings value and the repository/fakes provide model defaults at their own boundary.
  - Notes: ASR model catalog remains in `ai/AsrModel.kt` per plan; DataStore validation stays in `data/settings`; settings UI continues to talk through `SettingsViewModel`; androidTest fake compatibility was limited to the current `AgentLlmGateway` test boundary.

## Round 3: OpenRouter ASR Adapter

- Round contract: `.humanize/rlcr/2026-06-24_23-32-34/round-3-contract.md`
- BitLesson selection: `.humanize/bitlesson.md` has no actual lessons. The selector returned its placeholder format, so Round 3 proceeded with `LESSON_IDS: NONE`.
- Red evidence: `artifacts/2026-06-24-voice-first-ai-alarm/round3-red-openrouter-asr-client.log`
  - Command: `JAVA_TOOL_OPTIONS=-Duser.home=/tmp/noter-home HOME=/tmp/noter-home GRADLE_USER_HOME=/tmp/noter-gradle-home JAVA_HOME=/home/ronghua/.cache/codex-jdks/jdk-17 ANDROID_HOME=/home/ronghua/.cache/android-sdk ./gradlew --no-daemon testDebugUnitTest --tests com.cory.noter.ai.OpenRouterAsrClientTest`
  - Result: expected compile failure because `OpenRouterAsrClient`, `OpenRouterAsrRequest`, and `AsrTranscriptionResult` did not exist yet.
- Green evidence: `artifacts/2026-06-24-voice-first-ai-alarm/round3-green-openrouter-asr-client.log`
  - Command: `JAVA_TOOL_OPTIONS=-Duser.home=/tmp/noter-home HOME=/tmp/noter-home GRADLE_USER_HOME=/tmp/noter-gradle-home JAVA_HOME=/home/ronghua/.cache/codex-jdks/jdk-17 ANDROID_HOME=/home/ronghua/.cache/android-sdk ./gradlew --no-daemon testDebugUnitTest --tests com.cory.noter.ai.OpenRouterAsrClientTest`
  - Result: passed after adding `OpenRouterAsrClient`, explicit ASR result categories, `/api/v1/audio/transcriptions` request mapping, base64 audio payload serialization, transcript parsing, and explicit network/rate-limit/remote/malformed/blank failure handling.
- Diff check:
  - Log: `artifacts/2026-06-24-voice-first-ai-alarm/round3-git-diff-check.log`
  - Command: `git diff --check`
  - Result: passed with no output.
- Bounded architecture/refactor review:
  - Result: pass.
  - Notes: OpenRouter-specific ASR request/response JSON remains inside `OpenRouterAsrClient`; shared OpenRouter transport constants stay in `OpenRouterHttp`; the result type exposes explicit failure categories without adding voice-capture or UI orchestration scope; the client has no fallback or retry path that could silently switch ASR models.
