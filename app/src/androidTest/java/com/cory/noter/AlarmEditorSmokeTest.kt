package com.cory.noter

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.cory.noter.alarm.AlarmSchedulingUseCase
import com.cory.noter.domain.alarm.Alarm
import com.cory.noter.domain.alarm.AlarmSource
import com.cory.noter.domain.alarm.RepeatRule
import com.cory.noter.ui.editor.AlarmEditorScreen
import com.cory.noter.ui.editor.AlarmEditorViewModel
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

class AlarmEditorSmokeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val zoneId = ZoneId.of("Asia/Shanghai")
    private val clock = Clock.fixed(Instant.parse("2026-04-23T01:00:00Z"), zoneId)

    @Test
    fun manual_create_flow_saves_alarm() {
        val repository = AndroidTestAlarmRepository(clock = clock, zoneId = zoneId)
        val settingsRepository = AndroidTestSettingsRepository()
        val scheduler = AndroidTestAlarmScheduler()
        val viewModel = AlarmEditorViewModel(
            alarmId = null,
            repository = repository,
            settingsRepository = settingsRepository,
            schedulingUseCase = AlarmSchedulingUseCase(scheduler),
            clock = clock,
            zoneId = zoneId,
        )

        composeRule.setContent {
            val state by viewModel.uiState.collectAsState()
            MaterialTheme {
                AlarmEditorScreen(
                    state = state,
                    onTitleChanged = viewModel::onTitleChanged,
                    onHourSelected = viewModel::onHourSelected,
                    onMinuteSelected = viewModel::onMinuteSelected,
                    onRepeatRuleChanged = viewModel::onRepeatRuleChanged,
                    onOnceDateSelected = viewModel::onOnceDateSelected,
                    onIntervalStartDateSelected = viewModel::onIntervalStartDateSelected,
                    onIntervalEndDateSelected = viewModel::onIntervalEndDateSelected,
                    onIntervalWeeksSelected = viewModel::onIntervalWeeksSelected,
                    onCustomWeekdayToggled = viewModel::onCustomWeekdayToggled,
                    onPickRingtone = {},
                    onEnabledChanged = viewModel::onEnabledChanged,
                    onOpenExactAlarmSettings = {},
                    onSave = viewModel::save,
                    onDelete = viewModel::delete,
                )
            }
        }

        composeRule.onNodeWithText("Title").performTextInput("Morning run")
        composeRule.onAllNodesWithText("Hour").assertCountEquals(0)
        composeRule.onAllNodesWithText("Minute").assertCountEquals(0)
        composeRule.onAllNodesWithText("Date (YYYY-MM-DD)").assertCountEquals(0)
        composeRule.onNodeWithTag("OnceDateControl").assertIsDisplayed()
        composeRule.onNodeWithTag("OnceDateControl").performClick()
        composeRule.onNodeWithTag("DatePickerDialog-OnceDate").assertIsDisplayed()
        composeRule.onNodeWithTag("DatePickerDialog-OnceDate-Cancel").performClick()
        composeRule.onNodeWithTag("HourWheelSelectionFrame").assertIsDisplayed()
        composeRule.onNodeWithTag("MinuteWheelSelectionFrame").assertIsDisplayed()
        composeRule.onNodeWithTag("HourWheel").performScrollToIndex(6)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("HourWheel-selected-06").assertIsDisplayed()
        composeRule.onNodeWithTag("MinuteWheel").performScrollToIndex(30)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("MinuteWheel-selected-30").assertIsDisplayed()
        composeRule.onNodeWithText("Save").performClick()
        composeRule.waitForIdle()

        val alarms = runBlocking { repository.alarms.first() }
        assert(alarms.single().title == "Morning run")
        assert(alarms.single().hour == 6)
        assert(alarms.single().minute == 30)
        assert(scheduler.scheduledIds.contains(alarms.single().id))
    }

    @Test
    fun edit_flow_updates_existing_alarm() {
        val repository = AndroidTestAlarmRepository(clock = clock, zoneId = zoneId)
        runBlocking {
            repository.seed(
                Alarm(
                    id = 7L,
                    title = "Old title",
                    hour = 7,
                    minute = 0,
                    repeatRule = RepeatRule.Daily,
                    enabled = true,
                    ringtoneUri = "content://settings/system/alarm_alert",
                    source = AlarmSource.MANUAL,
                    aiOriginalText = null,
                    nextTriggerAtMillis = Instant.parse("2026-04-24T23:00:00Z").toEpochMilli(),
                    createdAtMillis = clock.millis(),
                    updatedAtMillis = clock.millis(),
                ),
            )
        }
        val viewModel = AlarmEditorViewModel(
            alarmId = 7L,
            repository = repository,
            settingsRepository = AndroidTestSettingsRepository(),
            schedulingUseCase = AlarmSchedulingUseCase(AndroidTestAlarmScheduler()),
            clock = clock,
            zoneId = zoneId,
        )

        composeRule.setContent {
            val state by viewModel.uiState.collectAsState()
            MaterialTheme {
                AlarmEditorScreen(
                    state = state,
                    onTitleChanged = viewModel::onTitleChanged,
                    onHourSelected = viewModel::onHourSelected,
                    onMinuteSelected = viewModel::onMinuteSelected,
                    onRepeatRuleChanged = viewModel::onRepeatRuleChanged,
                    onOnceDateSelected = viewModel::onOnceDateSelected,
                    onIntervalStartDateSelected = viewModel::onIntervalStartDateSelected,
                    onIntervalEndDateSelected = viewModel::onIntervalEndDateSelected,
                    onIntervalWeeksSelected = viewModel::onIntervalWeeksSelected,
                    onCustomWeekdayToggled = viewModel::onCustomWeekdayToggled,
                    onPickRingtone = {},
                    onEnabledChanged = viewModel::onEnabledChanged,
                    onOpenExactAlarmSettings = {},
                    onSave = viewModel::save,
                    onDelete = viewModel::delete,
                )
            }
        }

        composeRule.onNodeWithText("Delete").assertIsDisplayed()
        composeRule.onNodeWithTag("HourWheel-selected-07").assertIsDisplayed()
        composeRule.onNodeWithTag("MinuteWheel-selected-00").assertIsDisplayed()
        composeRule.onNodeWithText("Title").performTextClearance()
        composeRule.onNodeWithText("Title").performTextInput("Updated title")
        composeRule.onNodeWithText("Save").performClick()
        composeRule.waitForIdle()

        val updatedAlarm = runBlocking { repository.get(7L) }
        assert(updatedAlarm?.title == "Updated title")
    }

    @Test
    fun interval_date_controls_open_dialogs_without_text_fields() {
        val repository = AndroidTestAlarmRepository(clock = clock, zoneId = zoneId)
        val viewModel = AlarmEditorViewModel(
            alarmId = null,
            repository = repository,
            settingsRepository = AndroidTestSettingsRepository(),
            schedulingUseCase = AlarmSchedulingUseCase(AndroidTestAlarmScheduler()),
            clock = clock,
            zoneId = zoneId,
        )

        composeRule.setContent {
            val state by viewModel.uiState.collectAsState()
            MaterialTheme {
                AlarmEditorScreen(
                    state = state,
                    onTitleChanged = viewModel::onTitleChanged,
                    onHourSelected = viewModel::onHourSelected,
                    onMinuteSelected = viewModel::onMinuteSelected,
                    onRepeatRuleChanged = viewModel::onRepeatRuleChanged,
                    onOnceDateSelected = viewModel::onOnceDateSelected,
                    onIntervalStartDateSelected = viewModel::onIntervalStartDateSelected,
                    onIntervalEndDateSelected = viewModel::onIntervalEndDateSelected,
                    onIntervalWeeksSelected = viewModel::onIntervalWeeksSelected,
                    onCustomWeekdayToggled = viewModel::onCustomWeekdayToggled,
                    onPickRingtone = {},
                    onEnabledChanged = viewModel::onEnabledChanged,
                    onOpenExactAlarmSettings = {},
                    onSave = viewModel::save,
                    onDelete = viewModel::delete,
                )
            }
        }

        composeRule.onNodeWithText("Interval").performClick()
        composeRule.onAllNodesWithText("Start date").assertCountEquals(0)
        composeRule.onAllNodesWithText("End date").assertCountEquals(0)
        composeRule.onNodeWithTag("IntervalStartDateControl").assertIsDisplayed()
        composeRule.onNodeWithTag("IntervalEndDateControl").assertIsDisplayed()
        composeRule.onNodeWithTag("IntervalWeeksRow").assertIsDisplayed()
        composeRule.onNodeWithTag("IntervalWeeksLabel").assertIsDisplayed()
        composeRule.onNodeWithTag("IntervalWeeksWheelSelectionFrame").assertIsDisplayed()
        composeRule.onNodeWithTag("IntervalWeeksWheel").performScrollToIndex(2)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("IntervalWeeksWheel-selected-3").assertIsDisplayed()
        composeRule.onNodeWithTag("IntervalStartDateControl").performClick()
        composeRule.onNodeWithTag("DatePickerDialog-IntervalStartDate").assertIsDisplayed()
        composeRule.onNodeWithTag("DatePickerDialog-IntervalStartDate-Cancel").performClick()
    }
}
