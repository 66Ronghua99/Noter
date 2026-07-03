package com.cory.noter.ui.alarm_list

import com.cory.noter.alarm.AlarmSchedulingUseCase
import com.cory.noter.alarm.AlarmManagementUseCase
import com.cory.noter.alarm.FakeAlarmScheduler
import com.cory.noter.domain.alarm.AlarmPauseMode
import com.cory.noter.domain.alarm.Alarm
import com.cory.noter.domain.alarm.AlarmSource
import com.cory.noter.domain.alarm.NextTriggerCalculator
import com.cory.noter.domain.alarm.RepeatRule
import com.cory.noter.ui.FakeAlarmRepository
import com.cory.noter.ui.MainDispatcherRule
import com.google.common.truth.Truth.assertThat
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class AlarmListViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val zoneId = ZoneId.of("Asia/Shanghai")
    private val clock = Clock.fixed(Instant.parse("2026-04-23T01:00:00Z"), zoneId)

    @Test
    fun `empty alarm list exposes empty state`() = runTest {
        val repository = FakeAlarmRepository(clock = clock, zoneId = zoneId)
        val viewModel = AlarmListViewModel(
            repository = repository,
            schedulingUseCase = AlarmSchedulingUseCase(FakeAlarmScheduler()),
            managementUseCase = managementUseCase(repository, FakeAlarmScheduler()),
        )

        advanceUntilIdle()

        assertThat(viewModel.uiState.value.alarms).isEmpty()
        assertThat(viewModel.uiState.value.errorMessage).isNull()
    }

    @Test
    fun `enable toggle schedules updated alarm`() = runTest {
        val repository = FakeAlarmRepository(clock = clock, zoneId = zoneId)
        val scheduler = FakeAlarmScheduler()
        repository.seed(
            Alarm(
                id = 7L,
                title = "Take medicine",
                hour = 8,
                minute = 30,
                repeatRule = RepeatRule.Once(LocalDate.of(2026, 4, 24)),
                enabled = false,
                ringtoneUri = "content://settings/system/alarm_alert",
                source = AlarmSource.MANUAL,
                aiOriginalText = null,
                nextTriggerAtMillis = null,
                createdAtMillis = clock.millis(),
                updatedAtMillis = clock.millis(),
            ),
        )
        val viewModel = AlarmListViewModel(
            repository = repository,
            schedulingUseCase = AlarmSchedulingUseCase(scheduler),
            managementUseCase = managementUseCase(repository, scheduler),
        )

        viewModel.onAlarmEnabledChanged(alarmId = 7L, enabled = true)
        advanceUntilIdle()

        assertThat(repository.get(7L)?.enabled).isTrue()
        assertThat(scheduler.scheduledIds).contains(7L)
        assertThat(viewModel.uiState.value.errorMessage).isNull()
    }

    @Test
    fun `turning off active alarm opens pause dialog without mutating state`() = runTest {
        val repository = FakeAlarmRepository(clock = clock, zoneId = zoneId)
        repository.seed(activeDailyAlarm())
        val viewModel = AlarmListViewModel(
            repository = repository,
            schedulingUseCase = AlarmSchedulingUseCase(FakeAlarmScheduler()),
            managementUseCase = managementUseCase(repository, FakeAlarmScheduler()),
        )
        advanceUntilIdle()

        viewModel.onAlarmEnabledChanged(alarmId = 7L, enabled = false)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.pauseChoiceDialog?.alarmId).isEqualTo(7L)
        assertThat(repository.get(7L)?.pauseMode).isEqualTo(AlarmPauseMode.NONE)
        assertThat(repository.get(7L)?.enabled).isTrue()
    }

    @Test
    fun `canceling pause dialog preserves alarm state`() = runTest {
        val repository = FakeAlarmRepository(clock = clock, zoneId = zoneId)
        repository.seed(activeDailyAlarm())
        val viewModel = AlarmListViewModel(
            repository = repository,
            schedulingUseCase = AlarmSchedulingUseCase(FakeAlarmScheduler()),
            managementUseCase = managementUseCase(repository, FakeAlarmScheduler()),
        )
        advanceUntilIdle()

        viewModel.onAlarmEnabledChanged(alarmId = 7L, enabled = false)
        viewModel.onCancelPauseChoice()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.pauseChoiceDialog).isNull()
        assertThat(repository.get(7L)?.pauseMode).isEqualTo(AlarmPauseMode.NONE)
        assertThat(repository.get(7L)?.enabled).isTrue()
    }

    @Test
    fun `confirming pause next occurrence pauses through management use case`() = runTest {
        val repository = FakeAlarmRepository(clock = clock, zoneId = zoneId)
        repository.seed(activeDailyAlarm())
        val scheduler = FakeAlarmScheduler()
        val viewModel = AlarmListViewModel(
            repository = repository,
            schedulingUseCase = AlarmSchedulingUseCase(scheduler),
            managementUseCase = managementUseCase(repository, scheduler),
        )
        advanceUntilIdle()

        viewModel.onAlarmEnabledChanged(alarmId = 7L, enabled = false)
        viewModel.onConfirmPauseNextOccurrence()
        advanceUntilIdle()

        val stored = repository.get(7L)!!
        assertThat(stored.pauseMode).isEqualTo(AlarmPauseMode.NEXT_OCCURRENCE)
        assertThat(stored.pausedOccurrenceAtMillis).isEqualTo(stored.nextTriggerAtMillis)
        assertThat(viewModel.uiState.value.pauseChoiceDialog).isNull()
        assertThat(viewModel.uiState.value.alarms.single().pauseStatus)
            .isInstanceOf(AlarmPauseStatusUiModel.PausedNext::class.java)
    }

    @Test
    fun `confirming pause indefinitely pauses through management use case`() = runTest {
        val repository = FakeAlarmRepository(clock = clock, zoneId = zoneId)
        repository.seed(activeDailyAlarm())
        val scheduler = FakeAlarmScheduler()
        val viewModel = AlarmListViewModel(
            repository = repository,
            schedulingUseCase = AlarmSchedulingUseCase(scheduler),
            managementUseCase = managementUseCase(repository, scheduler),
        )
        advanceUntilIdle()

        viewModel.onAlarmEnabledChanged(alarmId = 7L, enabled = false)
        viewModel.onConfirmPauseIndefinitely()
        advanceUntilIdle()

        val stored = repository.get(7L)!!
        assertThat(stored.pauseMode).isEqualTo(AlarmPauseMode.INDEFINITE)
        assertThat(stored.enabled).isFalse()
        assertThat(scheduler.cancelledIds).contains(7L)
        assertThat(viewModel.uiState.value.alarms.single().pauseStatus)
            .isEqualTo(AlarmPauseStatusUiModel.PausedIndefinitely)
    }

    @Test
    fun `turning on paused alarm resumes directly without dialog`() = runTest {
        val repository = FakeAlarmRepository(clock = clock, zoneId = zoneId)
        repository.seed(
            activeDailyAlarm().copy(
                enabled = false,
                nextTriggerAtMillis = null,
                pauseMode = AlarmPauseMode.INDEFINITE,
            ),
        )
        val scheduler = FakeAlarmScheduler()
        val viewModel = AlarmListViewModel(
            repository = repository,
            schedulingUseCase = AlarmSchedulingUseCase(scheduler),
            managementUseCase = managementUseCase(repository, scheduler),
        )
        advanceUntilIdle()

        viewModel.onAlarmEnabledChanged(alarmId = 7L, enabled = true)
        advanceUntilIdle()

        val stored = repository.get(7L)!!
        assertThat(stored.pauseMode).isEqualTo(AlarmPauseMode.NONE)
        assertThat(stored.enabled).isTrue()
        assertThat(scheduler.scheduledIds).contains(7L)
        assertThat(viewModel.uiState.value.pauseChoiceDialog).isNull()
    }

    @Test
    fun `requesting delete opens confirmation without deleting alarm`() = runTest {
        val repository = FakeAlarmRepository(clock = clock, zoneId = zoneId)
        repository.seed(activeDailyAlarm())
        val viewModel = AlarmListViewModel(
            repository = repository,
            schedulingUseCase = AlarmSchedulingUseCase(FakeAlarmScheduler()),
            managementUseCase = managementUseCase(repository, FakeAlarmScheduler()),
        )
        advanceUntilIdle()

        viewModel.onDeleteAlarmRequested(alarmId = 7L)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.deleteConfirmDialog?.alarmId).isEqualTo(7L)
        assertThat(viewModel.uiState.value.deleteConfirmDialog?.alarmTitle).isEqualTo("Take medicine")
        assertThat(repository.get(7L)).isNotNull()
    }

    @Test
    fun `canceling delete confirmation preserves alarm`() = runTest {
        val repository = FakeAlarmRepository(clock = clock, zoneId = zoneId)
        repository.seed(activeDailyAlarm())
        val viewModel = AlarmListViewModel(
            repository = repository,
            schedulingUseCase = AlarmSchedulingUseCase(FakeAlarmScheduler()),
            managementUseCase = managementUseCase(repository, FakeAlarmScheduler()),
        )
        advanceUntilIdle()

        viewModel.onDeleteAlarmRequested(alarmId = 7L)
        viewModel.onCancelDeleteConfirmation()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.deleteConfirmDialog).isNull()
        assertThat(repository.get(7L)).isNotNull()
    }

    @Test
    fun `confirming delete removes alarm and cancels schedule`() = runTest {
        val repository = FakeAlarmRepository(clock = clock, zoneId = zoneId)
        repository.seed(activeDailyAlarm())
        val scheduler = FakeAlarmScheduler()
        val viewModel = AlarmListViewModel(
            repository = repository,
            schedulingUseCase = AlarmSchedulingUseCase(scheduler),
            managementUseCase = managementUseCase(repository, scheduler),
        )
        advanceUntilIdle()

        viewModel.onDeleteAlarmRequested(alarmId = 7L)
        viewModel.onConfirmDeleteAlarm()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.deleteConfirmDialog).isNull()
        assertThat(repository.get(7L)).isNull()
        assertThat(scheduler.cancelledIds).contains(7L)
        assertThat(viewModel.uiState.value.errorMessage).isNull()
    }

    @Test
    fun `paused row exposes user facing switch state and status`() = runTest {
        val repository = FakeAlarmRepository(clock = clock, zoneId = zoneId)
        repository.seed(
            activeDailyAlarm().copy(
                pauseMode = AlarmPauseMode.NEXT_OCCURRENCE,
                pausedOccurrenceAtMillis = 1_777_020_000_000L,
            ),
        )
        val viewModel = AlarmListViewModel(
            repository = repository,
            schedulingUseCase = AlarmSchedulingUseCase(FakeAlarmScheduler()),
            managementUseCase = managementUseCase(repository, FakeAlarmScheduler()),
        )
        advanceUntilIdle()

        val row = viewModel.uiState.value.alarms.single()

        assertThat(row.enabled).isFalse()
        assertThat(row.pauseStatus).isEqualTo(
            AlarmPauseStatusUiModel.PausedNext(1_777_020_000_000L),
        )
    }

    private fun managementUseCase(
        repository: FakeAlarmRepository,
        scheduler: FakeAlarmScheduler,
    ) = AlarmManagementUseCase(
        repository = repository,
        schedulingUseCase = AlarmSchedulingUseCase(scheduler),
        clock = clock,
        nextTriggerCalculator = NextTriggerCalculator(),
        zoneIdProvider = { zoneId },
    )

    private fun activeDailyAlarm() = Alarm(
        id = 7L,
        title = "Take medicine",
        hour = 8,
        minute = 30,
        repeatRule = RepeatRule.Daily,
        enabled = true,
        ringtoneUri = "content://settings/system/alarm_alert",
        source = AlarmSource.MANUAL,
        aiOriginalText = null,
        nextTriggerAtMillis = 1_777_020_000_000L,
        createdAtMillis = clock.millis(),
        updatedAtMillis = clock.millis(),
    )
}
