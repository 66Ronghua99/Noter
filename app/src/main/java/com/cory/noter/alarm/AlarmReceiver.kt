package com.cory.noter.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.cory.noter.di.appContainer
import com.cory.noter.domain.alarm.Alarm
import com.cory.noter.domain.alarm.AlarmPauseMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

enum class AlarmDeliveryAction {
    Ring,
    ConsumedPausedCheckpoint,
    Ignore,
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, INVALID_ALARM_ID)
        val deliveredTriggerAtMillis = intent.getLongExtra(
            EXTRA_TRIGGER_AT_MILLIS,
            INVALID_TRIGGER_AT_MILLIS,
        )
        if (alarmId == INVALID_ALARM_ID) {
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val appContainer = context.appContainer
                val alarm = appContainer.alarmRepository.get(alarmId) ?: return@launch
                when (
                    deliveryAction(
                        alarm = alarm,
                        deliveredTriggerAtMillis = deliveredTriggerAtMillis,
                        managementUseCase = appContainer.alarmManagementUseCase,
                    )
                ) {
                    AlarmDeliveryAction.Ring -> {
                        ContextCompat.startForegroundService(
                            context,
                            RingingService.createStartIntent(
                                context = context,
                                alarmId = alarm.id,
                                alarmTitle = alarm.title,
                                ringtoneUri = alarm.ringtoneUri,
                            ),
                        )
                    }

                    AlarmDeliveryAction.ConsumedPausedCheckpoint,
                    AlarmDeliveryAction.Ignore,
                    -> Unit
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val EXTRA_ALARM_ID = "alarm_id"
        const val EXTRA_TRIGGER_AT_MILLIS = "trigger_at_millis"
        const val INVALID_ALARM_ID = -1L
        const val INVALID_TRIGGER_AT_MILLIS = -1L
    }

    internal fun shouldHandleTrigger(
        alarm: Alarm,
        deliveredTriggerAtMillis: Long,
    ): Boolean = alarm.enabled &&
        deliveredTriggerAtMillis != INVALID_TRIGGER_AT_MILLIS &&
        alarm.nextTriggerAtMillis == deliveredTriggerAtMillis &&
        alarm.pauseMode == AlarmPauseMode.NONE

    internal suspend fun deliveryAction(
        alarm: Alarm,
        deliveredTriggerAtMillis: Long,
        managementUseCase: AlarmManagementUseCase,
    ): AlarmDeliveryAction {
        if (
            alarm.pauseMode == AlarmPauseMode.NEXT_OCCURRENCE &&
            deliveredTriggerAtMillis != INVALID_TRIGGER_AT_MILLIS
        ) {
            return when (managementUseCase.consumeDuePausedNext(alarm.id, deliveredTriggerAtMillis)) {
                is AlarmManagementResult.Updated -> AlarmDeliveryAction.ConsumedPausedCheckpoint
                AlarmManagementResult.MissingAlarm,
                is AlarmManagementResult.InvalidState,
                is AlarmManagementResult.MissingSchedulingPermission,
                is AlarmManagementResult.SchedulerFailed,
                is AlarmManagementResult.StaleOrMismatchedTrigger,
                -> AlarmDeliveryAction.Ignore
            }
        }

        return if (shouldHandleTrigger(alarm, deliveredTriggerAtMillis)) {
            AlarmDeliveryAction.Ring
        } else {
            AlarmDeliveryAction.Ignore
        }
    }
}
