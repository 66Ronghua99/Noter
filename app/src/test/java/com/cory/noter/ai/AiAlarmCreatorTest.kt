package com.cory.noter.ai

import com.cory.noter.agent.AgentLlmResult
import com.cory.noter.agent.AgentLoopRunner
import com.cory.noter.agent.AgentMessage
import com.cory.noter.agent.AgentMessageRole
import com.cory.noter.agent.AgentToolCall
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.cory.noter.alarm.AlarmSchedulingUseCase
import com.cory.noter.alarm.FakeAlarmScheduler
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
}
