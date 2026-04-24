package com.cory.noter.ui.ringing

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.cory.noter.alarm.RingingService

class RingingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, INVALID_ALARM_ID)
        val alarmTitle = intent.getStringExtra(EXTRA_ALARM_TITLE).orEmpty().ifBlank { "Alarm" }

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier) {
                    RingingScreen(
                        title = alarmTitle,
                        onStop = {
                            startService(
                                RingingService.createStopIntent(
                                    context = this,
                                    alarmId = alarmId,
                                ),
                            )
                            finish()
                        },
                    )
                }
            }
        }
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
