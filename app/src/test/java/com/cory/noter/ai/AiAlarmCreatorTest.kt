package com.cory.noter.ai

import com.cory.noter.agent.AgentLoopConfig
import com.cory.noter.agent.AgentFailure
import com.cory.noter.agent.AgentLlmResult
import com.cory.noter.agent.AgentLoopRunner
import com.cory.noter.agent.AgentMessage
import com.cory.noter.agent.AgentMessageRole
import com.cory.noter.agent.AgentToolCall
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.cory.noter.alarm.AlarmSchedulingUseCase
import com.cory.noter.alarm.FakeAlarmScheduler
import com.cory.noter.alarm.ScheduleResult
import com.cory.noter.data.alarm.AlarmDatabase
import com.cory.noter.data.alarm.AlarmRepository
import com.cory.noter.data.alarm.RoomAlarmRepository
import com.cory.noter.data.settings.FakeSettingsRepository
import com.cory.noter.domain.alarm.AlarmSource
import com.cory.noter.domain.alarm.NextTriggerCalculator
import com.cory.noter.domain.alarm.RepeatRule
import com.cory.noter.domain.settings.AppSettings
import com.cory.noter.ui.FakeAgentLlmGateway
import com.google.common.truth.Truth.assertThat
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AiAlarmCreatorTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val now = ZonedDateTime.of(2026, 4, 23, 9, 0, 0, 0, zone)
    private lateinit var database: AlarmDatabase
    private lateinit var repository: AlarmRepository
    private lateinit var settingsRepository: FakeSettingsRepository
    private lateinit var fakeAgentGateway: FakeAgentLlmGateway
    private lateinit var fakeScheduler: FakeAlarmScheduler
    private lateinit var creator: AiAlarmCreator

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AlarmDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
        repository = RoomAlarmRepository(
            alarmDao = database.alarmDao(),
            clock = Clock.fixed(now.toInstant(), zone),
            nextTriggerCalculator = NextTriggerCalculator(),
            zoneIdProvider = { zone },
        )
        settingsRepository = FakeSettingsRepository()
        fakeAgentGateway = FakeAgentLlmGateway()
        fakeScheduler = FakeAlarmScheduler()
        creator = AiAlarmCreator(
            settingsRepository = settingsRepository,
            agentLoopRunner = AgentLoopRunner(fakeAgentGateway),
            alarmRepository = repository,
            schedulingUseCase = AlarmSchedulingUseCase(fakeScheduler),
            promptBuilder = AiAlarmPromptBuilder(),
            clock = Clock.fixed(now.toInstant(), zone),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `missing api key fails before network request`() = runTest {
        settingsRepository.set(
            AppSettings(
                openRouterApiKey = "",
                selectedModelId = OpenRouterModel.DefaultId,
                defaultRingtoneUri = AppSettings.DefaultRingtoneUri,
            ),
        )

        val result = creator.createFromText("tomorrow 8 remind me to take medicine")

        assertThat(result).isEqualTo(AiCreateResult.MissingApiKey)
        assertThat(fakeAgentGateway.requests).isEmpty()
        assertThat(repository.alarms.first()).isEmpty()
    }

    @Test
    fun `network failure leaves alarms unchanged`() = runTest {
        settingsRepository.set(validSettings())
        fakeAgentGateway.results += AgentLlmResult.NetworkFailure("socket timeout")

        val result = creator.createFromText("tomorrow 8 remind me to take medicine")

        assertThat(result).isEqualTo(AiCreateResult.NetworkFailure("socket timeout"))
        assertThat(repository.alarms.first()).isEmpty()
        assertThat(fakeScheduler.scheduledAlarms).isEmpty()
    }

    @Test
    fun `rate limited model leaves alarms unchanged`() = runTest {
        settingsRepository.set(validSettings(modelId = "deepseek/deepseek-v3.2"))
        fakeAgentGateway.results += AgentLlmResult.RateLimited("Rate limit exceeded.")

        val result = creator.createFromText("tomorrow 8 remind me to take medicine")

        assertThat(result).isEqualTo(AiCreateResult.RateLimited("Rate limit exceeded."))
        assertThat(fakeAgentGateway.requests.single().modelId).isEqualTo("deepseek/deepseek-v3.2")
        assertThat(repository.alarms.first()).isEmpty()
    }

    @Test
    fun `remote failure leaves alarms unchanged`() = runTest {
        settingsRepository.set(validSettings())
        fakeAgentGateway.results += AgentLlmResult.RemoteFailure(503, "provider unavailable")

        val result = creator.createFromText("tomorrow 8 remind me to take medicine")

        assertThat(result).isEqualTo(AiCreateResult.RemoteFailure(503, "provider unavailable"))
        assertThat(repository.alarms.first()).isEmpty()
        assertThat(fakeScheduler.scheduledAlarms).isEmpty()
    }

    @Test
    fun `invalid model response becomes invalid response without creating alarm`() = runTest {
        settingsRepository.set(validSettings())
        fakeAgentGateway.results += AgentLlmResult.InvalidResponse("assistant payload was malformed")

        val result = creator.createFromText("tomorrow 8 remind me to take medicine")

        assertThat(result).isEqualTo(AiCreateResult.InvalidResponse("assistant payload was malformed"))
        assertThat(repository.alarms.first()).isEmpty()
    }

    @Test
    fun `missing tool call does not create alarm`() = runTest {
        settingsRepository.set(validSettings())
        fakeAgentGateway.results += AgentLlmResult.Message(
            AgentMessage(AgentMessageRole.ASSISTANT, "I cannot do that."),
        )

        val result = creator.createFromText("tomorrow 8 remind me to take medicine")

        assertThat(result).isInstanceOf(AiCreateResult.InvalidResponse::class.java)
        assertThat((result as AiCreateResult.InvalidResponse).reason)
            .isEqualTo("Model did not call a tool.")
        assertThat(repository.alarms.first()).isEmpty()
    }

    @Test
    fun `tool not registered becomes invalid response without creating alarm`() = runTest {
        settingsRepository.set(validSettings())
        fakeAgentGateway.results += AgentLlmResult.Message(
            AgentMessage(
                role = AgentMessageRole.ASSISTANT,
                content = "",
                toolCalls = listOf(
                    AgentToolCall(
                        id = "call-1",
                        name = "missing_tool",
                        arguments = validAlarmJson(),
                    ),
                ),
            ),
        )

        val result = creator.createFromText("tomorrow 8 remind me to take medicine")

        assertThat(result).isEqualTo(AiCreateResult.InvalidResponse("Tool is not registered: missing_tool"))
        assertThat(repository.alarms.first()).isEmpty()
    }

    @Test
    fun `tool execution limit before first tool call becomes invalid response`() = runTest {
        settingsRepository.set(validSettings())
        val limitedCreator = AiAlarmCreator(
            settingsRepository = settingsRepository,
            agentLoopRunner = AgentLoopRunner(
                fakeAgentGateway,
                AgentLoopConfig(maxToolExecutions = 0),
            ),
            alarmRepository = repository,
            schedulingUseCase = AlarmSchedulingUseCase(fakeScheduler),
            promptBuilder = AiAlarmPromptBuilder(),
            clock = Clock.fixed(now.toInstant(), zone),
        )
        fakeAgentGateway.results += AgentLlmResult.Message(
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

        val result = limitedCreator.createFromText("tomorrow 8 remind me to take medicine")

        assertThat(result).isEqualTo(AiCreateResult.InvalidResponse("Tool execution limit exceeded."))
        assertThat(repository.alarms.first()).isEmpty()
    }

    @Test
    fun `valid response creates ai alarm and schedules it`() = runTest {
        settingsRepository.set(validSettings(modelId = "deepseek/deepseek-v3.2"))
        fakeAgentGateway.results += AgentLlmResult.Message(
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
        fakeAgentGateway.results += AgentLlmResult.Message(
            AgentMessage(AgentMessageRole.ASSISTANT, "Created."),
        )

        val result = creator.createFromText("tomorrow morning remind me to take medicine")

        assertThat(result).isInstanceOf(AiCreateResult.Created::class.java)
        val created = (result as AiCreateResult.Created).alarm
        assertThat(created.id).isGreaterThan(0)
        assertThat(created.title).isEqualTo("Take medicine")
        assertThat(created.hour).isEqualTo(8)
        assertThat(created.minute).isEqualTo(30)
        assertThat(created.repeatRule).isEqualTo(RepeatRule.Once(LocalDate.of(2026, 4, 24)))
        assertThat(created.enabled).isTrue()
        assertThat(created.ringtoneUri).isEqualTo(AppSettings.DefaultRingtoneUri)
        assertThat(created.source).isEqualTo(AlarmSource.AI)
        assertThat(created.aiOriginalText).isEqualTo("tomorrow morning remind me to take medicine")
        assertThat(created.nextTriggerAtMillis).isEqualTo(
            ZonedDateTime.of(2026, 4, 24, 8, 30, 0, 0, zone).toInstant().toEpochMilli(),
        )
        assertThat(repository.alarms.first()).containsExactly(created)
        assertThat(fakeScheduler.scheduledAlarms[created.id]).isEqualTo(created)
        assertThat(fakeAgentGateway.requests).hasSize(2)
        assertThat(fakeAgentGateway.requests[0].modelId)
            .isEqualTo("deepseek/deepseek-v3.2")
        assertThat(fakeAgentGateway.requests[0].messages.single().content)
            .contains("Current local date: 2026-04-23")
        assertThat(fakeAgentGateway.requests.first().tools.single().name)
            .isEqualTo("create_alarm")
        assertThat(fakeAgentGateway.requests.first().tools.single().name)
            .isNotEqualTo("submit_alarm_draft")
    }

    @Test
    fun `clarification required preserves dedicated result category`() = runTest {
        settingsRepository.set(validSettings())
        fakeAgentGateway.results += AgentLlmResult.Message(
            AgentMessage(
                role = AgentMessageRole.ASSISTANT,
                content = "",
                toolCalls = listOf(
                    AgentToolCall(
                        id = "call-1",
                        name = "create_alarm",
                        arguments = clarificationAlarmJson(),
                    ),
                ),
            ),
        )

        val result = creator.createFromText("set a medicine reminder for tomorrow morning")

        assertThat(result).isEqualTo(AiCreateResult.ClarificationRequired("Which day should I use?"))
        assertThat(repository.alarms.first()).isEmpty()
    }

    @Test
    fun `repository create failure preserves create failed category`() = runTest {
        settingsRepository.set(validSettings())
        val createFailingCreator = AiAlarmCreator(
            settingsRepository = settingsRepository,
            agentLoopRunner = AgentLoopRunner(fakeAgentGateway),
            alarmRepository = CreateFailingAlarmRepository(repository),
            schedulingUseCase = AlarmSchedulingUseCase(fakeScheduler),
            promptBuilder = AiAlarmPromptBuilder(),
            clock = Clock.fixed(now.toInstant(), zone),
        )
        fakeAgentGateway.results += AgentLlmResult.Message(
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

        val result = createFailingCreator.createFromText("tomorrow morning remind me to take medicine")

        assertThat(result).isEqualTo(AiCreateResult.CreateFailed("database write failed"))
        assertThat(repository.alarms.first()).isEmpty()
    }

    @Test
    fun `committed schedule failure maps committed reason`() = runTest {
        settingsRepository.set(validSettings())
        fakeScheduler.nextScheduleResult = ScheduleResult.Failed("scheduler backend rejected alarm")
        fakeAgentGateway.results += AgentLlmResult.Message(
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

        val result = creator.createFromText("tomorrow morning remind me to take medicine")

        assertThat(result).isInstanceOf(AiCreateResult.ScheduleFailed::class.java)
        val scheduleFailed = result as AiCreateResult.ScheduleFailed
        assertThat(scheduleFailed.reason).isEqualTo("scheduler backend rejected alarm")
        assertThat(scheduleFailed.alarm.title).isEqualTo("Take medicine")
        assertThat(repository.alarms.first()).containsExactly(scheduleFailed.alarm)
        assertThat(fakeScheduler.scheduledAlarms).isEmpty()
    }

    @Test
    fun `committed tool result stays authoritative when finalization fails`() = runTest {
        settingsRepository.set(validSettings())
        fakeAgentGateway.results += AgentLlmResult.Message(
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
        fakeAgentGateway.results += AgentLlmResult.InvalidResponse("malformed final assistant message")

        val result = creator.createFromText("tomorrow morning remind me to take medicine")

        assertThat(result).isInstanceOf(AiCreateResult.Created::class.java)
        val created = (result as AiCreateResult.Created).alarm
        assertThat(created.title).isEqualTo("Take medicine")
    }

    @Test
    fun `model turn limit after committed create still returns created alarm`() = runTest {
        settingsRepository.set(validSettings())
        val turnLimitedCreator = AiAlarmCreator(
            settingsRepository = settingsRepository,
            agentLoopRunner = AgentLoopRunner(
                fakeAgentGateway,
                AgentLoopConfig(maxModelTurns = 1),
            ),
            alarmRepository = repository,
            schedulingUseCase = AlarmSchedulingUseCase(fakeScheduler),
            promptBuilder = AiAlarmPromptBuilder(),
            clock = Clock.fixed(now.toInstant(), zone),
        )
        fakeAgentGateway.results += AgentLlmResult.Message(
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

        val result = turnLimitedCreator.createFromText("tomorrow morning remind me to take medicine")

        assertThat(result).isInstanceOf(AiCreateResult.Created::class.java)
        assertThat((result as AiCreateResult.Created).alarm.title).isEqualTo("Take medicine")
    }

    @Test
    fun `model turn limit before commit becomes invalid response and creates no alarm`() = runTest {
        settingsRepository.set(validSettings())

        val result = creator.mapFailureForTest(
            AgentFailure.ModelTurnLimitExceeded("Model turn limit exceeded."),
        )

        assertThat(result).isEqualTo(
            AiCreateResult.InvalidResponse("Model turn limit exceeded."),
        )
        assertThat(repository.alarms.first()).isEmpty()
    }

    private fun validSettings(
        modelId: String = OpenRouterModel.DefaultId,
    ): AppSettings = AppSettings(
        openRouterApiKey = "sk-or-v1-test",
        selectedModelId = modelId,
        defaultRingtoneUri = AppSettings.DefaultRingtoneUri,
    )

    private fun validAlarmJson(): String = """
        {
          "title": "Take medicine",
          "hour": 8,
          "minute": 30,
          "repeatRule": { "type": "once", "daysOfWeek": [] },
          "date": "2026-04-24",
          "confidence": 0.92,
          "needsClarification": false,
          "clarificationReason": ""
        }
    """.trimIndent()

    private fun clarificationAlarmJson(): String = """
        {
          "title": "Take medicine",
          "hour": 8,
          "minute": 30,
          "repeatRule": { "type": "once", "daysOfWeek": [] },
          "date": "2026-04-24",
          "confidence": 0.4,
          "needsClarification": true,
          "clarificationReason": "Which day should I use?"
        }
    """.trimIndent()

    private class CreateFailingAlarmRepository(
        private val delegate: AlarmRepository,
    ) : AlarmRepository by delegate {
        override suspend fun create(draft: com.cory.noter.data.alarm.AlarmDraft): com.cory.noter.domain.alarm.Alarm {
            error("database write failed")
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun AiAlarmCreator.mapFailureForTest(failure: AgentFailure): AiCreateResult {
        val method = AiAlarmCreator::class.java.getDeclaredMethod(
            "toAiCreateResult",
            AgentFailure::class.java,
        )
        method.isAccessible = true
        return method.invoke(this, failure) as AiCreateResult
    }
}
