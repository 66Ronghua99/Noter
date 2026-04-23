package com.cory.noter.domain.alarm

data class Alarm(
    val id: Long,
    val title: String,
    val hour: Int,
    val minute: Int,
    val repeatRule: RepeatRule,
    val enabled: Boolean,
    val ringtoneUri: String,
    val source: AlarmSource,
    val aiOriginalText: String?,
    val nextTriggerAtMillis: Long?,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)
