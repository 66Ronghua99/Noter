package com.cory.noter.alarm

import com.cory.noter.domain.alarm.Alarm

class FakeAlarmScheduler : AlarmScheduler {
    val scheduledAlarms = linkedMapOf<Long, Alarm>()
    val cancelledIds = mutableListOf<Long>()

    var nextScheduleResult: ScheduleResult = ScheduleResult.Scheduled
    var nextCancelResult: ScheduleResult = ScheduleResult.Cancelled

    val scheduledIds: Set<Long>
        get() = scheduledAlarms.keys

    override fun schedule(alarm: Alarm): ScheduleResult {
        val result = nextScheduleResult
        if (result is ScheduleResult.Scheduled) {
            scheduledAlarms[alarm.id] = alarm
        }
        return result
    }

    override fun cancel(alarmId: Long): ScheduleResult {
        val result = nextCancelResult
        if (result is ScheduleResult.Cancelled) {
            cancelledIds += alarmId
            scheduledAlarms.remove(alarmId)
        }
        return result
    }
}
