package com.cory.noter.alarm

import android.content.Intent
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.cory.noter.data.alarm.AlarmDatabase
import com.cory.noter.data.alarm.AlarmDraft
import com.cory.noter.data.alarm.AlarmRepository
import com.cory.noter.data.alarm.RoomAlarmRepository
import com.cory.noter.domain.alarm.AlarmSource
import com.cory.noter.domain.alarm.NextTriggerCalculator
import com.cory.noter.domain.alarm.RepeatRule
import com.cory.noter.ui.ringing.RingingActivity
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
import org.robolectric.Robolectric
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

    @Test
    fun `stopping repeating alarm returns failure when reschedule fails`() = runTest {
        val repeating = repository.create(
            alarmDraft(
                hour = 8,
                minute = 0,
                repeatRule = RepeatRule.Daily,
            ),
        )
        fakeScheduler.nextScheduleResult = ScheduleResult.Failed("scheduler unavailable")
        clock.set(ZonedDateTime.of(2026, 4, 23, 8, 1, 0, 0, zoneId).toInstant())

        val result = coordinator.stopRinging(repeating.id)

        assertThat(result).isEqualTo(AlarmStopResult.Failed("scheduler unavailable"))
        assertThat(repository.get(repeating.id)).isNotNull()
        assertThat(fakeScheduler.scheduledIds).doesNotContain(repeating.id)
    }

    @Test
    fun `stopping once alarm returns failure when cancellation fails and keeps alarm`() = runTest {
        val once = repository.create(
            alarmDraft(
                hour = 8,
                minute = 0,
                repeatRule = RepeatRule.Once(LocalDate.of(2026, 4, 23)),
            ),
        )
        fakeScheduler.nextCancelResult = ScheduleResult.Failed("cancel failed")
        clock.set(ZonedDateTime.of(2026, 4, 23, 8, 1, 0, 0, zoneId).toInstant())

        val result = coordinator.stopRinging(once.id)

        assertThat(result).isEqualTo(AlarmStopResult.Failed("cancel failed"))
        assertThat(repository.get(once.id)).isNotNull()
    }

    @Test
    fun `stopping missing alarm returns missing alarm result`() = runTest {
        val result = coordinator.stopRinging(999L)

        assertThat(result).isEqualTo(AlarmStopResult.MissingAlarm)
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

@RunWith(RobolectricTestRunner::class)
class RingingServicePolicyTest {
    @Test
    fun `failed stop result keeps failure path visible`() {
        val handling = RingingService.stopHandlingFor(
            AlarmStopResult.Failed("cancel failed"),
        )

        assertThat(handling).isEqualTo(
            RingingService.StopHandling.ShowFailure("cancel failed"),
        )
    }

    @Test
    fun `full screen intent is gated off when android 14 plus disallows it`() {
        val allowed = RingingService.shouldUseFullScreenIntent(
            sdkInt = 34,
            managerAllowsFullScreenIntent = false,
        )

        assertThat(allowed).isFalse()
    }
}

@RunWith(RobolectricTestRunner::class)
class RingingActivityTest {
    @Test
    fun `on new intent updates active alarm id and title`() {
        val firstIntent = RingingActivity.createIntent(
            context = ApplicationProvider.getApplicationContext(),
            alarmId = 11L,
            alarmTitle = "First",
        )
        val controller = Robolectric.buildActivity(RingingActivity::class.java, firstIntent).setup()
        val activity = controller.get()

        controller.newIntent(
            RingingActivity.createIntent(
                context = ApplicationProvider.getApplicationContext(),
                alarmId = 22L,
                alarmTitle = "Second",
            ),
        )

        assertThat(activity.currentAlarmForTest().alarmId).isEqualTo(22L)
        assertThat(activity.currentAlarmForTest().title).isEqualTo("Second")
        assertThat(
            activity.currentStopIntentForTest().getLongExtra("alarm_id", -1L),
        ).isEqualTo(22L)
    }
}
