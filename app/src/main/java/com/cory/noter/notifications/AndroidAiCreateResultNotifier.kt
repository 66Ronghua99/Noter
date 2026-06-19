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
import com.cory.noter.R
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
                .setContentTitle(context.getString(R.string.notification_ai_creating_title))
                .setContentText(context.getString(R.string.notification_ai_creating_body))
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
                    .setContentTitle(context.getString(R.string.notification_ai_created_title))
                    .setContentText(result.alarm.title)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .build(),
            )

            is AiCreateResult.MissingSchedulingPermission -> notify(
                id = RESULT_NOTIFICATION_ID,
                notification = builder
                    .setContentTitle(context.getString(R.string.notification_ai_saved_title))
                    .setContentText(context.getString(R.string.notification_ai_permission_needed))
                    .setStyle(
                        NotificationCompat.BigTextStyle().bigText(
                            context.getString(
                                R.string.notification_ai_permission_big_text,
                                result.alarm.title,
                            ),
                        ),
                    )
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .addAction(
                        0,
                        context.getString(R.string.notification_ai_open_settings),
                        exactAlarmSettingsIntent(),
                    )
                    .build(),
            )

            else -> {
                val reason = result.failureText()
                notify(
                    id = RESULT_NOTIFICATION_ID,
                    notification = builder
                        .setContentTitle(context.getString(R.string.notification_ai_failed_title))
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
                context.getString(R.string.notification_ai_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.notification_ai_channel_description)
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
        AiCreateResult.MissingApiKey -> context.getString(R.string.notification_ai_missing_api_key)
        AiCreateResult.MissingModel -> context.getString(R.string.notification_ai_missing_model)
        is AiCreateResult.NetworkFailure -> context.getString(R.string.notification_ai_network_failure, reason)
        is AiCreateResult.RateLimited -> context.getString(R.string.notification_ai_rate_limited, reason)
        is AiCreateResult.RemoteFailure -> context.getString(R.string.notification_ai_remote_failure, code, reason)
        is AiCreateResult.InvalidResponse -> reason
        is AiCreateResult.ClarificationRequired -> reason
        is AiCreateResult.CreateFailed -> reason
        is AiCreateResult.ScheduleFailed -> reason
        is AiCreateResult.MissingSchedulingPermission,
        is AiCreateResult.Created,
        -> context.getString(R.string.notification_ai_finished)
    }

    private companion object {
        const val TAG = "NoterAiCreateNotify"
        const val CHANNEL_ID = "ai_alarm_creation"
        const val IN_PROGRESS_NOTIFICATION_ID = 21001
        const val RESULT_NOTIFICATION_ID = 21002
        const val EXACT_ALARM_SETTINGS_REQUEST_CODE = 21003
    }
}
