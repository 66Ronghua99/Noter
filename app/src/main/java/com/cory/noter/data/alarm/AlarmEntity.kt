package com.cory.noter.data.alarm

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val hour: Int,
    val minute: Int,
    val repeatType: String,
    val daysOfWeekCsv: String,
    val onceDate: String?,
    val enabled: Boolean,
    val ringtoneUri: String,
    val source: String,
    val aiOriginalText: String?,
    val nextTriggerAtMillis: Long?,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)
