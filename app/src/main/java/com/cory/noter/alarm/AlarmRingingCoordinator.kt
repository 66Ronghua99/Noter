package com.cory.noter.alarm

import com.cory.noter.data.alarm.AlarmRepository
import com.cory.noter.domain.alarm.RepeatRule

sealed interface AlarmStopResult {
    data object MissingAlarm : AlarmStopResult
    data object DeletedOneTimeAlarm : AlarmStopResult
    data class RescheduledRepeatingAlarm(val alarmId: Long) : AlarmStopResult
    data class Failed(val reason: String) : AlarmStopResult
}

class AlarmRingingCoordinator(
    private val repository: AlarmRepository,
    private val schedulingUseCase: AlarmSchedulingUseCase,
) {
    suspend fun stopRinging(alarmId: Long): AlarmStopResult {
        val alarm = repository.get(alarmId) ?: return AlarmStopResult.MissingAlarm

        return when (val cancelResult = schedulingUseCase.cancel(alarmId)) {
            is ScheduleResult.Failed -> AlarmStopResult.Failed(cancelResult.reason)
            else -> stopAfterCancel(alarm)
        }
    }

    private suspend fun stopAfterCancel(alarm: com.cory.noter.domain.alarm.Alarm): AlarmStopResult =
        when (alarm.repeatRule) {
            is RepeatRule.Once -> {
                repository.delete(alarm.id)
                AlarmStopResult.DeletedOneTimeAlarm
            }

            RepeatRule.Daily,
            RepeatRule.Weekdays,
            is RepeatRule.CustomWeekdays,
            -> {
                val updated = repository.update(alarm)
                when (val scheduleResult = schedulingUseCase.syncSchedule(updated)) {
                    is ScheduleResult.Scheduled -> AlarmStopResult.RescheduledRepeatingAlarm(updated.id)
                    is ScheduleResult.Cancelled -> AlarmStopResult.Failed(
                        "Repeating alarm ${updated.id} was cancelled instead of rescheduled.",
                    )

                    is ScheduleResult.MissingPermission -> AlarmStopResult.Failed(
                        "Missing permission while rescheduling alarm ${updated.id}: ${scheduleResult.permission}",
                    )

                    is ScheduleResult.Failed -> AlarmStopResult.Failed(scheduleResult.reason)
                }
            }
        }
}
