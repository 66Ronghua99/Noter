package com.cory.noter.data.alarm

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "alarm_calendar_events",
    foreignKeys = [
        ForeignKey(
            entity = AlarmEntity::class,
            parentColumns = ["id"],
            childColumns = ["alarmId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["alarmId"], unique = true),
    ],
)
data class AlarmCalendarEventEntity(
    @PrimaryKey val alarmId: Long,
    val calendarId: Long,
    val eventId: Long,
    val durationMinutes: Int,
    val reason: String,
    val status: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)
