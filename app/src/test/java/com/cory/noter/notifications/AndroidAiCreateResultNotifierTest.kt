package com.cory.noter.notifications

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.cory.noter.ai.AiCreateResult
import com.cory.noter.ai.AiListedAlarm
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class AndroidAiCreateResultNotifierTest {
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

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val notification = requireNotNull(notificationManager.activeNotifications.single().notification)
        val bigText = notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString()

        assertThat(notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
            .isEqualTo("Listed 1 alarms.")
        assertThat(bigText).contains("Take medicine")
        assertThat(bigText).contains("08:00")
        assertThat(bigText).contains("daily")
        assertThat(bigText).contains("pause: none")
    }
}
