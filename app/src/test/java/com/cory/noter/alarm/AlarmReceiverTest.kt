package com.cory.noter.alarm

import com.cory.noter.domain.alarm.Alarm
import com.cory.noter.domain.alarm.AlarmPauseMode
import com.cory.noter.domain.alarm.AlarmSource
import com.cory.noter.domain.alarm.RepeatRule
import com.google.common.truth.Truth.assertThat
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Test

class AlarmReceiverTest {
    private val receiver = AlarmReceiver()

    @Test
    fun `stale delivered trigger is ignored`() {
        val alarm = alarm(enabled = true, nextTriggerAtMillis = 2_000L)

        val result = receiver.shouldHandleTrigger(alarm, deliveredTriggerAtMillis = 1_000L)

        assertThat(result).isFalse()
    }

    @Test
    fun `disabled alarm is ignored even when trigger matches`() {
        val alarm = alarm(enabled = false, nextTriggerAtMillis = 2_000L)

        val result = receiver.shouldHandleTrigger(alarm, deliveredTriggerAtMillis = 2_000L)

        assertThat(result).isFalse()
    }

    @Test
    fun `enabled current trigger is handled`() {
        val alarm = alarm(enabled = true, nextTriggerAtMillis = 2_000L)

        val result = receiver.shouldHandleTrigger(alarm, deliveredTriggerAtMillis = 2_000L)

        assertThat(result).isTrue()
    }

    @Test
    fun `matching paused next checkpoint is consumed silently and does not ring`() = runTest {
        val repository = RecordingAlarmRepository(
            alarm(
                enabled = true,
                nextTriggerAtMillis = 2_000L,
                repeatRule = RepeatRule.Daily,
                pauseMode = AlarmPauseMode.NEXT_OCCURRENCE,
                pausedOccurrenceAtMillis = 2_000L,
            ),
        )
        val scheduler = FakeAlarmScheduler()
        val managementUseCase = AlarmManagementUseCase(
            repository = repository,
            schedulingUseCase = AlarmSchedulingUseCase(scheduler),
            clock = FixedClock(Instant.ofEpochMilli(2_000L)),
            zoneIdProvider = { ZoneId.of("UTC") },
        )

        val result = receiver.deliveryAction(
            alarm = repository.get(9L)!!,
            deliveredTriggerAtMillis = 2_000L,
            managementUseCase = managementUseCase,
        )

        assertThat(result).isEqualTo(AlarmDeliveryAction.ConsumedPausedCheckpoint)
        assertThat(repository.stored!!.pauseMode).isEqualTo(AlarmPauseMode.NONE)
        assertThat(scheduler.scheduledIds).contains(9L)
    }

    @Test
    fun `mismatched paused next checkpoint is ignored without mutation`() = runTest {
        val original = alarm(
            enabled = true,
            nextTriggerAtMillis = 2_000L,
            repeatRule = RepeatRule.Daily,
            pauseMode = AlarmPauseMode.NEXT_OCCURRENCE,
            pausedOccurrenceAtMillis = 2_000L,
        )
        val repository = RecordingAlarmRepository(original)
        val managementUseCase = AlarmManagementUseCase(
            repository = repository,
            schedulingUseCase = AlarmSchedulingUseCase(FakeAlarmScheduler()),
            clock = FixedClock(Instant.ofEpochMilli(2_000L)),
            zoneIdProvider = { ZoneId.of("UTC") },
        )

        val result = receiver.deliveryAction(
            alarm = original,
            deliveredTriggerAtMillis = 1_000L,
            managementUseCase = managementUseCase,
        )

        assertThat(result).isEqualTo(AlarmDeliveryAction.Ignore)
        assertThat(repository.stored).isEqualTo(original)
    }

    @Test
    fun `normal matching active alarm rings`() = runTest {
        val alarm = alarm(enabled = true, nextTriggerAtMillis = 2_000L)
        val managementUseCase = AlarmManagementUseCase(
            repository = RecordingAlarmRepository(alarm),
            schedulingUseCase = AlarmSchedulingUseCase(FakeAlarmScheduler()),
            clock = FixedClock(Instant.ofEpochMilli(1_000L)),
            zoneIdProvider = { ZoneId.of("UTC") },
        )

        val result = receiver.deliveryAction(
            alarm = alarm,
            deliveredTriggerAtMillis = 2_000L,
            managementUseCase = managementUseCase,
        )

        assertThat(result).isEqualTo(AlarmDeliveryAction.Ring)
    }

    private fun alarm(
        enabled: Boolean,
        nextTriggerAtMillis: Long?,
        repeatRule: RepeatRule = RepeatRule.Once(LocalDate.of(2026, 4, 24)),
        pauseMode: AlarmPauseMode = AlarmPauseMode.NONE,
        pausedOccurrenceAtMillis: Long? = null,
    ): Alarm = Alarm(
        id = 9L,
        title = "Wake up",
        hour = 8,
        minute = 0,
        repeatRule = repeatRule,
        enabled = enabled,
        ringtoneUri = "content://settings/system/alarm_alert",
        source = AlarmSource.MANUAL,
        aiOriginalText = null,
        nextTriggerAtMillis = nextTriggerAtMillis,
        createdAtMillis = 1_000L,
        updatedAtMillis = 1_000L,
        pauseMode = pauseMode,
        pausedOccurrenceAtMillis = pausedOccurrenceAtMillis,
    )

    private class RecordingAlarmRepository(initialAlarm: Alarm) : com.cory.noter.data.alarm.AlarmRepository {
        var stored: Alarm? = initialAlarm
        override val alarms: kotlinx.coroutines.flow.Flow<List<Alarm>> =
            kotlinx.coroutines.flow.flow { emit(listOfNotNull(stored)) }

        override suspend fun get(id: Long): Alarm? = stored?.takeIf { it.id == id }
        override suspend fun create(draft: com.cory.noter.data.alarm.AlarmDraft): Alarm = error("Not used")
        override suspend fun update(alarm: Alarm): Alarm {
            stored = alarm
            return alarm
        }
        override suspend fun updateFromManagement(alarm: Alarm): Alarm {
            stored = alarm
            return alarm
        }
        override suspend fun enable(id: Long): Alarm? = error("Not used")
        override suspend fun disable(id: Long): Alarm? = error("Not used")
        override suspend fun delete(id: Long) {
            stored = null
        }
    }

    private class FixedClock(private val now: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneId.of("UTC")
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = now
    }
}
