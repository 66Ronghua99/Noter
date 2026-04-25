package com.cory.noter.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.cory.noter.ai.AiCreateResult
import com.cory.noter.ai.AiCreateResultNotifier

class AndroidAiCreateResultNotifier(
    private val context: Context,
) : AiCreateResultNotifier {
    override fun notifyStarted() {
        Log.d(TAG, "notify.started")
        notify(
            id = IN_PROGRESS_NOTIFICATION_ID,
            notification = baseBuilder()
                .setContentTitle("Creating alarm")
                .setContentText("AI alarm creation is running in the background.")
                .setOngoing(true)
                .setAutoCancel(false)
                .setProgress(0, 0, true)
                .build(),
        )
    }

    override fun notifyResult(result: AiCreateResult) {
        Log.d(TAG, "notify.result type=${result.javaClass.simpleName}")
        NotificationManagerCompat.from(context).cancel(IN_PROGRESS_NOTIFICATION_ID)
        val builder = baseBuilder()
            .setOngoing(false)
            .setAutoCancel(true)
            .setProgress(0, 0, false)

        when (result) {
            is AiCreateResult.Created -> notify(
                id = RESULT_NOTIFICATION_ID,
                notification = builder
                    .setContentTitle("Alarm created")
                    .setContentText(result.alarm.title)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .build(),
            )

            is AiCreateResult.MissingSchedulingPermission -> notify(
                id = RESULT_NOTIFICATION_ID,
                notification = builder
                    .setContentTitle("Alarm saved")
                    .setContentText("Exact alarm permission is needed before it can ring reliably.")
                    .setStyle(
                        NotificationCompat.BigTextStyle().bigText(
                            "Alarm \"${result.alarm.title}\" was saved, but Android needs exact alarm permission before it can ring reliably.",
                        ),
                    )
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .addAction(0, "Open settings", exactAlarmSettingsIntent())
                    .build(),
            )

            else -> {
                val reason = result.failureText()
                notify(
                    id = RESULT_NOTIFICATION_ID,
                    notification = builder
                        .setContentTitle("AI alarm failed")
                        .setContentText(reason)
                        .setStyle(NotificationCompat.BigTextStyle().bigText(reason))
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .build(),
                )
            }
        }
    }

    private fun baseBuilder(): NotificationCompat.Builder {
        createChannel()
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
    }

    @SuppressLint("MissingPermission")
    private fun notify(id: Int, notification: android.app.Notification) {
        if (!canPostNotifications()) {
            Log.w(TAG, "Notification permission missing; cannot show AI create result.")
            return
        }
        NotificationManagerCompat.from(context).notify(id, notification)
        Log.d(TAG, "notify.posted id=$id")
    }

    private fun canPostNotifications(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true
        }
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val notificationManager = context.getSystemService(NotificationManager::class.java) ?: return
        if (notificationManager.getNotificationChannel(CHANNEL_ID) != null) {
            return
        }
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "AI alarm creation",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Status updates for background AI alarm creation."
            },
        )
    }

    private fun exactAlarmSettingsIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        EXACT_ALARM_SETTINGS_REQUEST_CODE,
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = Uri.parse("package:${context.packageName}")
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun AiCreateResult.failureText(): String = when (this) {
        AiCreateResult.MissingApiKey -> "Add an OpenRouter API key in Settings before using AI create."
        AiCreateResult.MissingModel -> "Choose a supported model in Settings before using AI create."
        is AiCreateResult.NetworkFailure -> "Network error: $reason"
        is AiCreateResult.RateLimited -> "Model rate limit: $reason"
        is AiCreateResult.RemoteFailure -> "OpenRouter error $code: $reason"
        is AiCreateResult.InvalidResponse -> reason
        is AiCreateResult.ClarificationRequired -> reason
        is AiCreateResult.CreateFailed -> reason
        is AiCreateResult.ScheduleFailed -> reason
        is AiCreateResult.MissingSchedulingPermission,
        is AiCreateResult.Created,
        -> "AI alarm creation finished."
    }

    private companion object {
        const val TAG = "NoterAiCreateNotify"
        const val CHANNEL_ID = "ai_alarm_creation"
        const val IN_PROGRESS_NOTIFICATION_ID = 21001
        const val RESULT_NOTIFICATION_ID = 21002
        const val EXACT_ALARM_SETTINGS_REQUEST_CODE = 21003
    }
}
