package com.cory.noter

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.platform.testTag
import com.cory.noter.ui.NoterApp
import com.cory.noter.ui.text.UiText
import com.cory.noter.ui.voice.VoiceHomeScreen
import com.cory.noter.ui.voice.VoiceHomeStatus
import com.cory.noter.ui.voice.VoiceHomeTestTags
import com.cory.noter.ui.voice.VoiceHomeUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class VoiceHomeSmokeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun noter_app_default_start_destination_is_voice_home() {
        composeRule.setContent {
            MaterialTheme {
                TestNoterApp()
            }
        }

        composeRule.onNodeWithTag(VoiceHomeTestTags.RecordButton)
            .assertIsDisplayed()
    }

    @Test
    fun noter_app_voice_home_list_action_reaches_alarm_list() {
        composeRule.setContent {
            MaterialTheme {
                TestNoterApp()
            }
        }

        composeRule.onNodeWithTag(VoiceHomeTestTags.ListAction)
            .assertIsDisplayed()
            .performClick()

        composeRule.onNodeWithTag(AppRouteTestTags.AlarmList)
            .assertIsDisplayed()
    }

    @Test
    fun noter_app_voice_home_settings_action_reaches_settings() {
        composeRule.setContent {
            MaterialTheme {
                TestNoterApp()
            }
        }

        composeRule.onNodeWithTag(VoiceHomeTestTags.SettingsAction)
            .assertIsDisplayed()
            .performClick()

        composeRule.onNodeWithTag(AppRouteTestTags.Settings)
            .assertIsDisplayed()
    }

    @Test
    fun noter_app_voice_home_text_fallback_reaches_ai_create() {
        composeRule.setContent {
            MaterialTheme {
                TestNoterApp(
                    voiceState = VoiceHomeUiState(
                        status = VoiceHomeStatus.Idle,
                        errorMessage = UiText.Raw("Speech recognition failed."),
                        showTextFallbackAction = true,
                    ),
                )
            }
        }

        composeRule.onNodeWithTag(VoiceHomeTestTags.TextFallbackAction)
            .assertIsDisplayed()
            .performClick()

        composeRule.onNodeWithTag(AppRouteTestTags.AiCreate)
            .assertIsDisplayed()
    }

    @Test
    fun voice_home_idle_surface_exposes_record_list_and_settings_actions() {
        var pressed = 0
        var released = 0
        var openedList = 0
        var openedSettings = 0

        composeRule.setContent {
            MaterialTheme {
                VoiceHomeScreen(
                    state = VoiceHomeUiState(status = VoiceHomeStatus.Idle),
                    onRecordPressed = { pressed += 1 },
                    onRecordReleased = { released += 1 },
                    onRecordCancelled = {},
                    onRetry = {},
                    onOpenTextInput = {},
                    onOpenAlarmList = { openedList += 1 },
                    onOpenSettings = { openedSettings += 1 },
                )
            }
        }

        composeRule.onNodeWithTag(VoiceHomeTestTags.RecordButton)
            .assertIsDisplayed()
            .performTouchInput {
                down(center)
                up()
            }
        composeRule.onNodeWithTag(VoiceHomeTestTags.ListAction).performClick()
        composeRule.onNodeWithTag(VoiceHomeTestTags.SettingsAction).performClick()

        assertEquals(1, pressed)
        assertEquals(1, released)
        assertEquals(1, openedList)
        assertEquals(1, openedSettings)
    }

    @Test
    fun voice_home_recording_surface_exposes_cancel_action() {
        var cancelled = 0

        composeRule.setContent {
            MaterialTheme {
                VoiceHomeScreen(
                    state = VoiceHomeUiState(status = VoiceHomeStatus.Recording),
                    onRecordPressed = {},
                    onRecordReleased = {},
                    onRecordCancelled = { cancelled += 1 },
                    onRetry = {},
                    onOpenTextInput = {},
                    onOpenAlarmList = {},
                    onOpenSettings = {},
                )
            }
        }

        composeRule.onNodeWithTag(VoiceHomeTestTags.CancelAction)
            .assertIsDisplayed()
            .performClick()

        assertEquals(1, cancelled)
    }

    @Test
    fun voice_home_notice_surface_is_visible_without_transcript_text() {
        composeRule.setContent {
            MaterialTheme {
                VoiceHomeScreen(
                    state = VoiceHomeUiState(
                        status = VoiceHomeStatus.Idle,
                        noticeMessage = UiText.Raw("Noter is processing your alarm."),
                    ),
                    onRecordPressed = {},
                    onRecordReleased = {},
                    onRecordCancelled = {},
                    onRetry = {},
                    onOpenTextInput = {},
                    onOpenAlarmList = {},
                    onOpenSettings = {},
                )
            }
        }

        composeRule.onNodeWithTag(VoiceHomeTestTags.Notice)
            .assertIsDisplayed()
            .assertTextContains("Noter is processing your alarm.")
    }

    @Test
    fun voice_home_failure_surface_exposes_retry_and_text_fallback_actions() {
        var retries = 0
        var textFallbacks = 0

        composeRule.setContent {
            MaterialTheme {
                VoiceHomeScreen(
                    state = VoiceHomeUiState(
                        status = VoiceHomeStatus.Idle,
                        errorMessage = UiText.Raw("Speech recognition failed."),
                        showRetryAction = true,
                        showTextFallbackAction = true,
                    ),
                    onRecordPressed = {},
                    onRecordReleased = {},
                    onRecordCancelled = {},
                    onRetry = { retries += 1 },
                    onOpenTextInput = { textFallbacks += 1 },
                    onOpenAlarmList = {},
                    onOpenSettings = {},
                )
            }
        }

        composeRule.onNodeWithTag(VoiceHomeTestTags.RetryAction)
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithTag(VoiceHomeTestTags.TextFallbackAction)
            .assertIsDisplayed()
            .performClick()

        assertEquals(1, retries)
        assertEquals(1, textFallbacks)
    }

    @Composable
    private fun TestNoterApp(
        voiceState: VoiceHomeUiState = VoiceHomeUiState(status = VoiceHomeStatus.Idle),
    ) {
        NoterApp(
            voiceHomeScreen = { onOpenAlarmList, onOpenSettings, onOpenTextInput ->
                VoiceHomeScreen(
                    state = voiceState,
                    onRecordPressed = {},
                    onRecordReleased = {},
                    onRecordCancelled = {},
                    onRetry = {},
                    onOpenTextInput = onOpenTextInput,
                    onOpenAlarmList = onOpenAlarmList,
                    onOpenSettings = onOpenSettings,
                )
            },
            alarmListScreen = { _, _, _, _ ->
                Box(modifier = Modifier.testTag(AppRouteTestTags.AlarmList))
            },
            alarmEditorScreen = { _, _ ->
                Box(modifier = Modifier.testTag(AppRouteTestTags.Editor))
            },
            aiCreateScreen = { _, _ ->
                Box(modifier = Modifier.testTag(AppRouteTestTags.AiCreate))
            },
            settingsScreen = {
                Box(modifier = Modifier.testTag(AppRouteTestTags.Settings))
            },
        )
    }

    private object AppRouteTestTags {
        const val AlarmList = "NoterAppAlarmListRoute"
        const val Editor = "NoterAppEditorRoute"
        const val AiCreate = "NoterAppAiCreateRoute"
        const val Settings = "NoterAppSettingsRoute"
    }
}
