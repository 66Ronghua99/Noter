package com.cory.noter.ui.ai

import com.cory.noter.ai.AiAlarmCreator
import com.cory.noter.ai.AiAlarmPromptBuilder
import com.cory.noter.ai.AiAlarmResponseParser
import com.cory.noter.alarm.AlarmSchedulingUseCase
import com.cory.noter.alarm.FakeAlarmScheduler
import com.cory.noter.data.settings.FakeSettingsRepository
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
}
