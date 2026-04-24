package com.cory.noter

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.cory.noter.ui.alarm_list.AlarmListScreen
import com.cory.noter.ui.alarm_list.AlarmListUiState
import org.junit.Rule
import org.junit.Test

class AlarmListSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun empty_alarm_list_shows_empty_state() {
        composeRule.setContent {
            MaterialTheme {
                AlarmListScreen(
                    state = AlarmListUiState(),
                    onAlarmEnabledChanged = { _, _ -> },
                    onEditAlarm = {},
                    onDeleteAlarm = {},
                    onOpenSettings = {},
                    onOpenManualCreate = {},
                    onOpenAiCreate = {},
                )
            }
        }

        composeRule.onNodeWithText("No alarms yet").assertIsDisplayed()
        composeRule.onNodeWithText("Create one manually or ask AI to draft it for you.")
            .assertIsDisplayed()
    }
}
