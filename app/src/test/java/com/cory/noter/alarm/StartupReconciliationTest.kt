package com.cory.noter.alarm

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.cory.noter.data.alarm.AlarmDatabase
import com.cory.noter.data.alarm.AlarmDraft
import com.cory.noter.data.alarm.AlarmRepository
import com.cory.noter.data.alarm.RoomAlarmRepository
import com.cory.noter.domain.alarm.AlarmSource
import com.cory.noter.domain.alarm.NextTriggerCalculator
import com.cory.noter.domain.alarm.RepeatRule
import com.google.common.truth.Truth.assertThat
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StartupReconciliationTest {
    private val zoneId = ZoneId.of("Asia/Shanghai")
    private lateinit var database: AlarmDatabase
    private lateinit var repository: AlarmRepository
    private lateinit var clock: MutableClock
    private lateinit var scheduler: FakeAlarmScheduler
    private lateinit var startupReconciliation: StartupReconciliation

    @Before
    fun setUp() {
        clock = MutableClock(
            currentInstant = ZonedDateTime.of(2026, 4, 24, 7, 45, 0, 0, zoneId).toInstant(),
            zoneId = zoneId,
        )
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AlarmDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
        repository = RoomAlarmRepository(
            alarmDao = database.alarmDao(),
            clock = clock,
            nextTriggerCalculator = NextTriggerCalculator(),
            zoneIdProvider = { zoneId },
        )
        scheduler = FakeAlarmScheduler()
        startupReconciliation = StartupReconciliation(
            repository = repository,
            schedulingUseCase = AlarmSchedulingUseCase(scheduler),
            clock = clock,
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `enabled alarm with future trigger gets scheduled`() = runTest {
        val alarm = repository.create(
            alarmDraft(
                hour = 8,
                minute = 0,
                repeatRule = RepeatRule.Daily,
                enabled = true,
            ),
        )

        val results = startupReconciliation.reconcile()

        assertThat(scheduler.scheduledIds).contains(alarm.id)
        assertThat(results).isEqualTo(
            listOf(
                StartupReconciliationResult.Scheduled(
                    alarmId = alarm.id,
                    triggerAtMillis = alarm.nextTriggerAtMillis!!,
                    scheduleResult = ScheduleResult.Scheduled,
                ),
            ),
        )
    }

    @Test
    fun `disabled alarm is skipped`() = runTest {
        val alarm = repository.create(
            alarmDraft(
                hour = 8,
                minute = 0,
                repeatRule = RepeatRule.Daily,
                enabled = false,
            ),
        )

        val results = startupReconciliation.reconcile()

        assertThat(scheduler.scheduledIds).isEmpty()
        assertThat(results).isEqualTo(
            listOf(StartupReconciliationResult.SkippedDisabled(alarm.id)),
        )
    }

    @Test
    fun `expired one time alarm is deleted`() = runTest {
        val alarm = repository.create(
            alarmDraft(
                hour = 8,
                minute = 0,
                repeatRule = RepeatRule.Once(LocalDate.of(2026, 4, 24)),
                enabled = true,
            ),
        )
        clock.set(ZonedDateTime.of(2026, 4, 24, 8, 5, 0, 0, zoneId).toInstant())

        val results = startupReconciliation.reconcile()

        assertThat(repository.get(alarm.id)).isNull()
        assertThat(scheduler.scheduledIds).isEmpty()
        assertThat(results).isEqualTo(
            listOf(StartupReconciliationResult.DeletedExpiredOneTimeAlarm(alarm.id)),
        )
    }

    @Test
    fun `repeating alarm with stale next trigger is recalculated`() = runTest {
        val alarm = repository.create(
            alarmDraft(
                hour = 8,
                minute = 0,
                repeatRule = RepeatRule.Daily,
                enabled = true,
            ),
        )
        clock.set(ZonedDateTime.of(2026, 4, 24, 8, 5, 0, 0, zoneId).toInstant())

        val results = startupReconciliation.reconcile()
        val stored = repository.get(alarm.id)

        assertThat(stored == null).isFalse()
        val storedAlarm = checkNotNull(stored)
        assertThat(storedAlarm.nextTriggerAtMillis).isGreaterThan(alarm.nextTriggerAtMillis)
        assertThat(scheduler.scheduledAlarms[alarm.id]?.nextTriggerAtMillis).isEqualTo(
            storedAlarm.nextTriggerAtMillis,
        )
        assertThat(results).isEqualTo(
            listOf(
                StartupReconciliationResult.RecalculatedAndScheduled(
                    alarmId = alarm.id,
                    previousTriggerAtMillis = alarm.nextTriggerAtMillis!!,
                    recalculatedTriggerAtMillis = storedAlarm.nextTriggerAtMillis!!,
                    scheduleResult = ScheduleResult.Scheduled,
                ),
            ),
        )
    }

    private fun alarmDraft(
        hour: Int,
        minute: Int,
        repeatRule: RepeatRule,
        enabled: Boolean,
    ) = AlarmDraft(
        title = "Wake up",
        hour = hour,
        minute = minute,
        repeatRule = repeatRule,
        enabled = enabled,
        ringtoneUri = "content://settings/system/alarm_alert",
        source = AlarmSource.MANUAL,
        aiOriginalText = null,
    )

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
