package com.cory.noter.alarm

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.cory.noter.domain.alarm.Alarm
import com.cory.noter.permissions.AndroidPermissionStatusReader
import com.cory.noter.permissions.PermissionStatusReader

class AndroidAlarmScheduler(
    private val context: Context,
    private val permissionStatusReader: PermissionStatusReader = AndroidPermissionStatusReader(context),
    private val alarmManager: AlarmManager = requireNotNull(
        context.getSystemService(AlarmManager::class.java),
    ) {
        "AlarmManager is not available."
    },
) : AlarmScheduler {
    override fun schedule(alarm: Alarm): ScheduleResult {
        if (!alarm.enabled) {
            return ScheduleResult.Failed("Alarm ${alarm.id} is disabled and cannot be scheduled.")
        }

        val triggerAtMillis = alarm.nextTriggerAtMillis
            ?: return ScheduleResult.Failed("Alarm ${alarm.id} has no future trigger to schedule.")

        if (!permissionStatusReader.canScheduleExactAlarms()) {
            return ScheduleResult.MissingPermission(Manifest.permission.SCHEDULE_EXACT_ALARM)
        }

        val pendingIntent = pendingIntent(
            alarmId = alarm.id,
            flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return runCatching {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent,
            )
            ScheduleResult.Scheduled
        }.getOrElse { error ->
            ScheduleResult.Failed(error.message ?: "Alarm scheduling failed.")
        }
    }

    override fun cancel(alarmId: Long): ScheduleResult {
        return runCatching {
            val pendingIntent = pendingIntentOrNull(alarmId)
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
            ScheduleResult.Cancelled
        }.getOrElse { error ->
            ScheduleResult.Failed(error.message ?: "Alarm cancellation failed.")
        }
    }

    fun pendingIntentRequestCode(alarmId: Long): Int = (alarmId xor (alarmId ushr 32)).toInt()

    fun alarmIntent(alarmId: Long): Intent = Intent(ACTION_TRIGGER_ALARM)
        .setClassName(context.packageName, ALARM_RECEIVER_CLASS_NAME)
        .setPackage(context.packageName)
        .putExtra(EXTRA_ALARM_ID, alarmId)

    private fun pendingIntent(
        alarmId: Long,
        flags: Int,
    ): PendingIntent = PendingIntent.getBroadcast(
        context,
        pendingIntentRequestCode(alarmId),
        alarmIntent(alarmId),
        flags,
    )

    private fun pendingIntentOrNull(alarmId: Long): PendingIntent? = PendingIntent.getBroadcast(
        context,
        pendingIntentRequestCode(alarmId),
        alarmIntent(alarmId),
        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
    )

    private companion object {
        const val ACTION_TRIGGER_ALARM = "com.cory.noter.alarm.TRIGGER"
        const val EXTRA_ALARM_ID = "alarm_id"
        const val ALARM_RECEIVER_CLASS_NAME = "com.cory.noter.alarm.AlarmReceiver"
    }
}
