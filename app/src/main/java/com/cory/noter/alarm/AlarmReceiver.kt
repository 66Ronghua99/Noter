package com.cory.noter.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.cory.noter.di.appContainer
import com.cory.noter.domain.alarm.Alarm
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
                val alarm = context.appContainer.alarmRepository.get(alarmId) ?: return@launch
                if (!shouldHandleTrigger(alarm, deliveredTriggerAtMillis)) {
                    return@launch
                }
                ContextCompat.startForegroundService(
                    context,
                    RingingService.createStartIntent(
                        context = context,
                        alarmId = alarm.id,
                        alarmTitle = alarm.title,
                        ringtoneUri = alarm.ringtoneUri,
                    ),
                )
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
        alarm.nextTriggerAtMillis == deliveredTriggerAtMillis
}
