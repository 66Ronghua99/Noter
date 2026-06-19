package com.cory.noter.ui.ringing

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.cory.noter.R
import com.cory.noter.alarm.RingingService

class RingingActivity : ComponentActivity() {
    private var currentAlarm by mutableStateOf(RingingAlarmState())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        updateFromIntent(intent)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier) {
                    RingingScreen(
                        title = currentAlarm.title,
                        onStop = {
                            startService(currentStopIntentForTest())
                            finish()
                        },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        updateFromIntent(intent)
    }

    internal fun currentAlarmForTest(): RingingAlarmState = currentAlarm

    internal fun currentStopIntentForTest(): Intent = RingingService.createStopIntent(
        context = this,
        alarmId = currentAlarm.alarmId,
    )

    private fun updateFromIntent(intent: Intent?) {
        currentAlarm = RingingAlarmState.fromIntent(
            intent = intent,
            defaultTitle = getString(R.string.notification_ringing_default_title),
        )
    }

    companion object {
        private const val EXTRA_ALARM_ID = "alarm_id"
        private const val EXTRA_ALARM_TITLE = "alarm_title"
        private const val INVALID_ALARM_ID = -1L

        fun createIntent(
            context: Context,
            alarmId: Long,
            alarmTitle: String,
        ): Intent = Intent(context, RingingActivity::class.java)
            .putExtra(EXTRA_ALARM_ID, alarmId)
            .putExtra(EXTRA_ALARM_TITLE, alarmTitle)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}

data class RingingAlarmState(
    val alarmId: Long = -1L,
    val title: String = "",
) {
    companion object {
        private const val EXTRA_ALARM_ID = "alarm_id"
        private const val EXTRA_ALARM_TITLE = "alarm_title"
        private const val INVALID_ALARM_ID = -1L

        fun fromIntent(
            intent: Intent?,
            defaultTitle: String,
        ): RingingAlarmState = RingingAlarmState(
            alarmId = intent?.getLongExtra(EXTRA_ALARM_ID, INVALID_ALARM_ID) ?: INVALID_ALARM_ID,
            title = intent?.getStringExtra(EXTRA_ALARM_TITLE).orEmpty().ifBlank { defaultTitle },
        )
    }
}
