package com.cory.noter.ai

import com.cory.noter.agent.AgentLlmResult
import com.cory.noter.agent.AgentLoopRunner
import com.cory.noter.agent.AgentMessage
import com.cory.noter.agent.AgentMessageRole
import com.cory.noter.agent.AgentToolCall
import com.cory.noter.alarm.AlarmSchedulingUseCase
import com.cory.noter.alarm.FakeAlarmScheduler
import com.cory.noter.alarm.ScheduleResult
import com.cory.noter.data.settings.FakeSettingsRepository
import com.cory.noter.domain.settings.AppSettings
import com.cory.noter.ui.FakeAlarmRepository
import com.cory.noter.ui.FakeAgentLlmGateway
import com.google.common.truth.Truth.assertThat
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

class AiCreateBackgroundSchedulerTest {
    private val zoneId = ZoneId.of("Asia/Shanghai")
    private val clock = Clock.fixed(Instant.parse("2026-04-23T01:00:00Z"), zoneId)

    @Test
    fun `enqueue reports missing exact alarm permission through notifier`() = runTest {
        val notifier = RecordingNotifier()
        val scheduler = ApplicationAiCreateBackgroundScheduler(
            creator = creator(
                agentGateway = FakeAgentLlmGateway().apply {
                    results += AgentLlmResult.Message(
                        AgentMessage(
                            role = AgentMessageRole.ASSISTANT,
                            content = "",
                            toolCalls = listOf(
                                AgentToolCall(
                                    id = "call-1",
                                    name = "create_alarm",
                                    arguments = validAlarmJson(),
                                ),
                            ),
                        ),
                    )
                },
                alarmScheduler = FakeAlarmScheduler().apply {
                    nextScheduleResult = ScheduleResult.MissingPermission(
                        android.Manifest.permission.SCHEDULE_EXACT_ALARM,
                    )
                },
            ),
            notifier = notifier,
            scope = TestScope(testScheduler),
        )

        scheduler.enqueue("tomorrow at 8 am remind me to take medicine")
        advanceUntilIdle()

        assertThat(notifier.startedCount).isEqualTo(1)
        assertThat(notifier.results.single())
            .isInstanceOf(AiCreateResult.MissingSchedulingPermission::class.java)
    }

    private fun creator(
        agentGateway: FakeAgentLlmGateway,
        alarmScheduler: FakeAlarmScheduler,
    ): AiAlarmCreator = AiAlarmCreator(
        settingsRepository = FakeSettingsRepository(
            initialSettings = AppSettings(
                openRouterApiKey = "sk-or-v1-test",
                selectedModelId = "deepseek/deepseek-v3.2",
                defaultRingtoneUri = AppSettings.DefaultRingtoneUri,
            ),
        ),
        agentLoopRunner = AgentLoopRunner(agentGateway),
        alarmRepository = FakeAlarmRepository(clock = clock, zoneId = zoneId),
        schedulingUseCase = AlarmSchedulingUseCase(alarmScheduler),
        promptBuilder = AiAlarmPromptBuilder(),
        clock = clock,
    )

    private fun validAlarmJson(): String = """
        {
          "title": "Take medicine",
          "hour": 8,
          "minute": 0,
          "repeatRule": { "type": "once", "daysOfWeek": [] },
          "date": "2026-04-24",
          "confidence": 0.92,
          "needsClarification": false,
          "clarificationReason": ""
        }
    """.trimIndent()

    private class RecordingNotifier : AiCreateResultNotifier {
        var startedCount: Int = 0
        val results = mutableListOf<AiCreateResult>()

        override fun notifyStarted() {
            startedCount += 1
        }

        override fun notifyResult(result: AiCreateResult) {
            results += result
        }
    }
}
