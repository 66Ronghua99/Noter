package com.cory.noter.ai

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
    private lateinit var fakeOpenRouter: FakeOpenRouterGateway
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
        fakeOpenRouter = FakeOpenRouterGateway()
        fakeScheduler = FakeAlarmScheduler()
        creator = AiAlarmCreator(
            settingsRepository = settingsRepository,
            openRouterClient = fakeOpenRouter,
            alarmRepository = repository,
            schedulingUseCase = AlarmSchedulingUseCase(fakeScheduler),
            promptBuilder = AiAlarmPromptBuilder(),
            responseParser = AiAlarmResponseParser(),
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
        assertThat(fakeOpenRouter.requests).isEmpty()
        assertThat(repository.alarms.first()).isEmpty()
    }

    @Test
    fun `network failure leaves alarms unchanged`() = runTest {
        settingsRepository.set(validSettings())
        fakeOpenRouter.nextResult = OpenRouterResult.NetworkFailure("socket timeout")

        val result = creator.createFromText("tomorrow 8 remind me to take medicine")

        assertThat(result).isEqualTo(AiCreateResult.NetworkFailure("socket timeout"))
        assertThat(repository.alarms.first()).isEmpty()
        assertThat(fakeScheduler.scheduledAlarms).isEmpty()
    }

    @Test
    fun `rate limited model leaves alarms unchanged`() = runTest {
        settingsRepository.set(validSettings(modelId = "deepseek/deepseek-v3.2"))
        fakeOpenRouter.nextResult = OpenRouterResult.RateLimited("Rate limit exceeded.")

        val result = creator.createFromText("tomorrow 8 remind me to take medicine")

        assertThat(result).isEqualTo(AiCreateResult.RateLimited("Rate limit exceeded."))
        assertThat(fakeOpenRouter.requests.single().modelId).isEqualTo("deepseek/deepseek-v3.2")
        assertThat(repository.alarms.first()).isEmpty()
    }

    @Test
    fun `invalid parsed response does not create alarm`() = runTest {
        settingsRepository.set(validSettings())
        fakeOpenRouter.nextResult = OpenRouterResult.Success("not json")

        val result = creator.createFromText("tomorrow 8 remind me to take medicine")

        assertThat(result).isInstanceOf(AiCreateResult.InvalidResponse::class.java)
        assertThat((result as AiCreateResult.InvalidResponse).reason).contains("Invalid JSON")
        assertThat(repository.alarms.first()).isEmpty()
    }

    @Test
    fun `clarification response is surfaced distinctly`() = runTest {
        settingsRepository.set(validSettings())
        fakeOpenRouter.nextResult = OpenRouterResult.Success(
            """
            {
              "title": "",
              "hour": 8,
              "minute": 0,
              "repeatRule": { "type": "once", "daysOfWeek": [] },
              "date": "2026-04-24",
              "confidence": 0.2,
              "needsClarification": true,
              "clarificationReason": "Which day should I use?"
            }
            """.trimIndent(),
        )

        val result = creator.createFromText("remind me to take medicine at 8")

        assertThat(result).isEqualTo(
            AiCreateResult.ClarificationRequired("Which day should I use?"),
        )
        assertThat(repository.alarms.first()).isEmpty()
    }

    @Test
    fun `blank clarification reason is rejected as invalid response`() = runTest {
        settingsRepository.set(validSettings())
        fakeOpenRouter.nextResult = OpenRouterResult.Success(
            """
            {
              "title": "",
              "hour": 8,
              "minute": 0,
              "repeatRule": { "type": "once", "daysOfWeek": [] },
              "date": "2026-04-24",
              "confidence": 0.2,
              "needsClarification": true,
              "clarificationReason": ""
            }
            """.trimIndent(),
        )

        val result = creator.createFromText("remind me to take medicine at 8")

        assertThat(result).isInstanceOf(AiCreateResult.InvalidResponse::class.java)
        assertThat((result as AiCreateResult.InvalidResponse).reason)
            .contains("clarificationReason")
        assertThat(repository.alarms.first()).isEmpty()
    }

    @Test
    fun `valid response creates ai alarm and schedules it`() = runTest {
        settingsRepository.set(validSettings(modelId = "deepseek/deepseek-v3.2"))
        fakeOpenRouter.nextResult = OpenRouterResult.Success(
            """
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
            """.trimIndent(),
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
        assertThat(fakeOpenRouter.requests.single().modelId)
            .isEqualTo("deepseek/deepseek-v3.2")
        assertThat(fakeOpenRouter.requests.single().prompt)
            .contains("Current local date: 2026-04-23")
    }

    private fun validSettings(
        modelId: String = OpenRouterModel.DefaultId,
    ): AppSettings = AppSettings(
        openRouterApiKey = "sk-or-v1-test",
        selectedModelId = modelId,
        defaultRingtoneUri = AppSettings.DefaultRingtoneUri,
    )

    private class FakeOpenRouterGateway : OpenRouterGateway {
        val requests = mutableListOf<RequestCall>()
        var nextResult: OpenRouterResult = OpenRouterResult.Success("{}")

        override suspend fun createChatCompletion(
            apiKey: String,
            modelId: String,
            prompt: String,
        ): OpenRouterResult {
            requests += RequestCall(
                apiKey = apiKey,
                modelId = modelId,
                prompt = prompt,
            )
            return nextResult
        }
    }

    private data class RequestCall(
        val apiKey: String,
        val modelId: String,
        val prompt: String,
    )
}
