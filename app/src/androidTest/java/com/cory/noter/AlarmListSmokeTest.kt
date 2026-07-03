package com.cory.noter

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.cory.noter.ui.alarm_list.AlarmListScreen
import com.cory.noter.ui.alarm_list.AlarmListItemUiModel
import com.cory.noter.ui.alarm_list.AlarmPauseChoiceDialogUiModel
import com.cory.noter.ui.alarm_list.AlarmPauseStatusUiModel
import com.cory.noter.ui.alarm_list.AlarmListTestTags
import com.cory.noter.ui.alarm_list.AlarmListUiState
import com.cory.noter.ui.alarm_list.AlarmDeleteConfirmDialogUiModel
import com.cory.noter.domain.alarm.RepeatRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AlarmListSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun empty_alarm_list_shows_empty_state() {
        var openedSettings = 0

        composeRule.setContent {
            MaterialTheme {
                AlarmListScreen(
                    state = AlarmListUiState(),
                    onAlarmEnabledChanged = { _, _ -> },
                    onConfirmPauseNextOccurrence = {},
                    onConfirmPauseIndefinitely = {},
                    onCancelPauseChoice = {},
                    onDeleteAlarmRequested = {},
                    onConfirmDeleteAlarm = {},
                    onCancelDeleteConfirmation = {},
                    onEditAlarm = {},
                    onOpenSettings = { openedSettings += 1 },
                    onOpenManualCreate = {},
                    onOpenAiCreate = {},
                )
            }
        }

        composeRule.onNodeWithText("Noter").assertIsDisplayed()
        composeRule.onNodeWithTag(AlarmListTestTags.SettingsAction)
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithText("No alarms yet").assertIsDisplayed()
        composeRule.onNodeWithText("Create one manually or ask AI to draft it for you.")
            .assertIsDisplayed()
        assertEquals(1, openedSettings)
    }

    @Test
    fun alarm_list_shows_pause_dialog_and_status() {
        composeRule.setContent {
            MaterialTheme {
                AlarmListScreen(
                    state = AlarmListUiState(
                        alarms = listOf(
                            AlarmListItemUiModel(
                                id = 7L,
                                title = "Wake up",
                                nextTriggerAtMillis = null,
                                repeatRule = RepeatRule.Daily,
                                enabled = false,
                                pauseStatus = AlarmPauseStatusUiModel.PausedIndefinitely,
                            ),
                        ),
                        pauseChoiceDialog = AlarmPauseChoiceDialogUiModel(
                            alarmId = 7L,
                            alarmTitle = "Wake up",
                        ),
                    ),
                    onAlarmEnabledChanged = { _, _ -> },
                    onConfirmPauseNextOccurrence = {},
                    onConfirmPauseIndefinitely = {},
                    onCancelPauseChoice = {},
                    onDeleteAlarmRequested = {},
                    onConfirmDeleteAlarm = {},
                    onCancelDeleteConfirmation = {},
                    onEditAlarm = {},
                    onOpenSettings = {},
                    onOpenManualCreate = {},
                    onOpenAiCreate = {},
                )
            }
        }

        composeRule.onNodeWithText("Paused indefinitely").assertIsDisplayed()
        composeRule.onNodeWithText("Pause alarm").assertIsDisplayed()
        composeRule.onNodeWithText("Next occurrence").assertIsDisplayed()
        composeRule.onNodeWithText("Indefinitely").assertIsDisplayed()
    }

    @Test
    fun alarm_list_shows_delete_confirmation() {
        var confirmedDelete = 0
        var canceledDelete = 0

        composeRule.setContent {
            MaterialTheme {
                AlarmListScreen(
                    state = AlarmListUiState(
                        deleteConfirmDialog = AlarmDeleteConfirmDialogUiModel(
                            alarmId = 7L,
                            alarmTitle = "Wake up",
                        ),
                    ),
                    onAlarmEnabledChanged = { _, _ -> },
                    onConfirmPauseNextOccurrence = {},
                    onConfirmPauseIndefinitely = {},
                    onCancelPauseChoice = {},
                    onDeleteAlarmRequested = {},
                    onConfirmDeleteAlarm = { confirmedDelete += 1 },
                    onCancelDeleteConfirmation = { canceledDelete += 1 },
                    onEditAlarm = {},
                    onOpenSettings = {},
                    onOpenManualCreate = {},
                    onOpenAiCreate = {},
                )
            }
        }

        composeRule.onNodeWithText("Delete alarm?").assertIsDisplayed()
        composeRule.onNodeWithText("Delete “Wake up”? This can’t be undone.").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").assertIsDisplayed().performClick()
        assertEquals(1, canceledDelete)

        composeRule.onNodeWithText("Delete").assertIsDisplayed().performClick()
        assertEquals(1, confirmedDelete)
    }
}
