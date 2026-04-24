package com.cory.noter.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.cory.noter.di.appContainer
import com.cory.noter.ui.ringing.RingingActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RingingService : Service() {
    internal sealed interface StopHandling {
        data object Finish : StopHandling

        data class ShowFailure(val reason: String) : StopHandling
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var ringtone: Ringtone? = null

    override fun onBind(intent: Intent): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        return when (intent?.action) {
            ACTION_START_RINGING -> {
                val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, INVALID_ALARM_ID)
                val alarmTitle = intent.getStringExtra(EXTRA_ALARM_TITLE).orEmpty().ifBlank { "Alarm" }
                val ringtoneUri = intent.getStringExtra(EXTRA_RINGTONE_URI).orEmpty()
                if (alarmId == INVALID_ALARM_ID || ringtoneUri.isBlank()) {
                    stopSelf(startId)
                    START_NOT_STICKY
                } else {
                    startForeground(
                        notificationIdFor(alarmId),
                        buildNotification(
                            alarmId = alarmId,
                            alarmTitle = alarmTitle,
                            ringtoneUri = ringtoneUri,
                        ),
                    )
                    startPlayback(ringtoneUri)
                    START_NOT_STICKY
                }
            }

            ACTION_STOP_RINGING -> {
                val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, INVALID_ALARM_ID)
                stopPlayback()
                serviceScope.launch {
                    val stopResult = stopAlarm(alarmId)
                    when (val handling = stopHandlingFor(stopResult)) {
                        StopHandling.Finish -> {
                            stopForeground(STOP_FOREGROUND_REMOVE)
                            stopSelf()
                        }

                        is StopHandling.ShowFailure -> {
                            startForeground(
                                notificationIdFor(alarmId.takeIf { it != INVALID_ALARM_ID } ?: CLEANUP_FAILURE_NOTIFICATION_ID),
                                buildCleanupFailureNotification(handling.reason),
                            )
                        }
                    }
                }
                START_NOT_STICKY
            }

            else -> START_NOT_STICKY
        }
    }

    override fun onDestroy() {
        stopPlayback()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(
        alarmId: Long,
        alarmTitle: String,
        ringtoneUri: String,
    ): android.app.Notification {
        createNotificationChannel()
        val activityIntent = PendingIntent.getActivity(
            this,
            requestCodeFor(alarmId),
            RingingActivity.createIntent(
                context = this,
                alarmId = alarmId,
                alarmTitle = alarmTitle,
            ),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(alarmTitle)
            .setContentText("Alarm is ringing")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(activityIntent)
            .apply {
                if (shouldUseFullScreenIntent(
                        sdkInt = Build.VERSION.SDK_INT,
                        managerAllowsFullScreenIntent = notificationManagerAllowsFullScreenIntent(),
                    )
                ) {
                    setFullScreenIntent(activityIntent, true)
                }
            }
            .addAction(
                0,
                "Stop",
                PendingIntent.getService(
                    this,
                    requestCodeFor(alarmId),
                    createStopIntent(
                        context = this,
                        alarmId = alarmId,
                        ringtoneUri = ringtoneUri,
                    ),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()
    }

    private fun buildCleanupFailureNotification(reason: String): android.app.Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Alarm cleanup failed")
            .setContentText(reason)
            .setStyle(NotificationCompat.BigTextStyle().bigText(reason))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setOngoing(true)
            .setAutoCancel(false)
            .also {
                createNotificationChannel()
            }
            .build()

    private suspend fun stopAlarm(alarmId: Long): AlarmStopResult = withContext(Dispatchers.IO) {
        if (alarmId == INVALID_ALARM_ID) {
            return@withContext AlarmStopResult.MissingAlarm
        }

        applicationContext.appContainer.alarmRingingCoordinator.stopRinging(alarmId)
    }

    private fun createNotificationChannel() {
        val notificationManager = getSystemService(NotificationManager::class.java) ?: return
        val existing = notificationManager.getNotificationChannel(CHANNEL_ID)
        if (existing != null) {
            return
        }

        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Ringing alarms",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Alarm notifications for active ringing flows."
                setBypassDnd(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            },
        )
    }

    private fun startPlayback(ringtoneUri: String) {
        stopPlayback()
        ringtone = RingtoneManager.getRingtone(applicationContext, Uri.parse(ringtoneUri))?.apply {
            audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            play()
        }
    }

    private fun stopPlayback() {
        ringtone?.stop()
        ringtone = null
    }

    private fun requestCodeFor(alarmId: Long): Int = (alarmId xor (alarmId ushr 32)).toInt()

    private fun notificationIdFor(alarmId: Long): Int = requestCodeFor(alarmId)

    private fun notificationManagerAllowsFullScreenIntent(): Boolean {
        val notificationManager = getSystemService(NotificationManager::class.java)
        return when {
            notificationManager == null -> false
            Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> true
            else -> notificationManager.canUseFullScreenIntent()
        }
    }

    companion object {
        private const val CHANNEL_ID = "ringing_alarm"
        private const val CLEANUP_FAILURE_NOTIFICATION_ID = 40_001L
        private const val ACTION_START_RINGING = "com.cory.noter.alarm.START_RINGING"
        private const val ACTION_STOP_RINGING = "com.cory.noter.alarm.STOP_RINGING"
        private const val EXTRA_ALARM_ID = "alarm_id"
        private const val EXTRA_ALARM_TITLE = "alarm_title"
        private const val EXTRA_RINGTONE_URI = "ringtone_uri"
        private const val INVALID_ALARM_ID = -1L

        internal fun stopHandlingFor(result: AlarmStopResult): StopHandling = when (result) {
            is AlarmStopResult.Failed -> StopHandling.ShowFailure(result.reason)
            AlarmStopResult.MissingAlarm,
            AlarmStopResult.DeletedOneTimeAlarm,
            is AlarmStopResult.RescheduledRepeatingAlarm,
            -> StopHandling.Finish
        }

        internal fun shouldUseFullScreenIntent(
            sdkInt: Int,
            managerAllowsFullScreenIntent: Boolean,
        ): Boolean = sdkInt < Build.VERSION_CODES.UPSIDE_DOWN_CAKE || managerAllowsFullScreenIntent

        fun createStartIntent(
            context: Context,
            alarmId: Long,
            alarmTitle: String,
            ringtoneUri: String,
        ): Intent = Intent(context, RingingService::class.java)
            .setAction(ACTION_START_RINGING)
            .putExtra(EXTRA_ALARM_ID, alarmId)
            .putExtra(EXTRA_ALARM_TITLE, alarmTitle)
            .putExtra(EXTRA_RINGTONE_URI, ringtoneUri)

        fun createStopIntent(
            context: Context,
            alarmId: Long,
            ringtoneUri: String = "",
        ): Intent = Intent(context, RingingService::class.java)
            .setAction(ACTION_STOP_RINGING)
            .putExtra(EXTRA_ALARM_ID, alarmId)
            .putExtra(EXTRA_RINGTONE_URI, ringtoneUri)
    }
}
