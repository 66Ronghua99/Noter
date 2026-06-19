package com.cory.noter.alarm

import com.cory.noter.data.alarm.AlarmRepository
import com.cory.noter.domain.alarm.Alarm
import com.cory.noter.domain.alarm.RepeatRule
import java.time.Clock
import kotlinx.coroutines.flow.first

sealed interface StartupReconciliationResult {
    data class SkippedDisabled(val alarmId: Long) : StartupReconciliationResult

    data class DeletedExpiredOneTimeAlarm(val alarmId: Long) : StartupReconciliationResult

    data class Scheduled(
        val alarmId: Long,
        val triggerAtMillis: Long,
        val scheduleResult: ScheduleResult,
    ) : StartupReconciliationResult

    data class RecalculatedAndScheduled(
        val alarmId: Long,
        val previousTriggerAtMillis: Long?,
        val recalculatedTriggerAtMillis: Long,
        val scheduleResult: ScheduleResult,
    ) : StartupReconciliationResult

    data class Failed(
        val alarmId: Long,
        val reason: String,
        val scheduleResult: ScheduleResult? = null,
    ) : StartupReconciliationResult
}

class StartupReconciliation(
    private val repository: AlarmRepository,
    private val schedulingUseCase: AlarmSchedulingUseCase,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    suspend fun reconcile(): List<StartupReconciliationResult> = repository.alarms.first().map { alarm ->
        reconcile(alarm)
    }

    private suspend fun reconcile(alarm: Alarm): StartupReconciliationResult = when {
        !alarm.enabled -> StartupReconciliationResult.SkippedDisabled(alarm.id)
        alarm.nextTriggerAtMillis == null -> reconcileMissingOrStale(alarm)
        alarm.nextTriggerAtMillis <= clock.millis() -> reconcileMissingOrStale(alarm)
        else -> scheduleCurrent(alarm)
    }

    private suspend fun reconcileMissingOrStale(alarm: Alarm): StartupReconciliationResult =
        when (alarm.repeatRule) {
            is RepeatRule.Once -> {
                repository.delete(alarm.id)
                StartupReconciliationResult.DeletedExpiredOneTimeAlarm(alarm.id)
            }

            RepeatRule.Daily,
            RepeatRule.Weekdays,
            is RepeatRule.CustomWeekdays,
            is RepeatRule.WeeklyInterval,
            -> recalculateAndSchedule(alarm)
        }

    private suspend fun recalculateAndSchedule(alarm: Alarm): StartupReconciliationResult {
        val updated = runCatching {
            repository.update(alarm)
        }.getOrElse { error ->
            return StartupReconciliationResult.Failed(
                alarmId = alarm.id,
                reason = error.message ?: "Failed to recalculate alarm ${alarm.id}.",
            )
        }

        val recalculatedTrigger = updated.nextTriggerAtMillis
            ?: return StartupReconciliationResult.Failed(
                alarmId = updated.id,
                reason = "Alarm ${updated.id} is enabled after reconciliation but still missing a trigger.",
            )
        val scheduleResult = schedulingUseCase.syncSchedule(updated)

        return when (scheduleResult) {
            ScheduleResult.Scheduled -> StartupReconciliationResult.RecalculatedAndScheduled(
                alarmId = updated.id,
                previousTriggerAtMillis = alarm.nextTriggerAtMillis,
                recalculatedTriggerAtMillis = recalculatedTrigger,
                scheduleResult = scheduleResult,
            )

            is ScheduleResult.Cancelled,
            is ScheduleResult.Failed,
            is ScheduleResult.MissingPermission,
            -> StartupReconciliationResult.Failed(
                alarmId = updated.id,
                reason = scheduleResult.toFailureReason(updated.id),
                scheduleResult = scheduleResult,
            )
        }
    }

    private fun scheduleCurrent(alarm: Alarm): StartupReconciliationResult {
        val triggerAtMillis = requireNotNull(alarm.nextTriggerAtMillis) {
            "Alarm ${alarm.id} is enabled but missing a trigger."
        }
        val scheduleResult = schedulingUseCase.syncSchedule(alarm)

        return when (scheduleResult) {
            ScheduleResult.Scheduled -> StartupReconciliationResult.Scheduled(
                alarmId = alarm.id,
                triggerAtMillis = triggerAtMillis,
                scheduleResult = scheduleResult,
            )

            is ScheduleResult.Cancelled,
            is ScheduleResult.Failed,
            is ScheduleResult.MissingPermission,
            -> StartupReconciliationResult.Failed(
                alarmId = alarm.id,
                reason = scheduleResult.toFailureReason(alarm.id),
                scheduleResult = scheduleResult,
            )
        }
    }

    private fun ScheduleResult.toFailureReason(alarmId: Long): String = when (this) {
        ScheduleResult.Cancelled -> "Alarm $alarmId reconciliation cancelled unexpectedly."
        is ScheduleResult.Failed -> reason
        is ScheduleResult.MissingPermission -> "Missing permission while scheduling alarm $alarmId: $permission"
        ScheduleResult.Scheduled -> "Alarm $alarmId scheduled successfully."
    }
}
