package com.cory.noter.ui.editor

import com.cory.noter.ai.OpenRouterModel
import com.cory.noter.alarm.AlarmSchedulingUseCase
import com.cory.noter.alarm.FakeAlarmScheduler
import com.cory.noter.data.settings.FakeSettingsRepository
import com.cory.noter.domain.alarm.AlarmValidation
import com.cory.noter.ui.FakeAlarmRepository
import com.cory.noter.ui.MainDispatcherRule
import com.google.common.truth.Truth.assertThat
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class AlarmEditorViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val zoneId = ZoneId.of("Asia/Shanghai")
    private val clock = Clock.fixed(Instant.parse("2026-04-23T01:00:00Z"), zoneId)

    @Test
    fun `save with blank title exposes validation failure`() = runTest {
        val repository = FakeAlarmRepository(clock = clock, zoneId = zoneId)
        val viewModel = AlarmEditorViewModel(
            alarmId = null,
            repository = repository,
            settingsRepository = FakeSettingsRepository(),
            schedulingUseCase = AlarmSchedulingUseCase(FakeAlarmScheduler()),
            clock = clock,
            zoneId = zoneId,
        )

        advanceUntilIdle()
        viewModel.onTitleChanged("")
        viewModel.save()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.validationErrors)
            .contains(AlarmValidation.Error.BLANK_TITLE)
        assertThat(repository.alarms.first()).isEmpty()
    }

    @Test
    fun `save creates enabled alarm and schedules it`() = runTest {
        val repository = FakeAlarmRepository(clock = clock, zoneId = zoneId)
        val scheduler = FakeAlarmScheduler()
        val settingsRepository = FakeSettingsRepository()
        val viewModel = AlarmEditorViewModel(
            alarmId = null,
            repository = repository,
            settingsRepository = settingsRepository,
            schedulingUseCase = AlarmSchedulingUseCase(scheduler),
            clock = clock,
            zoneId = zoneId,
        )

        advanceUntilIdle()
        viewModel.onTitleChanged("Drink water")
        viewModel.onHourChanged("7")
        viewModel.onMinuteChanged("45")
        viewModel.onRepeatRuleChanged(EditorRepeatOption.DAILY)
        viewModel.onEnabledChanged(true)
        viewModel.save()
        advanceUntilIdle()

        val savedAlarm = repository.alarms.first().single()
        assertThat(savedAlarm.title).isEqualTo("Drink water")
        assertThat(savedAlarm.hour).isEqualTo(7)
        assertThat(savedAlarm.minute).isEqualTo(45)
        assertThat(savedAlarm.enabled).isTrue()
        assertThat(savedAlarm.ringtoneUri)
            .isEqualTo(settingsRepository.settings.first().defaultRingtoneUri)
        assertThat(savedAlarm.source.name).isEqualTo("MANUAL")
        assertThat(viewModel.uiState.value.savedAlarmId).isEqualTo(savedAlarm.id)
        assertThat(scheduler.scheduledIds).contains(savedAlarm.id)
    }
}
