package com.cory.noter.ui

import com.cory.noter.data.alarm.AlarmDraft
import com.cory.noter.data.alarm.AlarmRepository
import com.cory.noter.domain.alarm.Alarm
import com.cory.noter.domain.alarm.NextTriggerCalculator
import java.time.Clock
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class FakeAlarmRepository(
    private val clock: Clock,
    private val zoneId: ZoneId,
    private val nextTriggerCalculator: NextTriggerCalculator = NextTriggerCalculator(),
) : AlarmRepository {
    private val state = MutableStateFlow<List<Alarm>>(emptyList())
    private var nextId = 1L

    override val alarms: Flow<List<Alarm>> = state

    override suspend fun get(id: Long): Alarm? = state.value.firstOrNull { it.id == id }

    override suspend fun create(draft: AlarmDraft): Alarm {
        val nowMillis = clock.millis()
        val alarm = Alarm(
            id = nextId++,
            title = draft.title,
            hour = draft.hour,
            minute = draft.minute,
            repeatRule = draft.repeatRule,
            enabled = draft.enabled,
            ringtoneUri = draft.ringtoneUri,
            source = draft.source,
            aiOriginalText = draft.aiOriginalText,
            nextTriggerAtMillis = computeNextTriggerAtMillis(
                hour = draft.hour,
                minute = draft.minute,
                repeatRule = draft.repeatRule,
                enabled = draft.enabled,
            ),
            createdAtMillis = nowMillis,
            updatedAtMillis = nowMillis,
        )
        state.update { it + alarm }
        return alarm
    }

    override suspend fun update(alarm: Alarm): Alarm {
        val updated = alarm.copy(
            nextTriggerAtMillis = computeNextTriggerAtMillis(
                hour = alarm.hour,
                minute = alarm.minute,
                repeatRule = alarm.repeatRule,
                enabled = alarm.enabled,
            ),
            updatedAtMillis = clock.millis(),
        )
        state.update { alarms -> alarms.map { existing -> if (existing.id == alarm.id) updated else existing } }
        return updated
    }

    override suspend fun enable(id: Long): Alarm? {
        val alarm = get(id) ?: return null
        return update(alarm.copy(enabled = true))
    }

    override suspend fun disable(id: Long): Alarm? {
        val alarm = get(id) ?: return null
        return update(alarm.copy(enabled = false))
    }

    override suspend fun delete(id: Long) {
        state.update { alarms -> alarms.filterNot { it.id == id } }
    }

    suspend fun seed(alarm: Alarm) {
        nextId = maxOf(nextId, alarm.id + 1)
        state.update { it + alarm }
    }

    private fun computeNextTriggerAtMillis(
        hour: Int,
        minute: Int,
        repeatRule: com.cory.noter.domain.alarm.RepeatRule,
        enabled: Boolean,
    ): Long? {
        if (!enabled) {
            return null
        }
        return nextTriggerCalculator.nextTrigger(
            hour = hour,
            minute = minute,
            repeatRule = repeatRule,
            now = clock.instant(),
            zoneId = zoneId,
        )?.toEpochMilli()
    }
}
