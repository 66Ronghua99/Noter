package com.cory.noter.data.alarm

import com.cory.noter.domain.alarm.Alarm
import com.cory.noter.domain.alarm.AlarmSource
import com.cory.noter.domain.alarm.RepeatRule
import kotlinx.coroutines.flow.Flow

data class AlarmDraft(
    val title: String,
    val hour: Int,
    val minute: Int,
    val repeatRule: RepeatRule,
    val enabled: Boolean,
    val ringtoneUri: String,
    val source: AlarmSource,
    val aiOriginalText: String?,
)

interface AlarmRepository {
    val alarms: Flow<List<Alarm>>

    suspend fun get(id: Long): Alarm?

    suspend fun create(draft: AlarmDraft): Alarm

    suspend fun update(alarm: Alarm): Alarm

    suspend fun enable(id: Long): Alarm?

    suspend fun disable(id: Long): Alarm?

    suspend fun delete(id: Long)
}
