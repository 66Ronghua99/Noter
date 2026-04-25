package com.cory.noter.ui.ai

import com.cory.noter.ai.AiAlarmCreator
import com.cory.noter.ai.AiAlarmPromptBuilder
import com.cory.noter.ai.AiAlarmResponseParser
import com.cory.noter.ai.OpenRouterResult
import com.cory.noter.alarm.AlarmSchedulingUseCase
import com.cory.noter.alarm.FakeAlarmScheduler
import com.cory.noter.alarm.ScheduleResult
import com.cory.noter.data.settings.FakeSettingsRepository
import com.cory.noter.domain.settings.AppSettings
import com.cory.noter.ui.FakeAlarmRepository
import com.cory.noter.ui.FakeOpenRouterGateway
import com.cory.noter.ui.MainDispatcherRule
import com.google.common.truth.Truth.assertThat
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class AiCreateViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val zoneId = ZoneId.of("Asia/Shanghai")
    private val clock = Clock.fixed(Instant.parse("2026-04-23T01:00:00Z"), zoneId)

    @Test
    fun `missing api key becomes explicit settings error`() = runTest {
        val settingsRepository = FakeSettingsRepository()
        val viewModel = AiCreateViewModel(
            creator = AiAlarmCreator(
                settingsRepository = settingsRepository,
                openRouterClient = FakeOpenRouterGateway(),
                alarmRepository = FakeAlarmRepository(clock = clock, zoneId = zoneId),
                schedulingUseCase = AlarmSchedulingUseCase(FakeAlarmScheduler()),
                promptBuilder = AiAlarmPromptBuilder(),
                responseParser = AiAlarmResponseParser(),
                clock = clock,
            ),
            settingsRepository = settingsRepository,
        )

        advanceUntilIdle()
        viewModel.onPromptChanged("wake me up tomorrow at 8")
        viewModel.submit()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.errorMessage)
            .isEqualTo("Add an OpenRouter API key in Settings before using AI create.")
        assertThat(viewModel.uiState.value.isLoading).isFalse()
    }

    @Test
    fun `missing exact alarm permission exposes permission action`() = runTest {
        val settingsRepository = FakeSettingsRepository(
            initialSettings = AppSettings(
                openRouterApiKey = "sk-or-v1-test",
                selectedModelId = "deepseek/deepseek-v3.2",
                defaultRingtoneUri = AppSettings.DefaultRingtoneUri,
            ),
        )
        val openRouterGateway = FakeOpenRouterGateway().apply {
            nextResult = OpenRouterResult.Success(
                """
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
                """.trimIndent(),
            )
        }
        val scheduler = FakeAlarmScheduler().apply {
            nextScheduleResult = ScheduleResult.MissingPermission(
                android.Manifest.permission.SCHEDULE_EXACT_ALARM,
            )
        }
        val viewModel = AiCreateViewModel(
            creator = AiAlarmCreator(
                settingsRepository = settingsRepository,
                openRouterClient = openRouterGateway,
                alarmRepository = FakeAlarmRepository(clock = clock, zoneId = zoneId),
                schedulingUseCase = AlarmSchedulingUseCase(scheduler),
                promptBuilder = AiAlarmPromptBuilder(),
                responseParser = AiAlarmResponseParser(),
                clock = clock,
            ),
            settingsRepository = settingsRepository,
        )

        advanceUntilIdle()
        viewModel.onPromptChanged("tomorrow at 8 am remind me to take medicine")
        viewModel.submit()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.exactAlarmPermissionRequired).isTrue()
        assertThat(viewModel.uiState.value.errorMessage)
            .contains(android.Manifest.permission.SCHEDULE_EXACT_ALARM)
    }

    @Test
    fun `submit queues background creation when scheduler is available`() = runTest {
        val backgroundScheduler = RecordingBackgroundScheduler()
        val viewModel = AiCreateViewModel(
            creator = AiAlarmCreator(
                settingsRepository = FakeSettingsRepository(),
                openRouterClient = FakeOpenRouterGateway(),
                alarmRepository = FakeAlarmRepository(clock = clock, zoneId = zoneId),
                schedulingUseCase = AlarmSchedulingUseCase(FakeAlarmScheduler()),
                promptBuilder = AiAlarmPromptBuilder(),
                responseParser = AiAlarmResponseParser(),
                clock = clock,
            ),
            settingsRepository = FakeSettingsRepository(),
            backgroundScheduler = backgroundScheduler,
        )

        advanceUntilIdle()
        viewModel.onPromptChanged("tomorrow at 8 am remind me to take medicine")
        viewModel.submit()

        assertThat(backgroundScheduler.prompts)
            .containsExactly("tomorrow at 8 am remind me to take medicine")
        assertThat(viewModel.uiState.value.isLoading).isFalse()
        assertThat(viewModel.uiState.value.statusMessage)
            .contains("background")
    }

    private class RecordingBackgroundScheduler : com.cory.noter.ai.AiCreateBackgroundScheduler {
        val prompts = mutableListOf<String>()

        override fun enqueue(prompt: String) {
            prompts += prompt
        }
    }
}
