package com.cory.noter.alarm

import com.cory.noter.data.alarm.AlarmRepository
import com.cory.noter.domain.alarm.Alarm
import com.cory.noter.domain.alarm.AlarmPauseMode
import com.cory.noter.domain.alarm.NextTriggerCalculator
import com.cory.noter.domain.alarm.RepeatRule
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

sealed interface AlarmManagementResult {
    data class Updated(val alarm: Alarm) : AlarmManagementResult
    data object MissingAlarm : AlarmManagementResult
    data class InvalidState(val reason: String) : AlarmManagementResult
    data class MissingSchedulingPermission(val permission: String) : AlarmManagementResult
    data class SchedulerFailed(val reason: String) : AlarmManagementResult
    data class StaleOrMismatchedTrigger(val alarmId: Long) : AlarmManagementResult
}

class AlarmManagementUseCase(
    private val repository: AlarmRepository,
    private val schedulingUseCase: AlarmSchedulingUseCase,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val nextTriggerCalculator: NextTriggerCalculator = NextTriggerCalculator(),
    private val zoneIdProvider: () -> ZoneId = { ZoneId.systemDefault() },
) {
    suspend fun pauseNextOccurrence(alarmId: Long): AlarmManagementResult {
        val alarm = repository.get(alarmId) ?: return AlarmManagementResult.MissingAlarm
        val triggerAtMillis = alarm.nextTriggerAtMillis
            ?: return AlarmManagementResult.InvalidState(
                "Alarm ${alarm.id} cannot pause its next occurrence because it has no next trigger.",
            )

        if (alarm.repeatRule is RepeatRule.Once) {
            return pauseOneTimeNextOccurrence(alarm)
        }

        val updated = alarm.copy(
            enabled = true,
            pauseMode = AlarmPauseMode.NEXT_OCCURRENCE,
            pausedOccurrenceAtMillis = triggerAtMillis,
            nextTriggerAtMillis = triggerAtMillis,
        )
        return AlarmManagementResult.Updated(repository.updateFromManagement(updated))
    }

    suspend fun pauseIndefinitely(alarmId: Long): AlarmManagementResult {
        val alarm = repository.get(alarmId) ?: return AlarmManagementResult.MissingAlarm
        return pauseIndefinitely(alarm)
    }

    suspend fun resume(alarmId: Long): AlarmManagementResult {
        val alarm = repository.get(alarmId) ?: return AlarmManagementResult.MissingAlarm
        return when (alarm.pauseMode) {
            AlarmPauseMode.NONE -> AlarmManagementResult.InvalidState("Alarm ${alarm.id} is not paused.")
            AlarmPauseMode.NEXT_OCCURRENCE -> resumePausedNext(alarm)
            AlarmPauseMode.INDEFINITE -> resumeIndefinite(alarm)
        }
    }

    suspend fun consumeDuePausedNext(
        alarmId: Long,
        deliveredTriggerAtMillis: Long,
    ): AlarmManagementResult {
        val alarm = repository.get(alarmId) ?: return AlarmManagementResult.MissingAlarm
        if (alarm.pauseMode != AlarmPauseMode.NEXT_OCCURRENCE) {
            return AlarmManagementResult.StaleOrMismatchedTrigger(alarm.id)
        }
        val anchor = alarm.pausedOccurrenceAtMillis
            ?: return AlarmManagementResult.InvalidState(
                "Alarm ${alarm.id} has NEXT_OCCURRENCE pause mode without a paused occurrence anchor.",
            )
        if (anchor != deliveredTriggerAtMillis || alarm.nextTriggerAtMillis != deliveredTriggerAtMillis) {
            return AlarmManagementResult.StaleOrMismatchedTrigger(alarm.id)
        }
        if (alarm.repeatRule is RepeatRule.Once) {
            return AlarmManagementResult.InvalidState("One-time alarm ${alarm.id} cannot consume paused-next state.")
        }

        val nextTriggerAtMillis = calculateNextTriggerAtMillis(
            alarm,
            maxOf(Instant.ofEpochMilli(anchor), clock.instant()),
        )
            ?: return clearPausedNextAsIndefinite(alarm)
        val updated = alarm.copy(
            enabled = true,
            pauseMode = AlarmPauseMode.NONE,
            pausedOccurrenceAtMillis = null,
            nextTriggerAtMillis = nextTriggerAtMillis,
        )
        return scheduleThenPersist(updated)
    }

    private suspend fun clearPausedNextAsIndefinite(alarm: Alarm): AlarmManagementResult {
        val updated = alarm.copy(
            enabled = false,
            pauseMode = AlarmPauseMode.INDEFINITE,
            pausedOccurrenceAtMillis = null,
            nextTriggerAtMillis = null,
        )
        return AlarmManagementResult.Updated(repository.updateFromManagement(updated))
    }

    private suspend fun pauseOneTimeNextOccurrence(alarm: Alarm): AlarmManagementResult = pauseIndefinitely(alarm)

    private suspend fun pauseIndefinitely(alarm: Alarm): AlarmManagementResult {
        val updated = alarm.copy(
            enabled = false,
            pauseMode = AlarmPauseMode.INDEFINITE,
            pausedOccurrenceAtMillis = null,
            nextTriggerAtMillis = null,
        )
        return when (val cancelResult = schedulingUseCase.cancel(alarm.id)) {
            ScheduleResult.Cancelled -> AlarmManagementResult.Updated(repository.updateFromManagement(updated))
            is ScheduleResult.MissingPermission -> AlarmManagementResult.MissingSchedulingPermission(cancelResult.permission)
            is ScheduleResult.Failed -> AlarmManagementResult.SchedulerFailed(cancelResult.reason)
            ScheduleResult.Scheduled -> AlarmManagementResult.SchedulerFailed(
                "Alarm ${alarm.id} was scheduled instead of cancelled.",
            )
        }
    }

    private suspend fun resumePausedNext(alarm: Alarm): AlarmManagementResult {
        val anchor = alarm.pausedOccurrenceAtMillis
            ?: return AlarmManagementResult.InvalidState(
                "Alarm ${alarm.id} has NEXT_OCCURRENCE pause mode without a paused occurrence anchor.",
            )
        val currentTrigger = alarm.nextTriggerAtMillis
            ?: return AlarmManagementResult.InvalidState(
                "Alarm ${alarm.id} has NEXT_OCCURRENCE pause mode without a scheduled checkpoint.",
            )
        if (anchor != currentTrigger) {
            return AlarmManagementResult.InvalidState("Alarm ${alarm.id} paused occurrence does not match its trigger.")
        }
        val updated = alarm.copy(
            enabled = true,
            pauseMode = AlarmPauseMode.NONE,
            pausedOccurrenceAtMillis = null,
            nextTriggerAtMillis = currentTrigger,
        )
        return scheduleThenPersist(updated)
    }

    private suspend fun resumeIndefinite(alarm: Alarm): AlarmManagementResult {
        val nextTriggerAtMillis = calculateNextTriggerAtMillis(alarm, clock.instant())
            ?: return when (alarm.repeatRule) {
                is RepeatRule.Once -> AlarmManagementResult.InvalidState(
                    "Alarm ${alarm.id} cannot resume because it is an expired one-time alarm.",
                )

                else -> AlarmManagementResult.InvalidState("Alarm ${alarm.id} has no future trigger.")
            }
        val updated = alarm.copy(
            enabled = true,
            pauseMode = AlarmPauseMode.NONE,
            pausedOccurrenceAtMillis = null,
            nextTriggerAtMillis = nextTriggerAtMillis,
        )
        return scheduleThenPersist(updated)
    }

    private suspend fun scheduleThenPersist(alarm: Alarm): AlarmManagementResult =
        when (val scheduleResult = schedulingUseCase.syncSchedule(alarm)) {
            ScheduleResult.Scheduled -> AlarmManagementResult.Updated(repository.updateFromManagement(alarm))
            is ScheduleResult.MissingPermission -> AlarmManagementResult.MissingSchedulingPermission(
                scheduleResult.permission,
            )

            is ScheduleResult.Failed -> AlarmManagementResult.SchedulerFailed(scheduleResult.reason)
            ScheduleResult.Cancelled -> AlarmManagementResult.SchedulerFailed(
                "Alarm ${alarm.id} was cancelled instead of scheduled.",
            )
        }

    private fun calculateNextTriggerAtMillis(alarm: Alarm, now: Instant): Long? =
        nextTriggerCalculator.nextTrigger(
            hour = alarm.hour,
            minute = alarm.minute,
            repeatRule = alarm.repeatRule,
            now = now,
            zoneId = zoneIdProvider(),
        )?.toEpochMilli()
}
