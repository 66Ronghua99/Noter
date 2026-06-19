package com.cory.noter.ui.editor

import com.cory.noter.ai.OpenRouterModel
import com.cory.noter.alarm.AlarmSchedulingUseCase
import com.cory.noter.alarm.FakeAlarmScheduler
import com.cory.noter.alarm.ScheduleResult
import com.cory.noter.data.settings.FakeSettingsRepository
import com.cory.noter.domain.alarm.AlarmValidation
import com.cory.noter.domain.alarm.RepeatRule
import com.cory.noter.ui.FakeAlarmRepository
import com.cory.noter.ui.MainDispatcherRule
import com.google.common.truth.Truth.assertThat
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
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
    fun `new alarm defaults weekly interval count to one`() = runTest {
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

        assertThat(viewModel.uiState.value.intervalWeeksText).isEqualTo("1")
    }

    @Test
    fun `interval start date after end date moves end date forward`() = runTest {
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
        viewModel.onIntervalEndDateChanged("2026-05-01")
        viewModel.onIntervalStartDateChanged("2026-06-01")

        assertThat(viewModel.uiState.value.intervalStartDateText).isEqualTo("2026-06-01")
        assertThat(viewModel.uiState.value.intervalEndDateText).isEqualTo("2026-06-01")
    }

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

    @Test
    fun `save creates weekly interval alarm with explicit date range`() = runTest {
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
        viewModel.onTitleChanged("Team sync")
        viewModel.onHourChanged("8")
        viewModel.onMinuteChanged("15")
        viewModel.onRepeatRuleChanged(EditorRepeatOption.INTERVAL)
        viewModel.onIntervalStartDateChanged("2026-05-04")
        viewModel.onIntervalEndDateChanged("2027-05-04")
        viewModel.onIntervalWeeksChanged("2")
        viewModel.onCustomWeekdayToggled(DayOfWeek.MONDAY)
        viewModel.save()
        advanceUntilIdle()

        val savedAlarm = repository.alarms.first().single()
        assertThat(savedAlarm.repeatRule).isEqualTo(
            RepeatRule.WeeklyInterval(
                startDate = LocalDate.of(2026, 5, 4),
                endDate = LocalDate.of(2027, 5, 4),
                intervalWeeks = 2,
                days = setOf(DayOfWeek.MONDAY),
            ),
        )
    }

    @Test
    fun `missing exact alarm permission keeps alarm saved and exposes permission action`() = runTest {
        val repository = FakeAlarmRepository(clock = clock, zoneId = zoneId)
        val scheduler = FakeAlarmScheduler().apply {
            nextScheduleResult = ScheduleResult.MissingPermission(
                android.Manifest.permission.SCHEDULE_EXACT_ALARM,
            )
        }
        val viewModel = AlarmEditorViewModel(
            alarmId = null,
            repository = repository,
            settingsRepository = FakeSettingsRepository(),
            schedulingUseCase = AlarmSchedulingUseCase(scheduler),
            clock = clock,
            zoneId = zoneId,
        )

        advanceUntilIdle()
        viewModel.onTitleChanged("Drink water")
        viewModel.onHourChanged("7")
        viewModel.onMinuteChanged("45")
        viewModel.onRepeatRuleChanged(EditorRepeatOption.DAILY)
        viewModel.save()
        advanceUntilIdle()

        assertThat(repository.alarms.first()).hasSize(1)
        assertThat(viewModel.uiState.value.savedAlarmId).isNull()
        assertThat(viewModel.uiState.value.exactAlarmPermissionRequired).isTrue()
        assertThat(viewModel.uiState.value.errorMessage)
            .contains(android.Manifest.permission.SCHEDULE_EXACT_ALARM)
    }
}
