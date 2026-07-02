package com.cory.noter.notifications

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.cory.noter.ai.AiCalendarSyncDetails
import com.cory.noter.ai.AiCalendarSyncStatus
import com.cory.noter.ai.AiCreateResult
import com.cory.noter.ai.AiListedAlarm
import com.cory.noter.domain.alarm.Alarm
import com.cory.noter.domain.alarm.AlarmSource
import com.cory.noter.domain.alarm.RepeatRule
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class AndroidAiCreateResultNotifierTest {
    @Test
    @Config(sdk = [Build.VERSION_CODES.S])
    fun `created alarm notification includes synced calendar status`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val notifier = AndroidAiCreateResultNotifier(context)

        notifier.notifyResult(
            AiCreateResult.Created(
                alarm = sampleAlarm(id = 10L),
                calendarSync = AiCalendarSyncDetails(
                    enabled = true,
                    reason = "explicit_user_request",
                    durationMinutes = 45,
                    status = AiCalendarSyncStatus.SYNCED,
                    calendarId = 42,
                    eventId = 9001,
                ),
            ),
        )

        val notification = postedNotification(context)

        assertThat(notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
            .isEqualTo("Take medicine synced to calendar.")
        assertThat(notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString())
            .contains("Alarm \"Take medicine\" was created and synced to calendar.")
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.S])
    fun `created alarm notification includes failed calendar status`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val notifier = AndroidAiCreateResultNotifier(context)

        notifier.notifyResult(
            AiCreateResult.Created(
                alarm = sampleAlarm(id = 10L),
                calendarSync = AiCalendarSyncDetails(
                    enabled = true,
                    reason = "explicit_user_request",
                    durationMinutes = 45,
                    status = AiCalendarSyncStatus.CALENDAR_INSERT_FAILED,
                    failureReason = "provider insert failed",
                ),
            ),
        )

        val notification = postedNotification(context)

        assertThat(notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
            .isEqualTo("Take medicine created; calendar sync needs attention.")
        assertThat(notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString())
            .contains("provider insert failed")
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.S])
    fun `listed alarms notification includes alarm details in expanded text`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val notifier = AndroidAiCreateResultNotifier(context)

        notifier.notifyResult(
            AiCreateResult.AlarmsListed(
                alarms = listOf(
                    AiListedAlarm(
                        id = 15L,
                        title = "Take medicine",
                        localTime = "08:00",
                        repeatSummary = "daily",
                        nextTriggerAtMillis = 1_719_014_400_000,
                        pauseState = "none",
                    ),
                ),
            ),
        )

        val notification = postedNotification(context)
        val bigText = notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString()

        assertThat(notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
            .isEqualTo("Listed 1 alarms.")
        assertThat(bigText).contains("Take medicine")
        assertThat(bigText).contains("08:00")
        assertThat(bigText).contains("daily")
        assertThat(bigText).contains("pause: none")
    }

    private fun postedNotification(context: Context): Notification {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        return requireNotNull(notificationManager.activeNotifications.single().notification)
    }

    private fun sampleAlarm(id: Long) = Alarm(
        id = id,
        title = "Take medicine",
        hour = 8,
        minute = 0,
        repeatRule = RepeatRule.Once(LocalDate.parse("2026-06-22")),
        enabled = true,
        ringtoneUri = "content://ringtone/default",
        source = AlarmSource.AI,
        aiOriginalText = "take medicine",
        nextTriggerAtMillis = 1_719_014_400_000,
        createdAtMillis = 1_719_000_000_000,
        updatedAtMillis = 1_719_000_000_000,
    )
}
