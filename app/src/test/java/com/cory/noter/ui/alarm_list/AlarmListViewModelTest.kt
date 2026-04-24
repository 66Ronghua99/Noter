package com.cory.noter.ui.alarm_list

import com.cory.noter.alarm.AlarmSchedulingUseCase
import com.cory.noter.alarm.FakeAlarmScheduler
import com.cory.noter.domain.alarm.Alarm
import com.cory.noter.domain.alarm.AlarmSource
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
        )

        viewModel.onAlarmEnabledChanged(alarmId = 7L, enabled = true)
        advanceUntilIdle()

        assertThat(repository.get(7L)?.enabled).isTrue()
        assertThat(scheduler.scheduledIds).contains(7L)
        assertThat(viewModel.uiState.value.errorMessage).isNull()
    }
}
