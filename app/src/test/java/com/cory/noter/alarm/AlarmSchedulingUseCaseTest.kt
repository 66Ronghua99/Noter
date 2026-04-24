package com.cory.noter.alarm

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.cory.noter.domain.alarm.Alarm
import com.cory.noter.domain.alarm.AlarmSource
import com.cory.noter.domain.alarm.RepeatRule
import com.cory.noter.permissions.PermissionStatusReader
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AlarmSchedulingUseCaseTest {
    private val fakeScheduler = FakeAlarmScheduler()
    private val schedulingUseCase = AlarmSchedulingUseCase(fakeScheduler)

    @Test
    fun `enabling alarm schedules next trigger`() {
        val alarm = alarm(enabled = true, nextTriggerAtMillis = 1_776_904_200_000)

        val result = schedulingUseCase.syncSchedule(alarm)

        assertThat(result).isEqualTo(ScheduleResult.Scheduled)
        assertThat(fakeScheduler.scheduledIds).contains(alarm.id)
    }

    @Test
    fun `disabling alarm cancels existing schedule`() {
        val alarm = alarm(enabled = false, nextTriggerAtMillis = null)

        val result = schedulingUseCase.syncSchedule(alarm)

        assertThat(result).isEqualTo(ScheduleResult.Cancelled)
        assertThat(fakeScheduler.cancelledIds).containsExactly(alarm.id)
    }

    @Test
    fun `deleting alarm cancels existing schedule`() {
        val alarmId = 42L

        val result = schedulingUseCase.cancel(alarmId)

        assertThat(result).isEqualTo(ScheduleResult.Cancelled)
        assertThat(fakeScheduler.cancelledIds).containsExactly(alarmId)
    }

    @Test
    fun `editing enabled alarm reschedules current snapshot`() {
        val original = alarm(
            hour = 7,
            minute = 30,
            nextTriggerAtMillis = 1_776_900_600_000,
        )
        schedulingUseCase.syncSchedule(original)
        val updated = original.copy(
            hour = 8,
            minute = 15,
            nextTriggerAtMillis = 1_776_904_500_000,
            updatedAtMillis = original.updatedAtMillis + 1_000,
        )

        val result = schedulingUseCase.syncSchedule(updated)

        assertThat(result).isEqualTo(ScheduleResult.Scheduled)
        assertThat(fakeScheduler.scheduledAlarms[updated.id]).isEqualTo(updated)
    }

    @Test
    fun `repeating alarm schedules next occurrence after ringing updates trigger`() {
        val original = repeatingAlarm(nextTriggerAtMillis = 1_776_900_600_000)
        schedulingUseCase.syncSchedule(original)
        val rescheduled = original.copy(
            nextTriggerAtMillis = 1_776_987_000_000,
            updatedAtMillis = original.updatedAtMillis + 1_000,
        )

        val result = schedulingUseCase.syncSchedule(rescheduled)

        assertThat(result).isEqualTo(ScheduleResult.Scheduled)
        assertThat(fakeScheduler.scheduledAlarms[rescheduled.id]?.nextTriggerAtMillis)
            .isEqualTo(1_776_987_000_000)
    }

    @Test
    fun `missing exact alarm permission returns an error`() {
        val scheduler = AndroidAlarmScheduler(
            context = ApplicationProvider.getApplicationContext(),
            permissionStatusReader = PermissionStatusReader { false },
            nowProvider = { 1_000L },
        )

        val result = scheduler.schedule(
            alarm(enabled = true, nextTriggerAtMillis = 61_000L),
        )

        assertThat(result).isEqualTo(
            ScheduleResult.MissingPermission(Manifest.permission.SCHEDULE_EXACT_ALARM),
        )
    }

    @Test
    fun `failed schedule does not record scheduled alarm side effect`() {
        fakeScheduler.nextScheduleResult = ScheduleResult.Failed("scheduler unavailable")

        val result = schedulingUseCase.syncSchedule(alarm(enabled = true, nextTriggerAtMillis = 1_776_904_200_000))

        assertThat(result).isEqualTo(ScheduleResult.Failed("scheduler unavailable"))
        assertThat(fakeScheduler.scheduledAlarms).isEmpty()
    }

    @Test
    fun `failed cancel does not record cancellation side effect`() {
        val alarm = alarm(enabled = true, nextTriggerAtMillis = 1_776_904_200_000)
        fakeScheduler.scheduledAlarms[alarm.id] = alarm
        fakeScheduler.nextCancelResult = ScheduleResult.Failed("cancel failed")

        val result = schedulingUseCase.cancel(alarm.id)

        assertThat(result).isEqualTo(ScheduleResult.Failed("cancel failed"))
        assertThat(fakeScheduler.cancelledIds).isEmpty()
        assertThat(fakeScheduler.scheduledAlarms[alarm.id]).isEqualTo(alarm)
    }

    private fun alarm(
        id: Long = 42L,
        hour: Int = 8,
        minute: Int = 0,
        repeatRule: RepeatRule = RepeatRule.Once(LocalDate.of(2026, 4, 24)),
        enabled: Boolean = true,
        nextTriggerAtMillis: Long? = 1_776_904_200_000,
    ): Alarm = Alarm(
        id = id,
        title = "Take medicine",
        hour = hour,
        minute = minute,
        repeatRule = repeatRule,
        enabled = enabled,
        ringtoneUri = "content://settings/system/alarm_alert",
        source = AlarmSource.MANUAL,
        aiOriginalText = null,
        nextTriggerAtMillis = nextTriggerAtMillis,
        createdAtMillis = 1_776_800_000_000,
        updatedAtMillis = 1_776_800_000_000,
    )

    private fun repeatingAlarm(
        id: Long = 99L,
        nextTriggerAtMillis: Long,
    ): Alarm = alarm(
        id = id,
        repeatRule = RepeatRule.Daily,
        nextTriggerAtMillis = nextTriggerAtMillis,
    )
}

