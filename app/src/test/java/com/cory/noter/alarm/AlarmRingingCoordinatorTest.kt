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
class AlarmRingingCoordinatorTest {
    private val zoneId = ZoneId.of("Asia/Shanghai")
    private lateinit var database: AlarmDatabase
    private lateinit var repository: AlarmRepository
    private lateinit var clock: MutableClock
    private lateinit var fakeScheduler: FakeAlarmScheduler
    private lateinit var coordinator: AlarmRingingCoordinator

    @Before
    fun setUp() {
        clock = MutableClock(
            currentInstant = ZonedDateTime.of(2026, 4, 23, 7, 45, 0, 0, zoneId).toInstant(),
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
        fakeScheduler = FakeAlarmScheduler()
        coordinator = AlarmRingingCoordinator(
            repository = repository,
            schedulingUseCase = AlarmSchedulingUseCase(fakeScheduler),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `stopping once alarm deletes it`() = runTest {
        val once = repository.create(
            alarmDraft(
                hour = 8,
                minute = 0,
                repeatRule = RepeatRule.Once(LocalDate.of(2026, 4, 23)),
            ),
        )
        clock.set(ZonedDateTime.of(2026, 4, 23, 8, 1, 0, 0, zoneId).toInstant())

        coordinator.stopRinging(once.id)

        assertThat(repository.get(once.id)).isNull()
        assertThat(fakeScheduler.cancelledIds).contains(once.id)
    }

    @Test
    fun `stopping repeating alarm keeps and reschedules it`() = runTest {
        val repeating = repository.create(
            alarmDraft(
                hour = 8,
                minute = 0,
                repeatRule = RepeatRule.Daily,
            ),
        )
        clock.set(ZonedDateTime.of(2026, 4, 23, 8, 1, 0, 0, zoneId).toInstant())

        coordinator.stopRinging(repeating.id)

        val stored = repository.get(repeating.id)

        assertThat(stored).isNotNull()
        assertThat(stored!!.nextTriggerAtMillis).isGreaterThan(repeating.nextTriggerAtMillis)
        assertThat(fakeScheduler.scheduledIds).contains(repeating.id)
    }

    private fun alarmDraft(
        hour: Int,
        minute: Int,
        repeatRule: RepeatRule,
    ) = AlarmDraft(
        title = "Wake up",
        hour = hour,
        minute = minute,
        repeatRule = repeatRule,
        enabled = true,
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
