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
        scheduledAlarms[alarm.id] = alarm
        return nextScheduleResult
    }

    override fun cancel(alarmId: Long): ScheduleResult {
        cancelledIds += alarmId
        scheduledAlarms.remove(alarmId)
        return nextCancelResult
    }
}
