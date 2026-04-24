package com.cory.noter

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
    val composeRule = createAndroidComposeRule<ComponentActivity>()

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
                    onHourChanged = viewModel::onHourChanged,
                    onMinuteChanged = viewModel::onMinuteChanged,
                    onRepeatRuleChanged = viewModel::onRepeatRuleChanged,
                    onOnceDateChanged = viewModel::onOnceDateChanged,
                    onCustomWeekdayToggled = viewModel::onCustomWeekdayToggled,
                    onPickRingtone = {},
                    onEnabledChanged = viewModel::onEnabledChanged,
                    onSave = viewModel::save,
                    onDelete = viewModel::delete,
                )
            }
        }

        composeRule.onNodeWithText("Title").performTextInput("Morning run")
        composeRule.onNodeWithText("Hour").performTextClearance()
        composeRule.onNodeWithText("Hour").performTextInput("6")
        composeRule.onNodeWithText("Minute").performTextClearance()
        composeRule.onNodeWithText("Minute").performTextInput("30")
        composeRule.onNodeWithText("Save").performClick()
        composeRule.waitForIdle()

        val alarms = runBlocking { repository.alarms.first() }
        assert(alarms.single().title == "Morning run")
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
                    onHourChanged = viewModel::onHourChanged,
                    onMinuteChanged = viewModel::onMinuteChanged,
                    onRepeatRuleChanged = viewModel::onRepeatRuleChanged,
                    onOnceDateChanged = viewModel::onOnceDateChanged,
                    onCustomWeekdayToggled = viewModel::onCustomWeekdayToggled,
                    onPickRingtone = {},
                    onEnabledChanged = viewModel::onEnabledChanged,
                    onSave = viewModel::save,
                    onDelete = viewModel::delete,
                )
            }
        }

        composeRule.onNodeWithText("Delete").assertIsDisplayed()
        composeRule.onNodeWithText("Title").performTextClearance()
        composeRule.onNodeWithText("Title").performTextInput("Updated title")
        composeRule.onNodeWithText("Save").performClick()
        composeRule.waitForIdle()

        val updatedAlarm = runBlocking { repository.get(7L) }
        assert(updatedAlarm?.title == "Updated title")
    }
}