@RunWith(RobolectricTestRunner::class)
class AndroidAlarmSchedulerTest {
    @Test
    fun `schedule creates pending intent with stable request code`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scheduler = AndroidAlarmScheduler(
            context = context,
            permissionStatusReader = PermissionStatusReader { true },
            nowProvider = { 1_000L },
        )
        val alarm = Alarm(
            id = 17L,
            title = "Workout",
            hour = 6,
            minute = 30,
            repeatRule = RepeatRule.Daily,
            enabled = true,
            ringtoneUri = "content://settings/system/alarm_alert",
            source = AlarmSource.MANUAL,
            aiOriginalText = null,
            nextTriggerAtMillis = 61_000L,
            createdAtMillis = 1_776_800_000_000,
            updatedAtMillis = 1_776_800_000_000,
        )

        val result = scheduler.schedule(alarm)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            scheduler.pendingIntentRequestCode(alarm.id),
            scheduler.alarmIntent(alarm.id),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )

        assertThat(result).isEqualTo(ScheduleResult.Scheduled)
        assertThat(scheduler.pendingIntentRequestCode(alarm.id)).isEqualTo(17)
        assertThat(pendingIntent).isNotNull()
    }

    @Test
    fun `schedule rejects stale past trigger`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scheduler = AndroidAlarmScheduler(
            context = context,
            permissionStatusReader = PermissionStatusReader { true },
            nowProvider = { 1_000L },
        )
        val alarm = Alarm(
            id = 31L,
            title = "Past due",
            hour = 6,
            minute = 30,
            repeatRule = RepeatRule.Daily,
            enabled = true,
            ringtoneUri = "content://settings/system/alarm_alert",
            source = AlarmSource.MANUAL,
            aiOriginalText = null,
            nextTriggerAtMillis = 1_000L,
            createdAtMillis = 1_776_800_000_000,
            updatedAtMillis = 1_776_800_000_000,
        )

        val result = scheduler.schedule(alarm)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            scheduler.pendingIntentRequestCode(alarm.id),
            scheduler.alarmIntent(alarm.id),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )

        assertThat(result).isEqualTo(
            ScheduleResult.Failed("Alarm ${alarm.id} has a stale trigger and cannot be scheduled."),
        )
        assertThat(pendingIntent).isNull()
    }

    @Test
    fun `cancel clears existing pending intent`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scheduler = AndroidAlarmScheduler(
            context = context,
            permissionStatusReader = PermissionStatusReader { true },
            nowProvider = { 1_000L },
        )
        val alarmId = 24L
        val futureTriggerAtMillis = 61_000L

        scheduler.schedule(
            Alarm(
                id = alarmId,
                title = "Read",
                hour = 21,
                minute = 0,
                repeatRule = RepeatRule.Daily,
                enabled = true,
                ringtoneUri = "content://settings/system/alarm_alert",
                source = AlarmSource.MANUAL,
                aiOriginalText = null,
                nextTriggerAtMillis = futureTriggerAtMillis,
                createdAtMillis = 1_776_800_000_000,
                updatedAtMillis = 1_776_800_000_000,
            ),
        )

        val result = scheduler.cancel(alarmId)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            scheduler.pendingIntentRequestCode(alarmId),
            scheduler.alarmIntent(alarmId),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )

        assertThat(result).isEqualTo(ScheduleResult.Cancelled)
        assertThat(pendingIntent).isNull()
    }

    @Test
    fun `near now future trigger schedules deterministically from injected clock`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scheduler = AndroidAlarmScheduler(
            context = context,
            permissionStatusReader = PermissionStatusReader { true },
            nowProvider = { 1_000L },
        )
        val alarm = Alarm(
            id = 52L,
            title = "Edge",
            hour = 6,
            minute = 30,
            repeatRule = RepeatRule.Daily,
            enabled = true,
            ringtoneUri = "content://settings/system/alarm_alert",
            source = AlarmSource.MANUAL,
            aiOriginalText = null,
            nextTriggerAtMillis = 1_001L,
            createdAtMillis = 1_776_800_000_000,
            updatedAtMillis = 1_776_800_000_000,
        )

        val result = scheduler.schedule(alarm)

        assertThat(result).isEqualTo(ScheduleResult.Scheduled)
    }

    @Test
    fun `colliding request codes do not share cancellation identity`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scheduler = AndroidAlarmScheduler(
            context = context,
            permissionStatusReader = PermissionStatusReader { true },
            nowProvider = { 1_000L },
        )
        val firstAlarmId = 1L
        val secondAlarmId = 4_294_967_296L

        assertThat(scheduler.pendingIntentRequestCode(firstAlarmId))
            .isEqualTo(scheduler.pendingIntentRequestCode(secondAlarmId))

        scheduler.schedule(scheduledAlarm(id = firstAlarmId, nextTriggerAtMillis = 61_000L))
        scheduler.schedule(scheduledAlarm(id = secondAlarmId, nextTriggerAtMillis = 62_000L))

        val firstPendingIntent = PendingIntent.getBroadcast(
            context,
            scheduler.pendingIntentRequestCode(firstAlarmId),
            scheduler.alarmIntent(firstAlarmId),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        val secondPendingIntent = PendingIntent.getBroadcast(
            context,
            scheduler.pendingIntentRequestCode(secondAlarmId),
            scheduler.alarmIntent(secondAlarmId),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )

        val cancelResult = scheduler.cancel(firstAlarmId)
        val remainingSecondPendingIntent = PendingIntent.getBroadcast(
            context,
            scheduler.pendingIntentRequestCode(secondAlarmId),
            scheduler.alarmIntent(secondAlarmId),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )

        assertThat(firstPendingIntent).isNotNull()
        assertThat(secondPendingIntent).isNotNull()
        assertThat(cancelResult).isEqualTo(ScheduleResult.Cancelled)
        assertThat(remainingSecondPendingIntent).isNotNull()
    }

    @Test
    fun `alarm intent targets real receiver class`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scheduler = AndroidAlarmScheduler(
            context = context,
            permissionStatusReader = PermissionStatusReader { true },
            nowProvider = { 1_000L },
        )

        val intent = scheduler.alarmIntent(77L)

        assertThat(intent.component?.className).isEqualTo(AlarmReceiver::class.java.name)
        assertThat(runCatching { Class.forName(intent.component!!.className) }.isSuccess).isTrue()
    }

    private fun scheduledAlarm(
        id: Long,
        nextTriggerAtMillis: Long,
    ): Alarm = Alarm(
        id = id,
        title = "Scheduled",
        hour = 6,
        minute = 30,
        repeatRule = RepeatRule.Daily,
        enabled = true,
        ringtoneUri = "content://settings/system/alarm_alert",
        source = AlarmSource.MANUAL,
        aiOriginalText = null,
        nextTriggerAtMillis = nextTriggerAtMillis,
        createdAtMillis = 1_776_800_000_000,
        updatedAtMillis = 1_776_800_000_000,
    )
}
