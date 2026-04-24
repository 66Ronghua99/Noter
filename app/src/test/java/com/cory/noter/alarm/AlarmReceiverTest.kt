package com.cory.noter.alarm

import com.cory.noter.domain.alarm.Alarm
import com.cory.noter.domain.alarm.AlarmSource
import com.cory.noter.domain.alarm.RepeatRule
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
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

    private fun alarm(
        enabled: Boolean,
        nextTriggerAtMillis: Long?,
    ): Alarm = Alarm(
        id = 9L,
        title = "Wake up",
        hour = 8,
        minute = 0,
        repeatRule = RepeatRule.Once(LocalDate.of(2026, 4, 24)),
        enabled = enabled,
        ringtoneUri = "content://settings/system/alarm_alert",
        source = AlarmSource.MANUAL,
        aiOriginalText = null,
        nextTriggerAtMillis = nextTriggerAtMillis,
        createdAtMillis = 1_000L,
        updatedAtMillis = 1_000L,
    )
}
