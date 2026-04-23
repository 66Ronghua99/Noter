package com.cory.noter.alarm

import com.cory.noter.domain.alarm.Alarm

sealed interface ScheduleResult {
    data object Scheduled : ScheduleResult
    data object Cancelled : ScheduleResult
    data class MissingPermission(val permission: String) : ScheduleResult
    data class Failed(val reason: String) : ScheduleResult
}

interface AlarmScheduler {
    fun schedule(alarm: Alarm): ScheduleResult

    fun cancel(alarmId: Long): ScheduleResult
}

class AlarmSchedulingUseCase(
    private val scheduler: AlarmScheduler,
) {
    fun syncSchedule(alarm: Alarm): ScheduleResult = when {
        !alarm.enabled -> scheduler.cancel(alarm.id)
        alarm.nextTriggerAtMillis == null -> {
            ScheduleResult.Failed("Alarm ${alarm.id} is enabled but has no next trigger to schedule.")
        }

        else -> scheduler.schedule(alarm)
    }

    fun cancel(alarmId: Long): ScheduleResult = scheduler.cancel(alarmId)
}
