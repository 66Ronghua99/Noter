package com.cory.noter.alarm

import com.cory.noter.data.alarm.AlarmRepository
import com.cory.noter.domain.alarm.Alarm
import com.cory.noter.domain.alarm.AlarmPauseMode
import com.cory.noter.domain.alarm.RepeatRule
import java.time.Clock
import kotlinx.coroutines.flow.first

sealed interface StartupReconciliationResult {
    data class SkippedDisabled(val alarmId: Long) : StartupReconciliationResult

    data class SkippedIndefinite(val alarmId: Long) : StartupReconciliationResult

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

    data class ConsumedPausedNext(
        val alarmId: Long,
        val skippedTriggerAtMillis: Long,
        val nextTriggerAtMillis: Long,
        val scheduleResult: ScheduleResult,
    ) : StartupReconciliationResult

    data class ConsumedFinalPausedNext(
        val alarmId: Long,
        val skippedTriggerAtMillis: Long,
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
    private val managementUseCase: AlarmManagementUseCase,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    suspend fun reconcile(): List<StartupReconciliationResult> = repository.alarms.first().map { alarm ->
        reconcile(alarm)
    }

    private suspend fun reconcile(alarm: Alarm): StartupReconciliationResult = when (alarm.pauseMode) {
        AlarmPauseMode.INDEFINITE -> StartupReconciliationResult.SkippedIndefinite(alarm.id)
        AlarmPauseMode.NEXT_OCCURRENCE -> reconcilePausedNext(alarm)
        AlarmPauseMode.NONE -> reconcileActive(alarm)
    }

    private suspend fun reconcileActive(alarm: Alarm): StartupReconciliationResult = when {
        !alarm.enabled -> StartupReconciliationResult.SkippedDisabled(alarm.id)
        alarm.nextTriggerAtMillis == null -> reconcileMissingOrStale(alarm)
        alarm.nextTriggerAtMillis <= clock.millis() -> reconcileMissingOrStale(alarm)
        else -> scheduleCurrent(alarm)
    }

    private suspend fun reconcilePausedNext(alarm: Alarm): StartupReconciliationResult {
        val triggerAtMillis = alarm.nextTriggerAtMillis
            ?: return StartupReconciliationResult.Failed(
                alarmId = alarm.id,
                reason = "Paused-next alarm ${alarm.id} is missing its scheduled checkpoint.",
            )
        val anchor = alarm.pausedOccurrenceAtMillis
            ?: return StartupReconciliationResult.Failed(
                alarmId = alarm.id,
                reason = "Paused-next alarm ${alarm.id} is missing its paused occurrence anchor.",
            )
        if (anchor != triggerAtMillis) {
            return StartupReconciliationResult.Failed(
                alarmId = alarm.id,
                reason = "Paused-next alarm ${alarm.id} anchor does not match its scheduled checkpoint.",
            )
        }

        return if (triggerAtMillis > clock.millis()) {
            scheduleCurrent(alarm)
        } else {
            consumePausedNext(alarm, triggerAtMillis)
        }
    }

    private suspend fun consumePausedNext(
        alarm: Alarm,
        triggerAtMillis: Long,
    ): StartupReconciliationResult =
        when (val result = managementUseCase.consumeDuePausedNext(alarm.id, triggerAtMillis)) {
            is AlarmManagementResult.Updated -> {
                val nextTrigger = result.alarm.nextTriggerAtMillis ?: return when {
                    result.alarm.pauseMode == AlarmPauseMode.INDEFINITE && !result.alarm.enabled ->
                        StartupReconciliationResult.ConsumedFinalPausedNext(
                            alarmId = result.alarm.id,
                            skippedTriggerAtMillis = triggerAtMillis,
                        )

                    else -> StartupReconciliationResult.Failed(
                        alarmId = result.alarm.id,
                        reason = "Alarm ${result.alarm.id} consumed paused-next state without a future trigger.",
                    )
                }
                StartupReconciliationResult.ConsumedPausedNext(
                    alarmId = result.alarm.id,
                    skippedTriggerAtMillis = triggerAtMillis,
                    nextTriggerAtMillis = nextTrigger,
                    scheduleResult = ScheduleResult.Scheduled,
                )
            }

            AlarmManagementResult.MissingAlarm -> StartupReconciliationResult.Failed(
                alarmId = alarm.id,
                reason = "Alarm ${alarm.id} disappeared during paused-next reconciliation.",
            )

            is AlarmManagementResult.InvalidState -> StartupReconciliationResult.Failed(
                alarmId = alarm.id,
                reason = result.reason,
            )

            is AlarmManagementResult.MissingSchedulingPermission -> StartupReconciliationResult.Failed(
                alarmId = alarm.id,
                reason = "Missing permission while scheduling alarm ${alarm.id}: ${result.permission}",
                scheduleResult = ScheduleResult.MissingPermission(result.permission),
            )

            is AlarmManagementResult.SchedulerFailed -> StartupReconciliationResult.Failed(
                alarmId = alarm.id,
                reason = result.reason,
                scheduleResult = ScheduleResult.Failed(result.reason),
            )

            is AlarmManagementResult.StaleOrMismatchedTrigger -> StartupReconciliationResult.Failed(
                alarmId = alarm.id,
                reason = "Alarm ${alarm.id} paused-next checkpoint did not match reconciliation trigger.",
            )
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
