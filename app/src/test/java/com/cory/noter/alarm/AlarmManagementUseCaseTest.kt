package com.cory.noter.alarm

import com.cory.noter.data.alarm.AlarmDraft
import com.cory.noter.data.alarm.AlarmRepository
import com.cory.noter.domain.alarm.Alarm
import com.cory.noter.domain.alarm.AlarmPauseMode
import com.cory.noter.domain.alarm.AlarmSource
import com.cory.noter.domain.alarm.NextTriggerCalculator
import com.cory.noter.domain.alarm.RepeatRule
import com.google.common.truth.Truth.assertThat
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class AlarmManagementUseCaseTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private lateinit var repository: RecordingAlarmRepository
    private lateinit var scheduler: FakeAlarmScheduler
    private lateinit var useCase: AlarmManagementUseCase
    private lateinit var clock: MutableClock

    @Before
    fun setUp() {
        repository = RecordingAlarmRepository()
        scheduler = FakeAlarmScheduler()
        clock = MutableClock(
            currentInstant = ZonedDateTime.of(2026, 4, 23, 9, 0, 0, 0, zone).toInstant(),
            zoneId = zone,
        )
        useCase = AlarmManagementUseCase(
            repository = repository,
            schedulingUseCase = AlarmSchedulingUseCase(scheduler),
            clock = clock,
            nextTriggerCalculator = NextTriggerCalculator(),
            zoneIdProvider = { zone },
        )
    }

    @Test
    fun `pause next occurrence on repeating alarm preserves current trigger and does not cancel schedule`() = runTest {
        val alarm = repeatingAlarm(
            nextTriggerAtMillis = ZonedDateTime.of(2026, 4, 24, 8, 0, 0, 0, zone).toInstant().toEpochMilli(),
        )
        repository.seed(alarm)

        val result = useCase.pauseNextOccurrence(alarm.id)

        val updated = (result as AlarmManagementResult.Updated).alarm
        assertThat(updated.enabled).isTrue()
        assertThat(updated.pauseMode).isEqualTo(AlarmPauseMode.NEXT_OCCURRENCE)
        assertThat(updated.nextTriggerAtMillis).isEqualTo(alarm.nextTriggerAtMillis)
        assertThat(updated.pausedOccurrenceAtMillis).isEqualTo(alarm.nextTriggerAtMillis)
        assertThat(scheduler.cancelledIds).isEmpty()
        assertThat(scheduler.scheduledAlarms).isEmpty()
        assertThat(repository.genericUpdates).isEmpty()
        assertThat(repository.managementUpdates).containsExactly(updated)
    }

    @Test
    fun `pause next occurrence rejects active alarm without trigger anchor`() = runTest {
        val alarm = repeatingAlarm(nextTriggerAtMillis = null)
        repository.seed(alarm)

        val result = useCase.pauseNextOccurrence(alarm.id)

        assertThat(result).isInstanceOf(AlarmManagementResult.InvalidState::class.java)
        assertThat((result as AlarmManagementResult.InvalidState).reason).contains("next trigger")
        assertThat(repository.managementUpdates).isEmpty()
    }

    @Test
    fun `pause indefinitely disables alarm clears anchor and cancels schedule`() = runTest {
        val alarm = repeatingAlarm()
        repository.seed(alarm)

        val result = useCase.pauseIndefinitely(alarm.id)

        val updated = (result as AlarmManagementResult.Updated).alarm
        assertThat(updated.enabled).isFalse()
        assertThat(updated.pauseMode).isEqualTo(AlarmPauseMode.INDEFINITE)
        assertThat(updated.nextTriggerAtMillis).isNull()
        assertThat(updated.pausedOccurrenceAtMillis).isNull()
        assertThat(scheduler.cancelledIds).containsExactly(alarm.id)
        assertThat(repository.managementUpdates).containsExactly(updated)
    }

    @Test
    fun `pause indefinitely returns scheduler failure without persisting`() = runTest {
        val alarm = repeatingAlarm()
        repository.seed(alarm)
        scheduler.nextCancelResult = ScheduleResult.Failed("cancel failed")

        val result = useCase.pauseIndefinitely(alarm.id)

        assertThat(result).isEqualTo(AlarmManagementResult.SchedulerFailed("cancel failed"))
        assertThat(repository.managementUpdates).isEmpty()
    }

    @Test
    fun `resume from paused next before anchor clears pause and preserves trigger`() = runTest {
        val anchor = ZonedDateTime.of(2026, 4, 24, 8, 0, 0, 0, zone).toInstant().toEpochMilli()
        val alarm = repeatingAlarm(
            nextTriggerAtMillis = anchor,
            pauseMode = AlarmPauseMode.NEXT_OCCURRENCE,
            pausedOccurrenceAtMillis = anchor,
        )
        repository.seed(alarm)

        val result = useCase.resume(alarm.id)

        val updated = (result as AlarmManagementResult.Updated).alarm
        assertThat(updated.enabled).isTrue()
        assertThat(updated.pauseMode).isEqualTo(AlarmPauseMode.NONE)
        assertThat(updated.nextTriggerAtMillis).isEqualTo(anchor)
        assertThat(updated.pausedOccurrenceAtMillis).isNull()
        assertThat(scheduler.scheduledAlarms[alarm.id]).isEqualTo(updated)
        assertThat(repository.managementUpdates).containsExactly(updated)
    }

    @Test
    fun `resume from indefinite recalculates future trigger and schedules`() = runTest {
        val alarm = repeatingAlarm(
            enabled = false,
            nextTriggerAtMillis = null,
            pauseMode = AlarmPauseMode.INDEFINITE,
        )
        repository.seed(alarm)

        val result = useCase.resume(alarm.id)

        val updated = (result as AlarmManagementResult.Updated).alarm
        assertThat(updated.enabled).isTrue()
        assertThat(updated.pauseMode).isEqualTo(AlarmPauseMode.NONE)
        assertThat(updated.nextTriggerAtMillis).isEqualTo(
            ZonedDateTime.of(2026, 4, 24, 8, 0, 0, 0, zone).toInstant().toEpochMilli(),
        )
        assertThat(scheduler.scheduledAlarms[alarm.id]).isEqualTo(updated)
    }

    @Test
    fun `pause next occurrence on one time alarm becomes indefinite and keeps record`() = runTest {
        val alarm = oneTimeAlarm()
        repository.seed(alarm)

        val result = useCase.pauseNextOccurrence(alarm.id)

        val updated = (result as AlarmManagementResult.Updated).alarm
        assertThat(updated.enabled).isFalse()
        assertThat(updated.pauseMode).isEqualTo(AlarmPauseMode.INDEFINITE)
        assertThat(updated.nextTriggerAtMillis).isNull()
        assertThat(repository.get(alarm.id)).isEqualTo(updated)
        assertThat(repository.deletedIds).isEmpty()
        assertThat(scheduler.cancelledIds).containsExactly(alarm.id)
    }

    @Test
    fun `resume future one time alarm schedules normally`() = runTest {
        val alarm = oneTimeAlarm(
            enabled = false,
            nextTriggerAtMillis = null,
            pauseMode = AlarmPauseMode.INDEFINITE,
        )
        repository.seed(alarm)

        val result = useCase.resume(alarm.id)

        val updated = (result as AlarmManagementResult.Updated).alarm
        assertThat(updated.nextTriggerAtMillis).isEqualTo(
            ZonedDateTime.of(2026, 4, 24, 8, 0, 0, 0, zone).toInstant().toEpochMilli(),
        )
        assertThat(scheduler.scheduledAlarms[alarm.id]).isEqualTo(updated)
    }

    @Test
    fun `resume expired one time alarm fails explicitly`() = runTest {
        val alarm = oneTimeAlarm(
            repeatRule = RepeatRule.Once(LocalDate.of(2026, 4, 22)),
            enabled = false,
            nextTriggerAtMillis = null,
            pauseMode = AlarmPauseMode.INDEFINITE,
        )
        repository.seed(alarm)

        val result = useCase.resume(alarm.id)

        assertThat(result).isInstanceOf(AlarmManagementResult.InvalidState::class.java)
        assertThat((result as AlarmManagementResult.InvalidState).reason).contains("expired one-time")
        assertThat(repository.managementUpdates).isEmpty()
        assertThat(scheduler.scheduledAlarms).isEmpty()
    }

    @Test
    fun `missing alarm returns missing alarm failure`() = runTest {
        val result = useCase.pauseNextOccurrence(404)

        assertThat(result).isEqualTo(AlarmManagementResult.MissingAlarm)
    }

    @Test
    fun `resume missing permission persists resumed state before surfacing permission`() = runTest {
        val alarm = repeatingAlarm(
            enabled = false,
            nextTriggerAtMillis = null,
            pauseMode = AlarmPauseMode.INDEFINITE,
        )
        repository.seed(alarm)
        scheduler.nextScheduleResult = ScheduleResult.MissingPermission("android.permission.SCHEDULE_EXACT_ALARM")

        val result = useCase.resume(alarm.id)

        assertThat(result).isEqualTo(
            AlarmManagementResult.MissingSchedulingPermission("android.permission.SCHEDULE_EXACT_ALARM"),
        )
        val updated = repository.get(alarm.id)!!
        assertThat(updated.enabled).isTrue()
        assertThat(updated.pauseMode).isEqualTo(AlarmPauseMode.NONE)
        assertThat(updated.pausedOccurrenceAtMillis).isNull()
        assertThat(updated.nextTriggerAtMillis).isEqualTo(
            ZonedDateTime.of(2026, 4, 24, 8, 0, 0, 0, zone).toInstant().toEpochMilli(),
        )
        assertThat(repository.managementUpdates).containsExactly(updated)
    }

    @Test
    fun `consume due paused next advances repeating alarm exactly once`() = runTest {
        val anchor = ZonedDateTime.of(2026, 4, 24, 8, 0, 0, 0, zone).toInstant().toEpochMilli()
        val alarm = repeatingAlarm(
            nextTriggerAtMillis = anchor,
            pauseMode = AlarmPauseMode.NEXT_OCCURRENCE,
            pausedOccurrenceAtMillis = anchor,
        )
        repository.seed(alarm)

        val result = useCase.consumeDuePausedNext(alarm.id, anchor)

        val updated = (result as AlarmManagementResult.Updated).alarm
        assertThat(updated.pauseMode).isEqualTo(AlarmPauseMode.NONE)
        assertThat(updated.pausedOccurrenceAtMillis).isNull()
        assertThat(updated.nextTriggerAtMillis).isEqualTo(
            ZonedDateTime.of(2026, 4, 25, 8, 0, 0, 0, zone).toInstant().toEpochMilli(),
        )
        assertThat(scheduler.scheduledAlarms[alarm.id]).isEqualTo(updated)
    }

    @Test
    fun `consume stale paused next advances repeating alarm from current time`() = runTest {
        val anchor = ZonedDateTime.of(2026, 4, 24, 8, 0, 0, 0, zone).toInstant().toEpochMilli()
        val alarm = repeatingAlarm(
            nextTriggerAtMillis = anchor,
            pauseMode = AlarmPauseMode.NEXT_OCCURRENCE,
            pausedOccurrenceAtMillis = anchor,
        )
        repository.seed(alarm)
        clock.set(ZonedDateTime.of(2026, 4, 26, 9, 0, 0, 0, zone).toInstant())

        val result = useCase.consumeDuePausedNext(alarm.id, anchor)

        val updated = (result as AlarmManagementResult.Updated).alarm
        assertThat(updated.pauseMode).isEqualTo(AlarmPauseMode.NONE)
        assertThat(updated.pausedOccurrenceAtMillis).isNull()
        assertThat(updated.nextTriggerAtMillis).isEqualTo(
            ZonedDateTime.of(2026, 4, 27, 8, 0, 0, 0, zone).toInstant().toEpochMilli(),
        )
        assertThat(scheduler.scheduledAlarms[alarm.id]).isEqualTo(updated)
    }

    @Test
    fun `consume final paused next clears checkpoint into indefinite pause`() = runTest {
        val anchor = ZonedDateTime.of(2026, 4, 24, 8, 0, 0, 0, zone).toInstant().toEpochMilli()
        val alarm = repeatingAlarm(
            repeatRule = RepeatRule.WeeklyInterval(
                startDate = LocalDate.of(2026, 4, 24),
                endDate = LocalDate.of(2026, 4, 24),
                intervalWeeks = 1,
                days = setOf(DayOfWeek.FRIDAY),
            ),
            nextTriggerAtMillis = anchor,
            pauseMode = AlarmPauseMode.NEXT_OCCURRENCE,
            pausedOccurrenceAtMillis = anchor,
        )
        repository.seed(alarm)
        clock.set(ZonedDateTime.of(2026, 4, 24, 8, 0, 1, 0, zone).toInstant())

        val result = useCase.consumeDuePausedNext(alarm.id, anchor)

        val updated = (result as AlarmManagementResult.Updated).alarm
        assertThat(updated.enabled).isFalse()
        assertThat(updated.pauseMode).isEqualTo(AlarmPauseMode.INDEFINITE)
        assertThat(updated.pausedOccurrenceAtMillis).isNull()
        assertThat(updated.nextTriggerAtMillis).isNull()
        assertThat(scheduler.scheduledAlarms).isEmpty()
        assertThat(repository.managementUpdates).containsExactly(updated)
    }

    @Test
    fun `consume due paused next rejects stale or mismatched trigger without mutating`() = runTest {
        val anchor = ZonedDateTime.of(2026, 4, 24, 8, 0, 0, 0, zone).toInstant().toEpochMilli()
        val alarm = repeatingAlarm(
            nextTriggerAtMillis = anchor,
            pauseMode = AlarmPauseMode.NEXT_OCCURRENCE,
            pausedOccurrenceAtMillis = anchor,
        )
        repository.seed(alarm)

        val result = useCase.consumeDuePausedNext(alarm.id, anchor - 1_000)

        assertThat(result).isEqualTo(AlarmManagementResult.StaleOrMismatchedTrigger(alarm.id))
        assertThat(repository.managementUpdates).isEmpty()
        assertThat(scheduler.scheduledAlarms).isEmpty()
    }

    private fun repeatingAlarm(
        id: Long = 1L,
        repeatRule: RepeatRule = RepeatRule.Daily,
        enabled: Boolean = true,
        nextTriggerAtMillis: Long? = ZonedDateTime.of(2026, 4, 24, 8, 0, 0, 0, zone).toInstant().toEpochMilli(),
        pauseMode: AlarmPauseMode = AlarmPauseMode.NONE,
        pausedOccurrenceAtMillis: Long? = null,
    ): Alarm = alarm(
        id = id,
        repeatRule = repeatRule,
        enabled = enabled,
        nextTriggerAtMillis = nextTriggerAtMillis,
        pauseMode = pauseMode,
        pausedOccurrenceAtMillis = pausedOccurrenceAtMillis,
    )

    private fun oneTimeAlarm(
        id: Long = 2L,
        repeatRule: RepeatRule.Once = RepeatRule.Once(LocalDate.of(2026, 4, 24)),
        enabled: Boolean = true,
        nextTriggerAtMillis: Long? = ZonedDateTime.of(2026, 4, 24, 8, 0, 0, 0, zone).toInstant().toEpochMilli(),
        pauseMode: AlarmPauseMode = AlarmPauseMode.NONE,
        pausedOccurrenceAtMillis: Long? = null,
    ): Alarm = alarm(
        id = id,
        repeatRule = repeatRule,
        enabled = enabled,
        nextTriggerAtMillis = nextTriggerAtMillis,
        pauseMode = pauseMode,
        pausedOccurrenceAtMillis = pausedOccurrenceAtMillis,
    )

    private fun alarm(
        id: Long,
        repeatRule: RepeatRule,
        enabled: Boolean,
        nextTriggerAtMillis: Long?,
        pauseMode: AlarmPauseMode,
        pausedOccurrenceAtMillis: Long?,
    ): Alarm = Alarm(
        id = id,
        title = "Morning",
        hour = 8,
        minute = 0,
        repeatRule = repeatRule,
        enabled = enabled,
        ringtoneUri = "content://settings/system/alarm_alert",
        source = AlarmSource.MANUAL,
        aiOriginalText = null,
        nextTriggerAtMillis = nextTriggerAtMillis,
        createdAtMillis = 1_776_800_000_000,
        updatedAtMillis = 1_776_800_000_000,
        pauseMode = pauseMode,
        pausedOccurrenceAtMillis = pausedOccurrenceAtMillis,
    )

    private class RecordingAlarmRepository : AlarmRepository {
        private val state = MutableStateFlow<List<Alarm>>(emptyList())
        val genericUpdates = mutableListOf<Alarm>()
        val managementUpdates = mutableListOf<Alarm>()
        val deletedIds = mutableListOf<Long>()

        override val alarms: Flow<List<Alarm>> = state

        override suspend fun get(id: Long): Alarm? = state.value.firstOrNull { it.id == id }

        override suspend fun create(draft: AlarmDraft): Alarm = error("Not used")

        override suspend fun update(alarm: Alarm): Alarm {
            genericUpdates += alarm
            return store(alarm)
        }

        override suspend fun updateFromManagement(alarm: Alarm): Alarm {
            managementUpdates += alarm
            return store(alarm)
        }

        override suspend fun enable(id: Long): Alarm? = error("Not used")

        override suspend fun disable(id: Long): Alarm? = error("Not used")

        override suspend fun delete(id: Long) {
            deletedIds += id
            state.update { alarms -> alarms.filterNot { it.id == id } }
        }

        fun seed(alarm: Alarm) {
            state.value = listOf(alarm)
        }

        private fun store(alarm: Alarm): Alarm {
            state.update { alarms ->
                alarms.map { existing -> if (existing.id == alarm.id) alarm else existing }
            }
            return alarm
        }
    }

    private class MutableClock(
        private var currentInstant: Instant,
        private val zoneId: ZoneId,
    ) : Clock() {
        override fun getZone(): ZoneId = zoneId

        override fun withZone(zone: ZoneId): Clock = MutableClock(currentInstant, zone)

        override fun instant(): Instant = currentInstant

        fun set(newInstant: Instant) {
            currentInstant = newInstant
        }
    }
}
