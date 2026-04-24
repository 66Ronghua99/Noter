package com.cory.noter.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, INVALID_ALARM_ID)
        if (alarmId == INVALID_ALARM_ID) {
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val alarm = AlarmRuntimeGraph.repository(context).get(alarmId) ?: return@launch
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
        const val INVALID_ALARM_ID = -1L
    }
}
