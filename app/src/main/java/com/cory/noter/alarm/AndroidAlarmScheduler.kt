package com.cory.noter.alarm

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
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
    private val nowProvider: () -> Long = System::currentTimeMillis,
) : AlarmScheduler {
    override fun schedule(alarm: Alarm): ScheduleResult {
        if (!alarm.enabled) {
            return ScheduleResult.Failed("Alarm ${alarm.id} is disabled and cannot be scheduled.")
        }

        val triggerAtMillis = alarm.nextTriggerAtMillis
            ?: return ScheduleResult.Failed("Alarm ${alarm.id} has no future trigger to schedule.")
        if (triggerAtMillis <= nowProvider()) {
            return ScheduleResult.Failed("Alarm ${alarm.id} has a stale trigger and cannot be scheduled.")
        }

        if (!permissionStatusReader.canScheduleExactAlarms()) {
            return ScheduleResult.MissingPermission(Manifest.permission.SCHEDULE_EXACT_ALARM)
        }

        val pendingIntent = pendingIntent(
            alarm = alarm,
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

    fun alarmIntent(alarmId: Long): Intent = Intent(context, AlarmReceiver::class.java)
        .setAction(ACTION_TRIGGER_ALARM)
        .setData(alarmDataUri(alarmId))
        .putExtra(EXTRA_ALARM_ID, alarmId)
        .putExtra(EXTRA_TRIGGER_AT_MILLIS, INVALID_TRIGGER_AT_MILLIS)

    fun alarmIntent(alarm: Alarm): Intent = Intent(context, AlarmReceiver::class.java)
        .setAction(ACTION_TRIGGER_ALARM)
        .setData(alarmDataUri(alarm.id))
        .putExtra(EXTRA_ALARM_ID, alarm.id)
        .putExtra(EXTRA_TRIGGER_AT_MILLIS, alarm.nextTriggerAtMillis ?: INVALID_TRIGGER_AT_MILLIS)

    private fun pendingIntent(
        alarm: Alarm,
        flags: Int,
    ): PendingIntent = PendingIntent.getBroadcast(
        context,
        pendingIntentRequestCode(alarm.id),
        alarmIntent(alarm),
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
        const val EXTRA_TRIGGER_AT_MILLIS = "trigger_at_millis"
        const val INVALID_TRIGGER_AT_MILLIS = -1L
    }

    private fun alarmDataUri(alarmId: Long): Uri = Uri.Builder()
        .scheme("noter")
        .authority("alarm")
        .appendPath(alarmId.toString())
        .build()
}
