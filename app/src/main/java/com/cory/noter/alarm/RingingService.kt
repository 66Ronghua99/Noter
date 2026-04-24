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
import android.os.IBinder
import androidx.room.Room
import androidx.core.app.NotificationCompat
import com.cory.noter.data.alarm.AlarmDatabase
import com.cory.noter.data.alarm.AlarmRepository
import com.cory.noter.data.alarm.RoomAlarmRepository
import com.cory.noter.ui.ringing.RingingActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class RingingService : Service() {
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
                serviceScope.launch(Dispatchers.IO) {
                    if (alarmId != INVALID_ALARM_ID) {
                        AlarmRuntimeGraph.ringingCoordinator(applicationContext).stopRinging(alarmId)
                    }
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
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
    ) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
        .setContentTitle(alarmTitle)
        .setContentText("Alarm is ringing")
        .setPriority(NotificationCompat.PRIORITY_MAX)
        .setCategory(NotificationCompat.CATEGORY_ALARM)
        .setOngoing(true)
        .setAutoCancel(false)
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                requestCodeFor(alarmId),
                RingingActivity.createIntent(
                    context = this,
                    alarmId = alarmId,
                    alarmTitle = alarmTitle,
                ),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        .setFullScreenIntent(
            PendingIntent.getActivity(
                this,
                requestCodeFor(alarmId),
                RingingActivity.createIntent(
                    context = this,
                    alarmId = alarmId,
                    alarmTitle = alarmTitle,
                ),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
            true,
        )
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
        .also {
            createNotificationChannel()
        }
        .build()

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

    companion object {
        private const val CHANNEL_ID = "ringing_alarm"
        private const val ACTION_START_RINGING = "com.cory.noter.alarm.START_RINGING"
        private const val ACTION_STOP_RINGING = "com.cory.noter.alarm.STOP_RINGING"
        private const val EXTRA_ALARM_ID = "alarm_id"
        private const val EXTRA_ALARM_TITLE = "alarm_title"
        private const val EXTRA_RINGTONE_URI = "ringtone_uri"
        private const val INVALID_ALARM_ID = -1L

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

internal object AlarmRuntimeGraph {
    private const val DATABASE_NAME = "noter.db"

    @Volatile
    private var database: AlarmDatabase? = null

    fun repository(context: Context): AlarmRepository = RoomAlarmRepository(database(context).alarmDao())

    fun scheduler(context: Context): AlarmScheduler = AndroidAlarmScheduler(context.applicationContext)

    fun ringingCoordinator(context: Context): AlarmRingingCoordinator = AlarmRingingCoordinator(
        repository = repository(context),
        schedulingUseCase = AlarmSchedulingUseCase(scheduler(context)),
    )

    private fun database(context: Context): AlarmDatabase {
        val existing = database
        if (existing != null) {
            return existing
        }

        return synchronized(this) {
            database ?: Room.databaseBuilder(
                context.applicationContext,
                AlarmDatabase::class.java,
                DATABASE_NAME,
            ).build().also { created ->
                database = created
            }
        }
    }
}
